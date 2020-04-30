package hu.bme.mit.theta.cfa.analysis.chc.constraint

import hu.bme.mit.theta.cfa.analysis.chc.CHC
import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.model.Valuation

data class Constraint(val source: Datapoint?, val target: Datapoint?, val causingCHC: CHC) {
    override fun toString(): String = "[$source -> $target]"
}

data class Datapoint(val invariant: Invariant, val valuation: Valuation) {
    override fun toString(): String = "$invariant(${valuation.toMap()})"
}

fun Constraint.deducePositively(forcedTrue: Datapoint): Datapoint? {
    if (source == null) {
        return target
    }
    require(!source.disjoint(forcedTrue)) { "Cannot deduce from a disjoint datapoint" }
    if (target == null) {
        return null
    }
    val intersection = intersection(source.valuation, forcedTrue.valuation)
    val deducedValuation = MutableValuation()
    for ((decl, value) in intersection.toMap().entries) {
        if (decl is VarDecl<*> && causingCHC.postIndexing[decl] == 0) {
            deducedValuation.put(decl, value)
        }
    }
    return Datapoint(target.invariant, intersection(deducedValuation, target.valuation))
}

fun Constraint.deduceNegatively(forcedFalse: Datapoint): Datapoint? {
    if (target == null) {
        return source
    }
    require(!target.disjoint(forcedFalse)) { "Cannot deduce from a disjoint datapoint" }
    if (source == null) {
        return null
    }
    val intersection = intersection(target.valuation, forcedFalse.valuation)
    val deducedValuation = MutableValuation()
    for ((decl, value) in intersection.toMap().entries) {
        if (decl is VarDecl<*> && causingCHC.postIndexing[decl] == 0) {
            deducedValuation.put(decl, value)
        }
    }
    return Datapoint(source.invariant, intersection(deducedValuation, source.valuation))
}


/**
 * Returns whether this datapoint can be separated from the other by a decision.
 * I.e. is there a decision which fits one but does not fit the other.
 */
fun Datapoint.disjoint(other: Datapoint): Boolean = invariant != other.invariant || valuation.disjoint(other.valuation)

/**
 * Returns whether this valuation can be separated from the other by a decision.
 * I.e. is there a decision which fits one but does not fit the other.
 */
fun Valuation.disjoint(other: Valuation): Boolean {
    val valuationMap = this.toMap()
    val otherValuationMap = other.toMap()
    return valuationMap.entries.any { (key, value) ->
        val otherValue = otherValuationMap[key]
        return@any otherValue != null && value != null && otherValue != value
    }
}

fun intersection(a: Datapoint, b: Datapoint): Datapoint {
    require(a.invariant == b.invariant)
    return Datapoint(b.invariant, intersection(a.valuation, b.valuation))
}

/**
 * Given two inseparable valuations, it returns a valuation that subsumes both of them.
 * The returned valuation assigns value to all of the variables that either of the valuation assigns value to, and the
 * value assigned to them is the one assigned by b, or if b does not assign a value, then the value assigned by a.
 * Requires the two valuations to be disjoint.
 */
private fun intersection(a: Valuation, b: Valuation): Valuation {
    val aMap = a.toMap()
    val bMap = b.toMap()
    val mutableValuation = MutableValuation()
    for ((key, value) in aMap.entries) {
        mutableValuation.put(key, value)
    }
    for ((key, value) in bMap.entries) {
        if (aMap[key]?.let { it != value } == true) {
            error("Valuations not disjoint.")
        }
        mutableValuation.put(key, value)
    }
    return mutableValuation
}

/**
 * Returns whether this Datapoint holds every time that Datapoint holds.
 * It returns true exactly when both datapoints refer to the same invariant and this datapoint assigns the same value
 * to all the Decls the other assigns value to and possibly assigns values to other Decls.
 */
fun Datapoint.subsetOf(that: Datapoint): Boolean = this === that || (this.invariant == that.invariant && this.valuation.subsetOf(that.valuation))

/**
 * Returns whether this valuation assigns the same value to all the Decls the other assigns value to and possibly
 * assigns value to other Decls.
 */
private fun Valuation.subsetOf(that: Valuation): Boolean = this.isLeq(that)