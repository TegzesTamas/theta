package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.core.model.MutableValuation
import kotlin.math.abs

class DecisionTreeBuilder(private val datapoints: Set<Datapoint>, constraints: List<Constraint>) {
    var constraintSystem = ConstraintSystem(datapoints, constraints)
    fun build(): DecisionTree {

        data class ParentSlot(val node: BranchBuildNode, val side: Boolean)
        data class SetToProcess(val datapoints: Set<Datapoint>, val slot: ParentSlot?)

        val toProcess = mutableListOf(SetToProcess(datapoints, null))
        val ready = mutableListOf<BuildNode>()
        while (toProcess.isNotEmpty()) {
            val (currDps, parentSlot) = toProcess.removeAt(0)

            val leaf = tryToLabel(currDps)

            val node: BuildNode
            if (leaf != null) {
                node = LeafBuildNode(leaf)
            } else {
                val decision = findSplittingDecision(currDps)
                val ifTrue = currDps.filter { decision.datapointCanBeTrue(it) }.toSet()
                val ifFalse = currDps.filter { decision.datapointCanBeFalse(it) }.toSet()
                node = BranchBuildNode(decision)
                toProcess += SetToProcess(ifTrue, ParentSlot(node, true))
                toProcess += SetToProcess(ifFalse, ParentSlot(node, false))
            }
            ready += node
            parentSlot?.let { (parent, side) ->
                if (side) {
                    parent.trueChild = node
                } else {
                    parent.falseChild = node
                }
            }
        }
        for (i in ready.lastIndex downTo 0) {
            ready[i].build()
        }
        return DecisionTree(ready[0].built!!)
    }

    private fun tryToLabel(datapoints: Set<Datapoint>): DecisionTree.Leaf? {
        if (datapoints.all { it in constraintSystem.existentiallyForcedTrue }) {
            return DecisionTree.Leaf(true)
        }
        if (datapoints.all { it in constraintSystem.existentiallyForcedFalse }) {
            return DecisionTree.Leaf(false)
        }
        if (datapoints.none { it in constraintSystem.universallyForcedFalse }) {
            constraintSystem.tryToSetDatapointsTrue(datapoints)?.let {
                constraintSystem = it
                return DecisionTree.Leaf(true)
            }
        }
        if (datapoints.none { it in constraintSystem.universallyForcedTrue }) {
            constraintSystem.tryToSetDatapointsFalse(datapoints)?.let {
                constraintSystem = it
                return DecisionTree.Leaf(false)
            }
        }
        return null
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

    private abstract class BuildNode {
        abstract val built: DecisionTree.Node?
        abstract fun build()
    }

    private data class BranchBuildNode(val pivot: Decision, var trueChild: BuildNode? = null, var falseChild: BuildNode? = null) : BuildNode() {
        override var built: DecisionTree.Branch? = null
            private set

        override fun build() {
            trueChild?.built?.let { ifTrue ->
                falseChild?.built?.let { ifFalse ->
                    built = DecisionTree.Branch(pivot, ifTrue, ifFalse)
                }
            } ?: kotlin.run { built = null }
        }
    }

    private data class LeafBuildNode(override val built: DecisionTree.Leaf) : BuildNode() {
        override fun build() {}
    }
}