package hu.bme.mit.theta.cfa.analysis.chc.decisiontree

import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import kotlin.math.abs

class DecisionTree(datapoints: Set<Datapoint>, constraints: Sequence<Constraint>) {
    private var root: Node = Builder(datapoints, constraints).build(datapoints)


    val candidates: InvariantCandidates
        get() = root.candidates

    abstract class Node {
        abstract val candidates: InvariantCandidates
        abstract val pivot: Decision?
        abstract val ifTrue: Node?
        abstract val ifFalse: Node?
    }

    class Leaf(val label: Boolean) : Node() {
        override val pivot: Decision?
            get() = null
        override val ifTrue: Node?
            get() = null
        override val ifFalse: Node?
            get() = null
        override val candidates: InvariantCandidates by lazy {
            if (label)
                InvariantCandidates(listOf(And(listOf(True()))), emptyMap())
            else
                InvariantCandidates(emptyList(), emptyMap())
        }
    }

    class Branch(override val pivot: Decision,
                 override val ifTrue: Node,
                 override val ifFalse: Node) : Node() {
        override val candidates: InvariantCandidates by lazy {
            pivot.transformCandidates(ifTrue.candidates, ifFalse.candidates)
        }
    }

    private class Builder(datapoints: Set<Datapoint>, constraints: Sequence<Constraint>) {
        private val constraints: Sequence<Constraint>
        private val forcedTrue: Set<Datapoint>

        init {
            val constraintSystem = calcForced(constraints)
                    ?: throw ContradictoryException("Constraints cannot be satisfied")
            this.constraints = constraintSystem.filteredConstraints
            this.forcedTrue = constraintSystem.forcedTrue
        }

        fun build(datapoints: Set<Datapoint>): Node {
            if (forcedTrue.isEmpty()) {
                return Leaf(false)
            }
            if (forcedTrue.containsAll(datapoints)) {
                return Leaf(true)
            }
            val decision = findSplitting(forcedTrue, datapoints)
            val (ifTrue, ifFalse) = datapoints.partition { decision.matchesDatapoint(it) }
            return Branch(decision, build(ifTrue.toSet()), build(ifFalse.toSet()))
        }

        private fun findSplitting(all: Set<Datapoint>, forcedTrue: Set<Datapoint>): Decision {
            require(all.isNotEmpty()) { "no datapoints given" }
            require(forcedTrue.isNotEmpty()) { "no splitting necessary" }
            val allInvariants = all.map { it.invariant }.toMutableSet()

            if (allInvariants.size > 1) {
                val chosenInvariants = allInvariants.take(allInvariants.size / 2).toSet()
                return InvariantDecision(chosenInvariants)
            }

            val forcedTrueValuations =
                    forcedTrue.asSequence()
                            .flatMap { it.valuation.toMap().entries.asSequence() }
                            .groupBy { it.key }


            val targetSplitSize = all.size / 2
            val variableToValuesMap = all.asSequence()
                    .flatMap { datapoint ->
                        datapoint.valuation.toMap()
                                .asSequence()
                                .map { entry -> datapoint to entry }
                    }
                    .groupBy { (_, valEntry) -> valEntry.key }
            val valueOccurenceCount =
                    variableToValuesMap
                            .asSequence()
                            .map { (variable, valuesInDatapoints) ->
                                val valuationToOccurrenceCount = valuesInDatapoints
                                        .groupBy { it.second.value }
                                        .asSequence()
                                        .filter { it.value.size > 1 }
                                        .map { (_, occurrences) ->
                                            occurrences.first().first.valuation to occurrences.distinctBy { it.first }.count()
                                        }
                                variable to valuationToOccurrenceCount.minBy { abs(it.second - targetSplitSize) }
                            }
                            .maxBy { abs(it.second?.second ?: 0 - targetSplitSize) }!!

            val valuation = valueOccurenceCount.second!!.first
            val chosenVariable = valueOccurenceCount.first
            val mutableValuation = MutableValuation.copyOf(valuation)
            for (decl in valuation.decls) {
                if (decl != chosenVariable) {
                    mutableValuation.remove(decl)
                }
            }
            return VarValueDecision(mutableValuation)
        }

        data class ConstraintSystem(val forcedTrue: Set<Datapoint>, val filteredConstraints: Sequence<Constraint>)

        private fun calcForced(constraints: Sequence<Constraint>): ConstraintSystem? {
            val forcedTrue = mutableSetOf<Datapoint?>()
            var filteredConstraints = constraints
            do {
                val prevSize = forcedTrue.size
                forcedTrue.addAll(
                        filteredConstraints
                                .filter { it.source.isEmpty() }
                                .map { it.target }
                )
                if (null in forcedTrue) {
                    return null
                }
                filteredConstraints = filteredConstraints
                        .filter { it.target !in forcedTrue }
                        .map { Constraint(it.source.filter { it in forcedTrue }, it.target) }
            } while (forcedTrue.size != prevSize)
            return ConstraintSystem(forcedTrue.filterNotNull().toSet(), filteredConstraints)
        }
    }

    class ContradictoryException(s: String) : Throwable()
}