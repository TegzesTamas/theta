package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem

interface Learner {
    fun suggestCandidates(constraintSystem: ConstraintSystem): InvariantCandidates
}