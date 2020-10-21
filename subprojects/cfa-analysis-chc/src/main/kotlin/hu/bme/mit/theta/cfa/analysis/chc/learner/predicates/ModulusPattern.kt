package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.inttype.IntEqExpr
import hu.bme.mit.theta.core.type.inttype.IntExprs.Eq
import hu.bme.mit.theta.core.type.inttype.IntExprs.Mod
import hu.bme.mit.theta.core.type.inttype.IntType

class ModulusPattern(intExprs: Iterable<Expr<IntType>>) : ExprPattern() {
    override val atoms: List<IntEqExpr>

    init {
        val exprs = mutableListOf<IntEqExpr>()

        for (a in intExprs) {
            for (b in intExprs) {
                for (c in intExprs) {
                    // b mod c == a
                    exprs.add(Eq(Mod(b, c), a))
                }
            }
        }

        atoms = exprs
    }
}