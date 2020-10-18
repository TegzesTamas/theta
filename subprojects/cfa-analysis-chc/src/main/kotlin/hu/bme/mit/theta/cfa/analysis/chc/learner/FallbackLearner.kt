package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem

class FallbackLearner(learners: Iterable<Learner>) : Learner {

    private val learners = learners.toMutableList()

    override fun suggestCandidates(constraintSystem: ConstraintSystem): InvariantCandidates {
        while (learners.isNotEmpty()) {
            try {
                return learners[0].suggestCandidates(constraintSystem)
            } catch (e: Learner.CandidatesNotExpressibleException) {
                learners.removeAt(0)
            }
        }
        throw Learner.CandidatesNotExpressibleException(
                "None of the learners could provide candidates satisfying the constraints")
    }
}