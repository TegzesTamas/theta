package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ExprDecision
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ImpurityMeasure
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType

data class ListPattern(val atoms: Set<Expr<BoolType>>) : PredicatePattern {
    override fun findBestSplit(datapointsToSplit: Set<Datapoint>, constraintSystem: ConstraintSystem, measure: ImpurityMeasure): PredicatePattern.Split? {
        var bestExpr: Expr<BoolType>? = null
        var bestError: Double? = null

        for (atom in atoms) {
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
            val newError = trueError + falseError
            if (bestError == null || newError < bestError) {
                bestError = newError
                bestExpr = atom
            }
        }
        return if (bestExpr == null || bestError == null) {
            null
        } else {
            PredicatePattern.Split(bestExpr, bestError)
        }
    }
}