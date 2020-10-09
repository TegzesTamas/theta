package hu.bme.mit.theta.cfa.analysis.chc.coordinator

import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Constraint
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.learner.Learner
import hu.bme.mit.theta.cfa.analysis.chc.teacher.Teacher
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.common.logging.NullLogger
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class MultiThreadedCoordinator(
        private val learners: Iterable<Learner>,
        private val teachers: Iterable<Teacher>,
        private val logger: Logger = NullLogger.getInstance()
) : Coordinator {

    private data class TeacherResult(val candidates: InvariantCandidates,
                                     val constraints: Iterable<Constraint>?)


    private data class LearnerThreadData(val learner: Learner,
                                         val thread: Thread,
                                         val input: ArrayBlockingQueue<ConstraintSystem>)

    private data class InitResult(val teacherInput: BlockingQueue<InvariantCandidates>,
                                  val teacherOutput: BlockingQueue<TeacherResult>,
                                  val learnerThreads: List<LearnerThreadData>,
                                  val teacherThreads: List<Thread>)

    @Volatile
    private var running: Boolean = true

    private fun runLearner(learner: Learner, input: BlockingQueue<ConstraintSystem>, output: BlockingQueue<InvariantCandidates>) {
        try {
            while (running) {
                val constraintSystem = input.take()
                val candidates = learner.suggestCandidates(constraintSystem)
                output.put(candidates)
            }
        } catch (e: InterruptedException) {
        } catch (e: Learner.CandidatesNotExpressibleException) {
        }
    }

    private fun runTeacher(teacher: Teacher,
                           chcSystem: CHCSystem,
                           input: BlockingQueue<InvariantCandidates>,
                           output: BlockingQueue<TeacherResult>) {
        try {
            while (running) {
                val candidates = input.take()
                val constraints = teacher.checkCandidates(chcSystem, candidates)
                output.put(TeacherResult(candidates, constraints))
            }
        } catch (e: InterruptedException) {
        }
    }


    private fun init(chcSystem: CHCSystem): InitResult {
        running = true
        val learnerOutputQueue = LinkedBlockingQueue<InvariantCandidates>()
        val teacherOutputQueue = LinkedBlockingQueue<TeacherResult>()
        val learnerThreads = learners.map { learner ->
            val learnerInputQueue = ArrayBlockingQueue<ConstraintSystem>(1)
            val thread = thread { runLearner(learner, learnerInputQueue, learnerOutputQueue) }
            return@map LearnerThreadData(learner, thread, learnerInputQueue)
        }
        val teacherThreads = teachers.map { teacher ->
            thread { runTeacher(teacher, chcSystem, learnerOutputQueue, teacherOutputQueue) }
        }

        val cs = ConstraintSystem.Builder().build()

        for (learnerData in learnerThreads) {
            learnerData.input.put(cs)
        }
        return InitResult(learnerOutputQueue, teacherOutputQueue, learnerThreads, teacherThreads)
    }

    override fun solveCHCSystem(chcSystem: CHCSystem): InvariantCandidates {
        val (teacherInputQueue,
                teacherOutputQueue,
                learnerThreads,
                teacherThreads) = init(chcSystem)
        try {
            val csBuilder = ConstraintSystem.Builder()
            while (true) {
                val drainedResults = mutableListOf<TeacherResult>()
                teacherOutputQueue.drainTo(drainedResults)
                val teacherResults =
                        if (drainedResults.isEmpty()) {
                            listOf(teacherOutputQueue.take())
                        } else {
                            drainedResults
                        }
                for ((candidates, constraints) in teacherResults) {
                    if (constraints == null) {
                        return candidates
                    } else {
                        for (constraint in constraints) {
                            csBuilder.addConstraint(constraint)
                        }
                    }
                }
                val cs = csBuilder.build()
                for ((_, _, input) in learnerThreads) {
                    input.clear()
                    input.put(cs)
                }
            }
        } finally {
            running = false
            for ((_, thread, input) in learnerThreads) {
                input.clear()
                thread.interrupt()
            }
            teacherInputQueue.clear()
            for (teacherThread in teacherThreads) {
                teacherThread.interrupt()
            }
        }
    }
}