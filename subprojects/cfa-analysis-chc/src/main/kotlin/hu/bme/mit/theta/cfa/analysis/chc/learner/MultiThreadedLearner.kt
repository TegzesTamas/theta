package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import kotlin.concurrent.thread

class MultiThreadedLearner(learners: Iterable<Learner>) : Learner {
    private data class LearnerThreadData(val learner: Learner, val thread: Thread, val input: BlockingQueue<ConstraintSystem>, val output: ArrayBlockingQueue<InvariantCandidates>)

    private val threadData: List<LearnerThreadData>
    private val queue: LinkedList<LearnerThreadData>

    @Volatile
    private var running = true

    init {
        threadData = learners
                .map { learner ->
                    val input = ArrayBlockingQueue<ConstraintSystem>(1)
                    val output = ArrayBlockingQueue<InvariantCandidates>(1)
                    val thread = thread {
                        runLearner(learner, input, output)
                    }
                    return@map LearnerThreadData(learner, thread, input, output)
                }
        queue = LinkedList(threadData)
    }

    private fun runLearner(learner: Learner, input: BlockingQueue<ConstraintSystem>, output: ArrayBlockingQueue<InvariantCandidates>) {
        try {
            while (running) {
                val cs = input.take()
                val ic = learner.suggestCandidates(cs)
                output.clear()
                output.put(ic)
            }
        } catch (e: Learner.CandidatesNotExpressibleException) {
        } catch (e: InterruptedException) {
        }
    }


    override fun suggestCandidates(constraintSystem: ConstraintSystem): InvariantCandidates {
        for (data in threadData) {
            data.input.clear()
            data.input.put(constraintSystem)
        }

        var candidate: InvariantCandidates? = null
        val notReady = mutableListOf<LearnerThreadData>()
        while (candidate == null && queue.isNotEmpty()) {
            val data = queue.removeFirst()
            candidate = data.output.poll()
            if (candidate == null) {
                if (data.thread.isAlive) {
                    notReady.add(data)
                } else {
                    data.learner.close()
                }
                if (queue.isEmpty()) {
                    for (i in notReady.lastIndex downTo 0) {
                        queue.addFirst(notReady[i])
                    }
                    notReady.clear()
                }
            } else {
                queue.addLast(data)
            }
        }
        if (candidate == null) {
            throw Learner.CandidatesNotExpressibleException()
        }
        for (i in notReady.lastIndex downTo 0) {
            queue.addFirst(notReady[i])
        }
        notReady.clear()
        return candidate
    }

    override fun close() {
        running = false
        for (data in threadData) {
            data.thread.interrupt()
            data.learner.close()
        }
    }
}