package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import java.io.Closeable

interface Learner : Closeable {
    @Throws(CandidatesNotExpressibleException::class)
    fun suggestCandidates(constraintSystem: ConstraintSystem): InvariantCandidates

    override fun close() {}

    class CandidatesNotExpressibleException(s: String? = null) : Throwable(s)
}