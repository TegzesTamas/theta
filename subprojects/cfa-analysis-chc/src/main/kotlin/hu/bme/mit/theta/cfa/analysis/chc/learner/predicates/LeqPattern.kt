package hu.bme.mit.theta.cfa.analysis.chc.learner.predicates

import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ImpurityMeasure
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.PredicatePattern.Split
import hu.bme.mit.theta.core.decl.Decl
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntLitExpr
import hu.bme.mit.theta.core.type.inttype.IntType
import java.util.*

object LeqPattern : PredicatePattern {
    private data class VariableOccurrence(val variable: Decl<IntType>, val lit: IntLitExpr, val datapoint: Datapoint)
    private data class ForcedLabeling(val mustBeTrue: Int, val mustBeFalse: Int)

    override fun findAllSplits(datapointsToSplit: Set<Datapoint>, constraintSystem: ConstraintSystem, measure: ImpurityMeasure): PriorityQueue<Split> {
        val variableToOccurrences = datapointsToSplit.asSequence()
                .flatMap { datapoint ->
                    datapoint.valuation.toMap()
                            .asSequence()
                            .mapNotNull { (variable, value) ->
                                (value as? IntLitExpr)?.let {
                                    @Suppress("UNCHECKED_CAST")
                                    (VariableOccurrence(variable as Decl<IntType>, it, datapoint))
                                } //TODO BitVector support
                            }
                }
                .groupBy { it.variable }
                .asSequence()
                .map { (variable, occurrences) -> variable to occurrences.groupBy { it.lit } }

        val splits = PriorityQueue<Split>()

        for ((variable, occurrences) in variableToOccurrences) {
            if (occurrences.size > 1) {
                collectAllSplitsForVariable(splits, variable, occurrences,
                        datapointsToSplit, constraintSystem, measure)
            }
        }

        return splits
    }

    private fun collectAllSplitsForVariable(
            splits: MutableCollection<Split>,
            variable: Decl<IntType>,
            occurrencesByLit: Map<IntLitExpr, List<VariableOccurrence>>,
            datapointsToSplit: Set<Datapoint>,
            constraintSystem: ConstraintSystem,
            measure: ImpurityMeasure
    ) {
        //In splittableDps, every datapoint occurs at most once, because it assigns at most one value to a variable
        val splittableDps = occurrencesByLit.values.flatMap { it.map { occurrence -> occurrence.datapoint } }
        val (splittableTrue, splittableFalse) = calcForcedLabeling(splittableDps, constraintSystem)
        val unsplittableDps = datapointsToSplit - splittableDps
        val (unsplittableTrue, unsplittableFalse) = calcForcedLabeling(unsplittableDps, constraintSystem)

        var leqMatchingTrue = unsplittableTrue
        var leqMatchingFalse = unsplittableFalse
        var matchingTotal = unsplittableDps.size
        var leqNonMatchingTrue = unsplittableTrue + splittableTrue
        var leqNonMatchingFalse = unsplittableFalse + splittableFalse
        var nonMatchingTotal = splittableDps.size + unsplittableDps.size


        val orderedLiterals = occurrencesByLit.entries.sortedBy { it.key.value }

        for ((lit, currentOccurrences) in orderedLiterals) {
            matchingTotal += currentOccurrences.size
            nonMatchingTotal -= currentOccurrences.size

            val currentTrue = currentOccurrences.count { it.datapoint in constraintSystem.forcedTrue }
            val currentFalse = currentOccurrences.count { it.datapoint in constraintSystem.forcedFalse }

            leqMatchingTrue += currentTrue
            leqNonMatchingTrue -= currentTrue
            leqMatchingFalse += currentFalse
            leqNonMatchingFalse -= currentFalse

            val leqError = measure.impurity(leqMatchingTrue, leqMatchingFalse, matchingTotal) +
                    measure.impurity(leqNonMatchingTrue, leqNonMatchingFalse, nonMatchingTotal)

            splits.add(Split(IntExprs.Leq(variable.ref, lit), leqError))

            val eqError = measure.impurity(
                    currentTrue + unsplittableTrue,
                    currentFalse + unsplittableFalse,
                    currentOccurrences.size + unsplittableDps.size
            ) + measure.impurity(
                    splittableTrue - currentTrue + unsplittableTrue,
                    splittableFalse - currentFalse + unsplittableFalse,
                    splittableDps.size - currentOccurrences.size + unsplittableDps.size
            )

            splits.add(Split(IntExprs.Eq(variable.ref, lit), eqError))
        }
    }

    private fun calcForcedLabeling(dps: Iterable<Datapoint>, constraintSystem: ConstraintSystem): ForcedLabeling {
        var mustBeTrue = 0
        var mustBeFalse = 0
        for (dp in dps) {
            when (dp) {
                in constraintSystem.forcedTrue -> ++mustBeTrue
                in constraintSystem.forcedFalse -> ++mustBeFalse
            }
        }
        return ForcedLabeling(mustBeTrue, mustBeFalse)
    }

}