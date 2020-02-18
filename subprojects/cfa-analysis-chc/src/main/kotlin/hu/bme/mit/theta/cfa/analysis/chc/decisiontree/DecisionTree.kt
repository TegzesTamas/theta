package hu.bme.mit.theta.cfa.analysis.chc.decisiontree

import hu.bme.mit.theta.cfa.analysis.chc.CNFCandidates
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import kotlin.math.abs

class DecisionTree(datapoints: Set<Datapoint>, constraints: List<Constraint>) {
    private val root: Node = Builder(datapoints, constraints).build(datapoints)


    val candidates: CNFCandidates
        get() = root.candidates

    abstract class Node {
        abstract val candidates: CNFCandidates
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
        override val candidates: CNFCandidates by lazy {
            if (label)
                CNFCandidates(listOf(And(listOf(True()))), emptyMap())
            else
                CNFCandidates(emptyList(), emptyMap())
        }
    }

    class Branch(override val pivot: Decision,
                 override val ifTrue: Node,
                 override val ifFalse: Node) : Node() {
        override val candidates: CNFCandidates by lazy {
            pivot.transformCandidates(ifTrue.candidates, ifFalse.candidates)
        }
    }

    private class Builder(datapoints: Collection<Datapoint>, constraints: Collection<Constraint>) {

        private val constraintSystem = ConstraintSystem(datapoints, constraints)

        fun build(datapoints: Set<Datapoint>): Node {
            if (datapoints.none { it in constraintSystem.universallyForcedTrue }) {
                return Leaf(false)
            }
            if (datapoints.all { it in constraintSystem.existentiallyForcedTrue }) {
                return Leaf(true)
            }
            val decision = findSplittingDecision(datapoints)
            val (ifTrue, ifFalse) = datapoints.partition { decision.matchesDatapoint(it) }
            return Branch(decision, build(ifTrue.toSet()), build(ifFalse.toSet()))
        }

        private fun findSplittingDecision(datapointsToSplit: Set<Datapoint>): Decision {
            require(datapointsToSplit.size > 1) { "At least two datapoints needed." }
            val allInvariants = datapointsToSplit.map { it.invariant }.toMutableSet()

            if (allInvariants.size > 1) {
                val chosenInvariants = allInvariants.take(allInvariants.size / 2).toSet()
                return InvariantDecision(chosenInvariants)
            }


            val targetSplitSize = datapointsToSplit.size / 2
            val variableToDatapointsAndValues = datapointsToSplit.asSequence()
                    .flatMap { datapoint ->
                        datapoint.valuation.toMap()
                                .asSequence()
                                .map { valEntry -> valEntry }
                    }
                    .groupBy({ it.key }, { it.value })

            val variableToValuesToCount =
                    variableToDatapointsAndValues
                            .mapValues { (_, values) ->
                                values.groupingBy { it }
                                        .eachCount()
                            }
            val bestVariableToSplitBy = variableToValuesToCount
                    .asSequence()
                    .filter { (_, valueToOccurrences) -> valueToOccurrences.keys.size > 1 }
                    .minBy { (_, valueToOccurrences) -> valueToOccurrences.keys.size }
            assert(bestVariableToSplitBy != null)
            assert(bestVariableToSplitBy!!.value.isNotEmpty())
            val chosenVariable = bestVariableToSplitBy.key
            val (chosenValue, _) =
                    bestVariableToSplitBy.value.asSequence()
                            .minBy { abs(it.value - targetSplitSize) }!!
            val mutableValuation = MutableValuation()
            mutableValuation.put(chosenVariable, chosenValue)
            return VarValueDecision(mutableValuation)
        }

        class ConstraintSystem(datapoints: Collection<Datapoint>, constraints: Collection<Constraint>) {
            private val mutExistentiallyForcedTrue: MutableSet<Datapoint?> = mutableSetOf()
            private val mutUniversallyForcedTrue: MutableSet<Datapoint?> = mutableSetOf()
            private var ambiguousDatapoints = datapoints
            val existentiallyForcedTrue: Set<Datapoint?>
                get() = mutExistentiallyForcedTrue
            val universallyForcedTrue: Set<Datapoint?>
                get() = mutUniversallyForcedTrue
            var filteredConstraints: Collection<Constraint> = constraints
                private set

            var contradictory: Boolean = false
                private set

            init {
                calcForced()
            }

            private fun calcForced() {
                do {
                    val newUniversallyForcedTrue = filteredConstraints
                            .filter { it.source.isEmpty() }
                            .map { it.target }
                            .toMutableList()
                    if (null in newUniversallyForcedTrue) {
                        throw ContradictoryException("Unsatisfiable constraints")
                    }
                    mutUniversallyForcedTrue += newUniversallyForcedTrue
                    mutExistentiallyForcedTrue += newUniversallyForcedTrue
                    val stillAmbiguous = mutableSetOf<Datapoint>()
                    for (ambiguous in ambiguousDatapoints) {
                        if (ambiguous !in newUniversallyForcedTrue) {
                            if (newUniversallyForcedTrue.any { it != null && ambiguous.subsetOf(it) }) {
                                mutUniversallyForcedTrue += ambiguous
                            } else {
                                stillAmbiguous += ambiguous
                                if (newUniversallyForcedTrue.any { it != null && !ambiguous.disjoint(it) }) {
                                    mutExistentiallyForcedTrue += ambiguous
                                }
                            }
                        }
                    }
                    ambiguousDatapoints = stillAmbiguous
                    filteredConstraints = filteredConstraints
                            .filter { it.target !in universallyForcedTrue }
                            .map { c -> Constraint(c.source.filter { dp -> dp !in existentiallyForcedTrue }, c.target) }
                } while (newUniversallyForcedTrue.isNotEmpty())
            }

        }
    }

    class ContradictoryException(s: String? = null) : Throwable(s)
}