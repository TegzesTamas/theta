package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType

class ListPattern(override val atoms: Set<Expr<BoolType>>) : ExprPattern()