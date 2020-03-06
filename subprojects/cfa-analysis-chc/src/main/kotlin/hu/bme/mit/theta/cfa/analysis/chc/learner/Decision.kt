package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.CNFCandidates
import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.core.model.Valuation
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Not
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.booltype.NotExpr
import hu.bme.mit.theta.core.utils.ExprUtils


interface Decision {
    fun datapointCanBeTrue(datapoint: Datapoint): Boolean
    fun datapointCanBeFalse(datapoint: Datapoint): Boolean
    fun transformCandidates(ifTrue: CNFCandidates, ifFalse: CNFCandidates): CNFCandidates
}

data class InvariantDecision(val matching: Set<Invariant>) : Decision {
    override fun datapointCanBeTrue(datapoint: Datapoint) = datapoint.invariant in matching
    override fun datapointCanBeFalse(datapoint: Datapoint): Boolean = !datapointCanBeTrue(datapoint)
    override fun transformCandidates(ifTrue: CNFCandidates, ifFalse: CNFCandidates): CNFCandidates {
        val invariants = matching.toMutableSet()
        val candidateMap = mutableMapOf<Invariant, List<AndExpr>>()
        invariants += ifTrue.candidateMap.keys
        invariants += ifFalse.candidateMap.keys
        for (invariant in invariants) {
            if (invariant in matching) {
                candidateMap[invariant] = ifTrue.getOperators(invariant)
            } else {
                candidateMap[invariant] = ifFalse.getOperators(invariant)
            }
        }
        return CNFCandidates(ifFalse.default, candidateMap)
    }
}

abstract class LogicDecision : Decision {
    protected abstract val trueExpr: Expr<BoolType>
    protected abstract val falseExpr: Expr<BoolType>
    final override fun transformCandidates(ifTrue: CNFCandidates, ifFalse: CNFCandidates): CNFCandidates {
        val invariants = mutableSetOf<Invariant>()
        invariants += ifTrue.candidateMap.keys
        invariants += ifFalse.candidateMap.keys
        val candidateMap = mutableMapOf<Invariant, List<AndExpr>>()
        for (invariant in invariants) {
            candidateMap[invariant] = ifTrue.getOperators(invariant).andAlso(trueExpr) + ifFalse.getOperators(invariant).andAlso(falseExpr)
        }
        return CNFCandidates(
                ifTrue.default.andAlso(trueExpr) + ifFalse.default.andAlso(falseExpr),
                candidateMap
        )
    }

    private fun List<AndExpr>.andAlso(expr: Expr<BoolType>) = map { BoolExprs.And(it.ops + expr) }

}

data class VarValueDecision(val valuation: Valuation) : LogicDecision() {
    override val trueExpr: Expr<BoolType>
        get() = valuation.toExpr()
    override val falseExpr: NotExpr
        get() = Not(valuation.toExpr())

    override fun datapointCanBeTrue(datapoint: Datapoint) = !datapoint.valuation.disjoint(valuation)
    override fun datapointCanBeFalse(datapoint: Datapoint): Boolean = !datapoint.valuation.isLeq(valuation)


}

data class ExprDecision(override val trueExpr: Expr<BoolType>) : LogicDecision() {
    override val falseExpr: Expr<BoolType>
        get() = Not(trueExpr)

    override fun datapointCanBeTrue(datapoint: Datapoint): Boolean = ExprUtils.simplify(trueExpr, datapoint.valuation) != BoolExprs.False()
    override fun datapointCanBeFalse(datapoint: Datapoint): Boolean = ExprUtils.simplify(trueExpr, datapoint.valuation) != BoolExprs.True()
}