package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.learner.Learner.CandidatesNotExpressibleException

class RoundRobinLearner(learners: Iterable<Learner>) : Learner {
    private val learners = learners.toMutableList()
    private var index = 0

    @Throws(CandidatesNotExpressibleException::class)
    override fun suggestCandidates(constraintSystem: ConstraintSystem): InvariantCandidates {
        var candidates: InvariantCandidates? = null
        while (candidates == null) {
            if (learners.isEmpty()) {
                throw CandidatesNotExpressibleException()
            }
            if (index >= learners.size) {
                index = 0
            }
            try {
                candidates = learners[index].suggestCandidates(constraintSystem)
            } catch (e: CandidatesNotExpressibleException) {
                learners.removeAt(index)
            }
        }
        ++index
        return candidates
    }
}