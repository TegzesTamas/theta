package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.DEBUG
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.tryToSetDatapointsFalse
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.tryToSetDatapointsTrue
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.Decision
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.DecisionTree
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ExprDecision
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.InvariantDecision
import hu.bme.mit.theta.core.decl.Decl
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.inttype.IntExprs.Eq
import hu.bme.mit.theta.core.type.inttype.IntExprs.Leq
import hu.bme.mit.theta.core.type.inttype.IntLitExpr
import hu.bme.mit.theta.core.type.inttype.IntType
import kotlin.math.min

class Learner(private var constraintSystem: ConstraintSystem) {

    fun buildTree(): DecisionTree {

        data class ParentSlot(val node: BranchBuildNode, val side: Boolean)
        data class SetToProcess(val datapoints: Set<Datapoint>, val slot: ParentSlot?)

        val toProcess = mutableListOf(SetToProcess(constraintSystem.datapoints.keys, null))
        val ready = mutableListOf<BuildNode>()
        while (toProcess.isNotEmpty()) {
            val (currDps, parentSlot) = toProcess.removeAt(0)

            val leaf = tryToLabel(currDps)

            val node: BuildNode
            if (leaf != null) {
                node = LeafBuildNode(leaf)
            } else {
                val decision = findSplittingDecision(currDps)
                val ifTrue: Set<Datapoint> = currDps
                        .filterTo(mutableSetOf()) { decision.datapointCanBeTrue(it) }
                val ifFalse = currDps
                        .filterTo(mutableSetOf()) { decision.datapointCanBeFalse(it) }
                node = BranchBuildNode(decision)
                if (DEBUG) {
                    if (ifTrue == currDps || ifFalse == currDps) {
                        error("Tried to split datapoints, but one of the new nodes have the same datapoints as before")
                    }
                }
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
        if (datapoints.all { constraintSystem.forcedTrue.containsKey(it) }) {
            return DecisionTree.Leaf(true)
        }
        if (datapoints.all { constraintSystem.forcedFalse.contains(it) }) {
            return DecisionTree.Leaf(false)
        }
        if (datapoints.none { constraintSystem.forcedFalse.contains(it) }) {
            constraintSystem.tryToSetDatapointsTrue(datapoints)?.let {
                constraintSystem = it
                return DecisionTree.Leaf(true)
            }
        }
        if (datapoints.none { constraintSystem.forcedTrue.containsKey(it) }) {
            constraintSystem.tryToSetDatapointsFalse(datapoints)?.let {
                constraintSystem = it
                return DecisionTree.Leaf(false)
            }
        }
        return null
    }


    private fun findSplittingDecision(datapointsToSplit: Set<Datapoint>): Decision {
        require(datapointsToSplit.size > 1) { "At least two datapoints needed." }
        val allInvariants = datapointsToSplit.map { it.invariant }.toSet()

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
                .mapValues { (_, occurrences) -> occurrences.groupBy { it.lit } }

        var bestExpr: Expr<BoolType>? = null
        var bestError: Double? = null

        for ((variable, occurrences) in variableToOccurrences) {
            if (occurrences.size > 1) {
                val bestSplit = findBestSplitForVariable(variable, occurrences, datapointsToSplit)
                if (bestSplit != null) {
                    val (expr, error) = bestSplit
                    if (bestError == null || error < bestError) {
                        bestExpr = expr
                        bestError = error
                    }
                }
            }
        }
        if (bestExpr != null && bestError != null) {
            return ExprDecision(bestExpr)
        } else {
            error("No viable split was found")
        }
    }

    private fun findBestSplitForVariable(
            variable: Decl<IntType>,
            occurrencesByLit: Map<IntLitExpr, List<VariableOccurrence>>,
            datapointsToSplit: Set<Datapoint>
    ): Pair<Expr<BoolType>, Double>? {
        //In splittableDps, every datapoint occurs at most once, because it assigns at most one value to a variable
        val splittableDps = occurrencesByLit.values.flatMap { it.map { occurrence -> occurrence.datapoint } }
        val (splittableTrue, splittableFalse) = calcForcedLabeling(splittableDps)
        val unsplittableDps = datapointsToSplit - splittableDps
        val (unsplittableTrue, unsplittableFalse) = calcForcedLabeling(unsplittableDps)

        var leqMatchingTrue = unsplittableTrue
        var leqMatchingFalse = unsplittableFalse
        var matchingTotal = unsplittableDps.size
        var leqNonMatchingTrue = unsplittableTrue + splittableTrue
        var leqNonMatchingFalse = unsplittableFalse + splittableFalse
        var nonMatchingTotal = splittableDps.size + unsplittableDps.size

        var bestExpr: Expr<BoolType>? = null
        var bestError: Double? = null


        val orderedLiterals = occurrencesByLit.entries.asSequence().sortedBy { it.key.value }

        for ((lit, currentOccurrences) in orderedLiterals) {
            matchingTotal += currentOccurrences.size
            nonMatchingTotal -= currentOccurrences.size

            val currentTrue = currentOccurrences.count { it.datapoint in constraintSystem.forcedTrue }
            val currentFalse = currentOccurrences.count { it.datapoint in constraintSystem.forcedFalse }

            leqMatchingTrue += currentTrue
            leqNonMatchingTrue -= currentTrue
            leqMatchingFalse += currentFalse
            leqNonMatchingFalse -= currentFalse

            val leqError = classificationError(leqMatchingTrue, leqMatchingFalse, matchingTotal) +
                    classificationError(leqNonMatchingTrue, leqNonMatchingFalse, nonMatchingTotal)

            if (bestError == null || leqError < bestError) {
                bestExpr = Leq(variable.ref, lit)
                bestError = leqError
            }

            val eqError = classificationError(
                    currentTrue + unsplittableTrue,
                    currentFalse + unsplittableFalse,
                    currentOccurrences.size + unsplittableDps.size
            ) + classificationError(
                    splittableTrue - currentTrue + unsplittableTrue,
                    splittableFalse - currentFalse + unsplittableFalse,
                    splittableDps.size - currentOccurrences.size + unsplittableDps.size
            )
            if (eqError < bestError) {
                bestExpr = Eq(variable.ref, lit)
                bestError = eqError
            }
        }
        return if (bestError == null || bestExpr == null) {
            null
        } else {
            bestExpr to bestError
        }
    }

    private data class VariableOccurrence(val variable: Decl<IntType>, val lit: IntLitExpr, val datapoint: Datapoint)
    private data class ForcedLabeling(val mustBeTrue: Int, val mustBeFalse: Int)

    private fun calcForcedLabeling(dps: Iterable<Datapoint>): ForcedLabeling {
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

    private fun classificationError(mustBeTrue: Int, mustBeFalse: Int, total: Int): Double = min(mustBeTrue, mustBeFalse) + (total - mustBeTrue - mustBeFalse) / 2.0

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