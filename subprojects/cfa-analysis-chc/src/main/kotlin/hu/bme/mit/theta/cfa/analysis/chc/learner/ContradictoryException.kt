package hu.bme.mit.theta.cfa.analysis.chc.learner

class ContradictoryException(val contradictorySubset: List<Constraint>, s: String? = null) : Throwable(s)