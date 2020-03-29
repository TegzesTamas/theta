package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.core.decl.Decl
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntLitExpr
import hu.bme.mit.theta.core.type.inttype.IntType
import kotlin.math.min

class DecisionTreeBuilder(private var constraintSystem: ConstraintSystem) {
    fun build(): DecisionTree {

        data class ParentSlot(val node: BranchBuildNode, val side: Boolean)
        data class SetToProcess(val datapoints: Set<Datapoint>, val slot: ParentSlot?)

        val toProcess = mutableListOf(SetToProcess(constraintSystem.datapoints, null))
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
        if (datapoints.all { it in constraintSystem.existentiallyTrue }) {
            return DecisionTree.Leaf(true)
        }
        if (datapoints.all { it in constraintSystem.existentiallyFalse }) {
            return DecisionTree.Leaf(false)
        }
        if (datapoints.none { it in constraintSystem.universallyFalse }) {
            constraintSystem.tryToSetDatapointsTrue(datapoints)?.let {
                constraintSystem = it
                return DecisionTree.Leaf(true)
            }
        }
        if (datapoints.none { it in constraintSystem.universallyTrue }) {
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

        val variableToOccurrences = datapointsToSplit.asSequence()
                .flatMap { datapoint ->
                    datapoint.valuation.toMap()
                            .asSequence()
                            .map { (variable, value) ->
                                (value as? IntLitExpr)?.let {
                                    @Suppress("UNCHECKED_CAST")
                                    VariableOccurrence(variable as Decl<IntType>, it, datapoint)
                                }
                            }
                            .filterNotNull()
                }
                .groupBy { it.variable }
                .mapValues { (_, occurrences) -> occurrences.sortedBy { it.value } }

        var bestVar: Decl<IntType>? = null
        var bestLit: IntLitExpr? = null
        var bestError: Int? = null

        for ((variable, occurrences) in variableToOccurrences) {
            val bestSplit = findBestSplitForVariable(occurrences, datapointsToSplit)
            if (bestSplit != null) {
                val (lit, error) = bestSplit
                if (bestError == null || error < bestError) {
                    bestVar = variable
                    bestLit = lit
                    bestError = error
                }
            }
        }
        if (bestVar != null && bestLit != null && bestError != null) {
            return ExprDecision(IntExprs.Leq(bestVar.ref, bestLit))
        } else {
            error("No viable split was found")
        }
    }

    private fun findBestSplitForVariable(
            orderedOccurrences: List<VariableOccurrence>,
            datapointsToSplit: Set<Datapoint>
    ): Pair<IntLitExpr, Int>? {
        val splittableDps = orderedOccurrences.map { it.datapoint }
        val (splittableTrue, splittableFalse) = calcForcedLabeling(splittableDps)
        val unsplittableDps = datapointsToSplit - splittableDps
        val (unsplittableTrue, unsplittableFalse) = calcForcedLabeling(unsplittableDps)

        var bestLit: IntLitExpr? = null
        var bestError: Int? = null

        var matchingTrue = unsplittableTrue
        var matchingFalse = unsplittableFalse
        var matchingTotal = unsplittableDps.size
        var nonMatchingTrue = splittableTrue
        var nonMatchingFalse = splittableFalse
        var nonMatchingTotal = splittableDps.size + unsplittableDps.size
        for ((_, lit, dp) in orderedOccurrences) {
            ++matchingTotal
            --nonMatchingTotal
            when (dp) {
                in constraintSystem.universallyTrue -> {
                    ++matchingTrue
                    --nonMatchingTrue
                }
                in constraintSystem.universallyFalse -> {
                    ++matchingFalse
                    --nonMatchingFalse
                }
            }
            val error = classificationError(matchingTrue, matchingFalse, matchingTotal) +
                    classificationError(nonMatchingTrue, nonMatchingFalse, nonMatchingTotal)

            if (bestError == null || error < bestError) {
                bestLit = lit
                bestError = error
            }
        }
        return bestLit?.let { bestError?.let { bestLit to bestError } }
    }

    private data class VariableOccurrence(val variable: Decl<IntType>, val value: IntLitExpr, val datapoint: Datapoint)
    private data class ForcedLabeling(val mustBeTrue: Int, val mustBeFalse: Int)

    private fun calcForcedLabeling(dps: Iterable<Datapoint>): ForcedLabeling {
        var mustBeTrue = 0
        var mustBeFalse = 0
        for (dp in dps) {
            when (dp) {
                in constraintSystem.universallyTrue -> ++mustBeTrue
                in constraintSystem.universallyFalse -> ++mustBeFalse
            }
        }
        return ForcedLabeling(mustBeTrue, mustBeFalse)
    }

    private fun classificationError(mustBeTrue: Int, mustBeFalse: Int, total: Int): Int = min(mustBeTrue, mustBeFalse)

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