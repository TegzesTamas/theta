package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem

interface Learner {
    val name: String
    @Throws(CandidatesNotExpressibleException::class)
    fun suggestCandidates(constraintSystem: ConstraintSystem): InvariantCandidates

    class CandidatesNotExpressibleException(s: String? = null) : Throwable(s)
}