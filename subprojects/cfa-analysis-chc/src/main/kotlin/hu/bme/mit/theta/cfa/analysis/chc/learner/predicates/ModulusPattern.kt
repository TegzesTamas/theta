package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.cfa.analysis.chc.utilities.getSubexprsOfTypeAsSequence
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Or
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.inttype.IntExprs.*

class ModulusPattern(private val seedExprs: Iterable<Expr<*>>) : ExprPattern() {
    override val atoms: Sequence<Expr<BoolType>>
        get() =
            getSubexprsOfTypeAsSequence(Int(), seedExprs)
                .flatMap { a ->
                    getSubexprsOfTypeAsSequence(Int(), seedExprs)
                        .flatMap { b ->
                            getSubexprsOfTypeAsSequence(Int(), seedExprs).map { c ->
                                Or(
                                    Leq(c, Int(0)),
                                    Eq(a, Mod(b, c))
                                )
                            }
                        }
                }
}