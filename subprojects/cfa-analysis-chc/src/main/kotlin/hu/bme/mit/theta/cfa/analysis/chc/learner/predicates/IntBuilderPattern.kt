package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.core.type.BinaryExpr
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.inttype.IntExprs.*
import hu.bme.mit.theta.core.type.inttype.IntType

class IntBuilderPattern(intExprs: List<Expr<IntType>>) : ExprPattern() {
    override val atoms: List<BinaryExpr<IntType, BoolType>>

    init {
        val exprs = mutableListOf<BinaryExpr<IntType, BoolType>>()
        for (lhsIndex in intExprs.indices) {
            val lhs = intExprs[lhsIndex]
            for (rhsIndex in lhsIndex..intExprs.lastIndex) {
                val rhs = intExprs[rhsIndex]
                exprs.add(Eq(lhs, rhs))
                exprs.add(Lt(lhs, rhs))
                exprs.add(Gt(lhs, rhs))
            }
        }
        atoms = exprs
    }
}