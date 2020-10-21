package hu.bme.mit.theta.cfa.analysis.chc.utilities

import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.Type
import hu.bme.mit.theta.core.type.anytype.PrimeExpr
import hu.bme.mit.theta.core.utils.TypeUtils

fun <T : Type> removePrimes(expr: Expr<T>): Expr<T> {
    return if (expr is PrimeExpr<T>) {
        removePrimes(expr.op)
    } else {
        expr.withOps(expr.ops.map { removePrimes(it) })
    }
}

fun <T : Type> getExprsOfSubType(type: T, exprs: Iterable<Expr<*>>): List<Expr<T>> {
    val destination = mutableListOf<Expr<T>>()
    collectSubexprsOfType(type, exprs, destination)
    return destination
}

fun <T : Type> collectSubexprsOfType(type: T, exprs: Iterable<Expr<*>>, destination: MutableCollection<Expr<T>>) {
    val toProcess = exprs.toMutableList()
    while (toProcess.isNotEmpty()) {
        val expr = toProcess.removeLast()
        try {
            val casted = TypeUtils.cast(expr, type)
            destination.add(casted)
        } catch (e: ClassCastException) {
            toProcess.addAll(expr.ops)
        }
    }
}

private fun <T> MutableList<T>.removeLast() = removeAt(lastIndex)