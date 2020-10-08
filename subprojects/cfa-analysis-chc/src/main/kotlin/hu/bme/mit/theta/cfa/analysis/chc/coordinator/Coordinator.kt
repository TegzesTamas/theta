package hu.bme.mit.theta.cfa.analysis.chc.coordinator

import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.learner.Learner
import hu.bme.mit.theta.cfa.analysis.chc.teacher.Teacher
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.common.logging.NullLogger

interface Coordinator {
    @Throws(ContradictoryException::class)
    fun solveCHCSystem(chcSystem: CHCSystem): InvariantCandidates
}

class SimpleCoordinator(
        private val teacher: Teacher,
        private val learner: Learner,
        private val logger: Logger = NullLogger.getInstance()
) : Coordinator {
    @Throws(ContradictoryException::class)
    override fun solveCHCSystem(chcSystem: CHCSystem): InvariantCandidates {
        val csBuilder = ConstraintSystem.Builder()
        while (true) {
            val candidates = learner.suggestCandidates(csBuilder.build())
            val constraints = teacher.checkCandidates(chcSystem, candidates)
            if (constraints != null) {
                for (constraint in constraints) {
                    csBuilder.addConstraint(constraint)
                }
            } else {
                return candidates
            }
        }
    }
}