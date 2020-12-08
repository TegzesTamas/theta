package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType

class ListPattern(private val atomSet: Set<Expr<BoolType>>) : ExprPattern(){
    override val atoms: Sequence<Expr<BoolType>>
        get() = atomSet.asSequence()

}