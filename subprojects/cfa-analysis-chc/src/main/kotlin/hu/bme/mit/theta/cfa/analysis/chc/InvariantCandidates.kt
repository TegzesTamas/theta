package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.OrExpr

data class InvariantCandidates(val default: List<AndExpr>, val candidateMap: Map<Invariant, List<AndExpr>>) {
    operator fun get(invariant: Invariant): List<AndExpr> = candidateMap[invariant] ?: default
    fun getExpr(invariant: Invariant): OrExpr = OrExpr.create(this[invariant])
}