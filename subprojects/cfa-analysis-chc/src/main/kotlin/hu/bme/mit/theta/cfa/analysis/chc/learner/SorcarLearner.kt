package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.DNFCandidates
import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.constraint.eval
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.NullError
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.LeqPattern
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.PredicatePattern
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolType

class SorcarLearner(override val name: String,
                    private val predicatePatterns: Collection<PredicatePattern> = listOf(LeqPattern)
) : Learner {
    constructor(name: String, pattern: PredicatePattern) : this(name, listOf(pattern))

    override fun suggestCandidates(constraintSystem: ConstraintSystem): DNFCandidates {
        val atoms = predicatePatterns.asSequence().flatMap { it.findAllSplits(constraintSystem.datapoints, constraintSystem, NullError) }
        val candidates = mutableMapOf<Invariant, MutableList<Expr<BoolType>>>()

        for (invariant in constraintSystem.datapointsByInvariant.keys) {
            candidates[invariant] = atoms.mapNotNullTo(mutableListOf()) {
                if (isRelevant(it.expr, invariant, constraintSystem)) {
                    it.expr
                } else {
                    null
                }
            }
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
        return DNFCandidates(name, listOf(), candidates.mapValues { listOf(And(it.value)) })
    }

    private fun isRelevant(atom: Expr<BoolType>, invariant: Invariant, constraintSystem: ConstraintSystem) =
            constraintSystem.run {
                forcedFalse.any { dp -> dp.invariant == invariant && dp.eval(atom) != true }
                        || constraints.any { it.source != null && it.source.invariant == invariant && it.source.eval(atom) != true }
            }
}