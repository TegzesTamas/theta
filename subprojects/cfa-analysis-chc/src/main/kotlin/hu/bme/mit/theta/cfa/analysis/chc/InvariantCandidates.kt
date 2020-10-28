package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Or
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.booltype.OrExpr

interface InvariantCandidates {
    val learnerName: String
    operator fun get(invariant: Invariant): Expr<BoolType>
}

data class GenericCandidates(override val learnerName: String, val default: Expr<BoolType>, val candidateMap: Map<Invariant, Expr<BoolType>>) : InvariantCandidates {
    override fun get(invariant: Invariant): Expr<BoolType> = candidateMap[invariant] ?: default
}

data class CNFCandidates(override val learnerName: String, val default: List<AndExpr>, val candidateMap: Map<Invariant, List<AndExpr>>) : InvariantCandidates {
    fun getOperators(invariant: Invariant): List<AndExpr> = candidateMap[invariant] ?: default
    override operator fun get(invariant: Invariant): OrExpr = Or(this.getOperators(invariant))
}