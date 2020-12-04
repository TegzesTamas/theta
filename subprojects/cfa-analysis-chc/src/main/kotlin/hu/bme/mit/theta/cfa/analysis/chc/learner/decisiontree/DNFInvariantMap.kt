package hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.core.type.booltype.AndExpr

data class DNFInvariantMap(val default: List<AndExpr>, val candidateMap: Map<Invariant, List<AndExpr>>) {
        fun getOperators(invariant: Invariant): List<AndExpr> = candidateMap[invariant] ?: default
    }
