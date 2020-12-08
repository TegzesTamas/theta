package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ExprDecision
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ImpurityMeasure
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.PredicatePattern.Split
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import java.util.*

abstract class ExprPattern : PredicatePattern {

    abstract val atoms: Sequence<Expr<BoolType>>

    override fun findAllSplits(
        datapointsToSplit: Set<Datapoint>,
        constraintSystem: ConstraintSystem,
        measure: ImpurityMeasure
    ): Sequence<Split> =
        atoms.map { atom ->
            val decision = ExprDecision(atom)
            val trueDatapoints = datapointsToSplit.filterTo(mutableSetOf()) { decision.datapointCanBeTrue(it) }
            val falseDatapoints = datapointsToSplit.filterTo(mutableSetOf()) { decision.datapointCanBeFalse(it) }
            val trueError = measure.impurity(
                trueDatapoints.count { constraintSystem.forcedTrue.contains(it) },
                trueDatapoints.count { constraintSystem.forcedFalse.contains(it) },
                trueDatapoints.count()
            )
            val falseError = measure.impurity(
                falseDatapoints.count { constraintSystem.forcedTrue.contains(it) },
                falseDatapoints.count { constraintSystem.forcedFalse.contains(it) },
                falseDatapoints.count()
            )
            val error = trueError + falseError
            return@map Split(atom, error)
        }
}