package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.cfa.analysis.chc.utilities.getSubexprsOfTypeAsSequence
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.inttype.IntExprs.*

class IntBuilderPattern(private val seedExprs: List<Expr<*>>) : ExprPattern() {
    override val atoms: Sequence<Expr<BoolType>>
        get() = getSubexprsOfTypeAsSequence(Int(), seedExprs)
            .flatMap { lhs ->
                getSubexprsOfTypeAsSequence(Int(), seedExprs).flatMap { rhs ->
                    sequenceOf(
                        Eq(lhs, rhs),
                        Lt(lhs, rhs)
                    )
                }
            }
}