package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Or
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.inttype.IntExprs.*
import hu.bme.mit.theta.core.type.inttype.IntType

class ModulusPattern(intExprs: Iterable<Expr<IntType>>) : ExprPattern() {
    override val atoms: List<Expr<BoolType>>

    init {
        val exprs = mutableListOf<Expr<BoolType>>()

        for (a in intExprs) {
            for (b in intExprs) {
                for (c in intExprs) {
                    // c <= 0 || a == (b mod c)
                    exprs.add(Or(Leq(c, Int(0)),
                            Eq(a, Mod(b, c))))
                }
            }
        }

        atoms = exprs
    }
}