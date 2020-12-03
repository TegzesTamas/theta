package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.cfa.analysis.chc.utilities.getSubexprsOfType
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Or
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.inttype.IntExprs.*

class ModulusPattern(private val seedExprs: Iterable<Expr<*>>) : ExprPattern() {
    override val atoms: List<Expr<BoolType>> by lazy {
        val intExprs = getSubexprsOfType(Int(), seedExprs)
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
        return@lazy exprs
    }
}