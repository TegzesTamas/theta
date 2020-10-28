package hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True

class DecisionTree(private val root: Node) {
    val candidates: CNFInvariantMap
        get() = root.invariantMap

    data class CNFInvariantMap(val default: List<AndExpr>, val candidateMap: Map<Invariant, List<AndExpr>>) {
        fun getOperators(invariant: Invariant): List<AndExpr> = candidateMap[invariant] ?: default
    }

    abstract class Node {
        abstract val invariantMap: CNFInvariantMap
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
        override val invariantMap: CNFInvariantMap by lazy {
            if (label)
                CNFInvariantMap(listOf(And(listOf(True()))), emptyMap())
            else
                CNFInvariantMap(emptyList(), emptyMap())
        }
    }

    class Branch(override val pivot: Decision,
                 override val ifTrue: Node,
                 override val ifFalse: Node) : Node() {
        override val invariantMap: CNFInvariantMap by lazy {
            pivot.transformCandidates(ifTrue.invariantMap, ifFalse.invariantMap)
        }
    }
}