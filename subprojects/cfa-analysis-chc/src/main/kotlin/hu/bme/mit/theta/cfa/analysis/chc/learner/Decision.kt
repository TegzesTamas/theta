package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.CNFCandidates
import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.core.model.Valuation
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType


interface Decision {
    fun matchesDatapoint(datapoint: Datapoint): Boolean
    fun transformCandidates(ifTrue: CNFCandidates, ifFalse: CNFCandidates): CNFCandidates
}

data class InvariantDecision(val matching: Set<Invariant>) : Decision {
    override fun matchesDatapoint(datapoint: Datapoint) = datapoint.invariant in matching
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

data class VarValueDecision(val valuation: Valuation) : Decision {
    override fun matchesDatapoint(datapoint: Datapoint) = datapoint.valuation.isLeq(valuation)

    override fun transformCandidates(ifTrue: CNFCandidates, ifFalse: CNFCandidates): CNFCandidates {
        val invariants = mutableSetOf<Invariant>()
        invariants += ifTrue.candidateMap.keys
        invariants += ifFalse.candidateMap.keys
        val candidateMap = mutableMapOf<Invariant, List<AndExpr>>()
        val trueExpr = valuation.toExpr()
        val falseExpr = BoolExprs.Not(valuation.toExpr())
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