package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ExprDecision
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ImpurityMeasure
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import java.util.*

interface PredicatePattern {
    data class Split(val expr: Expr<BoolType>, val error: Double) : Comparable<Split> {
        override fun compareTo(other: Split): Int = error.compareTo(other.error)
    }

    fun findAllSplits(datapointsToSplit: Set<Datapoint>, constraintSystem: ConstraintSystem, measure: ImpurityMeasure): Sequence<Split>
    fun findBestSplit(datapointsToSplit: Set<Datapoint>, constraintSystem: ConstraintSystem, measure: ImpurityMeasure): Split? =
            findAllSplits(datapointsToSplit, constraintSystem, measure).min()
}

