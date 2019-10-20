package hu.bme.mit.theta.cfa.analysis.chc.decisiontree

import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.*

class DecisionTree {
    var root: Node = Leaf(BoolExprs.True())

    val asExpr: OrExpr
        get() = BoolExprs.Or(root.asExpr)

    abstract class Node {
        abstract val asExpr: List<AndExpr>
        abstract val pivot: Expr<BoolType>?
        abstract val ifTrue: Node?
        abstract val ifFalse: Node?
    }

    class Leaf(val label: BoolLitExpr) : Node() {
        override val pivot: Expr<BoolType>?
            get() = null
        override val ifTrue: Node?
            get() = null
        override val ifFalse: Node?
            get() = null
        override val asExpr: List<AndExpr> by lazy {
            listOf(BoolExprs.And(listOf(label)))
        }
    }

    class Decision(override val pivot: Expr<BoolType>?,
                   override val ifTrue: Node,
                   override val ifFalse: Node) : Node() {
        override val asExpr: List<AndExpr> by lazy {
            ifTrue.asExpr.map { BoolExprs.And(it.ops + pivot) } +
                    ifFalse.asExpr.map { BoolExprs.And(it.ops + BoolExprs.Not(pivot)) }
        }
    }
}