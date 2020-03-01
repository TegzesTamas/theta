package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.CNFCandidates
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True

class DecisionTree(private val root: Node) {
    val candidates: CNFCandidates
        get() = root.candidates

    abstract class Node {
        abstract val candidates: CNFCandidates
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
        override val candidates: CNFCandidates by lazy {
            if (label)
                CNFCandidates(listOf(And(listOf(True()))), emptyMap())
            else
                CNFCandidates(emptyList(), emptyMap())
        }
    }

    class Branch(override val pivot: Decision,
                 override val ifTrue: Node,
                 override val ifFalse: Node) : Node() {
        override val candidates: CNFCandidates by lazy {
            pivot.transformCandidates(ifTrue.candidates, ifFalse.candidates)
        }
    }
}