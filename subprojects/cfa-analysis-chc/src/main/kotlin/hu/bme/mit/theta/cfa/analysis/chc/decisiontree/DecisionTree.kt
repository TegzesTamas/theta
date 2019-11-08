package hu.bme.mit.theta.cfa.analysis.chc.decisiontree

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.LitExpr
import hu.bme.mit.theta.core.type.abstracttype.Equational
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.core.type.booltype.BoolType

class DecisionTree {
    private var root: Node = Leaf(true)
    //TODO solver deciding which datapoints are forced true/forced false
    //TODO list of all datapoints

    val candidates: InvariantCandidates
        get() = root.candidates

    abstract class Node {
        abstract val candidates: InvariantCandidates
        abstract val pivot: Decision?
        abstract val ifTrue: Node?
        abstract val ifFalse: Node?
    }

    class Leaf(val label: Boolean) : Node() {
        override val pivot: Decision?
            get() = null
        override val ifTrue: Node?
            get() = null
        override val ifFalse: Node?
            get() = null
        override val candidates: InvariantCandidates by lazy {
            if (label)
                InvariantCandidates(listOf(And(listOf(True()))), emptyMap())
            else
                InvariantCandidates(emptyList(), emptyMap())
        }
    }

    class Branch(override val pivot: Decision,
                 override val ifTrue: Node,
                 override val ifFalse: Node) : Node() {
        override val candidates: InvariantCandidates by lazy {
            pivot.transformCandidates(ifTrue.candidates, ifFalse.candidates)
        }
    }
}

interface Decision {
    fun matchesDatapoint(datapoint: Datapoint): Boolean
    fun transformCandidates(ifTrue: InvariantCandidates, ifFalse: InvariantCandidates): InvariantCandidates
}

data class InvariantDecision(val matching: Set<Invariant>) : Decision {
    override fun matchesDatapoint(datapoint: Datapoint) = datapoint.invariant in matching
    override fun transformCandidates(ifTrue: InvariantCandidates, ifFalse: InvariantCandidates): InvariantCandidates {
        val invariants = matching.toMutableSet()
        val candidateMap = mutableMapOf<Invariant, List<AndExpr>>()
        invariants += ifTrue.candidateMap.keys
        invariants += ifFalse.candidateMap.keys
        for (invariant in invariants) {
            if (invariant in matching) {
                candidateMap[invariant] = ifTrue[invariant]
            } else {
                candidateMap[invariant] = ifFalse[invariant]
            }
        }
        return InvariantCandidates(ifFalse.default, candidateMap)
    }
}

data class VarValueDecision<T : Equational<T>>(val variable: VarDecl<T>, val value: LitExpr<T>, val type: T) : Decision {
    override fun matchesDatapoint(datapoint: Datapoint) = datapoint.valuation.toMap()[variable]?.let { it == value }
            ?: true

    override fun transformCandidates(ifTrue: InvariantCandidates, ifFalse: InvariantCandidates): InvariantCandidates {
        val invariants = mutableSetOf<Invariant>()
        invariants += ifTrue.candidateMap.keys
        invariants += ifFalse.candidateMap.keys
        val candidateMap = mutableMapOf<Invariant, List<AndExpr>>()
        val trueExpr = type.Eq(variable.ref, value)
        val falseExpr = type.Neq(variable.ref, value)
        for (invariant in invariants) {
            candidateMap[invariant] = ifTrue[invariant].andAlso(trueExpr) + ifFalse[invariant].andAlso(falseExpr)
        }
        return InvariantCandidates(
                ifTrue.default.andAlso(trueExpr) + ifFalse.default.andAlso(falseExpr),
                candidateMap
        )
    }

    private fun List<AndExpr>.andAlso(expr: Expr<BoolType>) = map { And(it.ops + expr) }
}