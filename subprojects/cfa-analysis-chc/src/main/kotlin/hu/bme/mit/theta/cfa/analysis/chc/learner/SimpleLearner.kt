package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.GenericCandidates
import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Or
import hu.bme.mit.theta.core.type.booltype.BoolExprs.False
import hu.bme.mit.theta.core.type.booltype.BoolType


class SimpleLearner(override val name: String) : Learner {
    override fun suggestCandidates(constraintSystem: ConstraintSystem): GenericCandidates {
        val candidateMap = mutableMapOf<Invariant, MutableList<Expr<BoolType>>>()
        for ((invariant, valuation) in constraintSystem.forcedTrue) {
            candidateMap.getOrPut(invariant) { mutableListOf() }.add(valuation.toExpr())
        }
        return GenericCandidates(
            name,
            False(),
            candidateMap.mapValues { Or(it.value) }
        )
    }
}