package hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.predicates

import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ImpurityMeasure
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType

interface PredicatePattern {
    data class Split(val expr: Expr<BoolType>, val error: Double)

    fun findBestSplit(datapointsToSplit: Set<Datapoint>, constraintSystem: ConstraintSystem, measure: ImpurityMeasure): Split?
}

