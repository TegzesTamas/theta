package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.CNFCandidates
import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.constraint.eval
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolType

class SorcarLearner(private val atoms: Set<Expr<BoolType>>) : Learner {

    override fun suggestCandidates(constraintSystem: ConstraintSystem): CNFCandidates {
        val candidates = mutableMapOf<Invariant, MutableList<Expr<BoolType>>>()

        for (invariant in constraintSystem.datapointsByInvariant.keys) {
            candidates[invariant] = atoms.filterTo(mutableListOf()) { isRelevant(it, invariant, constraintSystem) }
        }

        var curCS = constraintSystem

        do {
            var consistent = true
            val builder = curCS.builder()
            for ((invariant, chosenAtoms) in candidates) {
                val datapoints = curCS.datapointsByInvariant[invariant] ?: emptyList()
                val forcedTrue = datapoints.filter { curCS.forcedTrue.contains(it) }
                chosenAtoms.retainAll { atom ->
                    forcedTrue.all { it.eval(atom) == true }
                }
                val newlyTrueDatapoints = datapoints.filter { dp ->
                    !forcedTrue.contains(dp) && chosenAtoms.all { atom -> dp.eval(atom) == true }
                }
                if (newlyTrueDatapoints.isNotEmpty()) {
                    consistent = false
                    try {
                        builder.labelDatapointsTrue(newlyTrueDatapoints)
                    } catch (e: ContradictoryException) {
                        throw Learner.CandidatesNotExpressibleException("Used atoms ($atoms) cannot express satisfactory candidates.")
                    }
                }
            }
            curCS = builder.build()
        } while (!consistent)
        return CNFCandidates(listOf(), candidates.mapValues { listOf(And(it.value)) })
    }

    private fun isRelevant(atom: Expr<BoolType>, invariant: Invariant, constraintSystem: ConstraintSystem) =
            constraintSystem.run {
                forcedFalse.any { dp -> dp.invariant == invariant && dp.eval(atom) != true }
                        || constraints.any { it.source != null && it.source.invariant == invariant && it.source.eval(atom) != true }
            }
}