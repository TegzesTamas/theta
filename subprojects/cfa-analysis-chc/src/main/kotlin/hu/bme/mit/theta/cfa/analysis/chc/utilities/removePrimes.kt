package hu.bme.mit.theta.cfa.analysis.chc.utilities

import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.Type
import hu.bme.mit.theta.core.type.anytype.PrimeExpr

fun <T : Type> removePrimes(expr: Expr<T>): Expr<T> {
    return if (expr is PrimeExpr<T>) {
        removePrimes(expr.op)
    } else {
        expr.withOps(expr.ops.map { removePrimes(it) })
    }
}