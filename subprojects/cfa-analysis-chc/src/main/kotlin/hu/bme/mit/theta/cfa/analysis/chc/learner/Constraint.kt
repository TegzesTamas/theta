package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.model.Valuation

data class Constraint(val source: List<Datapoint>, val target: Datapoint?)

data class Datapoint(val invariant: Invariant, val valuation: Valuation)

/**
 * Returns whether this datapoint can be separated from the other by a decision.
 * I.e. is there a decision which fits one but does not fit the other.
 */
fun Datapoint.disjoint(other: Datapoint): Boolean = invariant != other.invariant || valuation.disjoint(other.valuation)

/**
 * Returns whether this valuation can be separated from the other by a decision.
 * I.e. is there a decision which fits one but does not fit the other.
 */
private fun Valuation.disjoint(other: Valuation): Boolean {
    val valuationMap = this.toMap()
    val otherValuationMap = other.toMap()
    return valuationMap.entries.any { (key, value) ->
        val otherValue = otherValuationMap[key]
        return otherValue != null && value != null && otherValue != value
    }
}

fun intersection(a: Datapoint, b: Datapoint): Datapoint = Datapoint(a.invariant, intersection(a.valuation, b.valuation))

/**
 * Given two inseparable valuations, it returns a valuation that subsumes both of them.
 * The returned valuation assigns value to all of the variables that either of the valuation assigns value to, and the
 * value assigned to them is the one assigned by b, or if b does not assign a value, then the value assigned by a.
 */
private fun intersection(a: Valuation, b: Valuation): Valuation {
    val aMap = a.toMap()
    val bMap = b.toMap()
    val mutableValuation = MutableValuation()
    for ((key, value) in aMap.entries) {
        mutableValuation.put(key, value)
    }
    for ((key, value) in bMap.entries) {
        mutableValuation.put(key, value)
    }
    return mutableValuation
}

/**
 * Returns whether this Datapoint holds every time that Datapoint holds.
 * It returns true exactly when both datapoints refer to the same invariant and this datapoint assigns the same value
 * to all the Decls the other assigns value to and possibly assignes values to other Decls.
 */
fun Datapoint.subsetOf(that: Datapoint): Boolean = this.invariant == that.invariant && this.valuation.subsetOf(that.valuation)

/**
 * Returns whether this valuation assignes the same value to all the Decls the other assignes value to and possibly
 * assignes value to other Decls.
 */
private fun Valuation.subsetOf(that: Valuation): Boolean = this.isLeq(that)