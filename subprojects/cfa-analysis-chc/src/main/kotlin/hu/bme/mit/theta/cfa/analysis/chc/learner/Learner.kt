package hu.bme.mit.theta.cfa.analysis.chc.learner

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
import hu.bme.mit.theta.core.type.inttype.IntExprs.*
import hu.bme.mit.theta.core.type.inttype.IntLitExpr
import hu.bme.mit.theta.core.type.inttype.IntType
import kotlin.math.min

class Learner(private var constraintSystem: ConstraintSystem) {
    fun buildTree(): DecisionTree {

        data class ParentSlot(val node: BranchBuildNode, val side: Boolean)
        data class SetToProcess(val datapoints: Map<Datapoint, Boolean>, val slot: ParentSlot?)

        val toProcess = mutableListOf(SetToProcess(constraintSystem.datapoints.asSequence().map { it to true }.toMap(), null))
        val ready = mutableListOf<BuildNode>()
        while (toProcess.isNotEmpty()) {
            val (currDps, parentSlot) = toProcess.removeAt(0)

            val leaf = tryToLabel(currDps)

            val node: BuildNode
            if (leaf != null) {
                node = LeafBuildNode(leaf)
            } else {
                val decision = findSplittingDecision(currDps.keys)
                val ifTrue = currDps.asSequence().filter { decision.datapointCanBeTrue(it.key) }.map { it.key to (it.value || decision.datapointCanBeFalse(it.key)) }.toMap()
                val ifFalse = currDps.asSequence().filter { decision.datapointCanBeFalse(it.key) }.map { it.key to (it.value || decision.datapointCanBeTrue(it.key)) }.toMap()
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

    private fun tryToLabel(datapoints: Map<Datapoint, Boolean>): DecisionTree.Leaf? {
        if (datapoints.keys.all { it in constraintSystem.universallyTrue }) {
            return DecisionTree.Leaf(true)
        }
        if (datapoints.keys.all { it in constraintSystem.universallyFalse }) {
            return DecisionTree.Leaf(false)
        }
        val universalDps = datapoints.filterValues { it }.keys
        val existentialDps = datapoints.filterValues { !it }.keys
        val couldBeLabeledTrue =
                universalDps.none { it in constraintSystem.existentiallyFalse }
                        && existentialDps.none { it in constraintSystem.universallyFalse }
        if (couldBeLabeledTrue) {
            constraintSystem.tryToSetDatapointsTrue(universalDps, existentialDps)?.let {
                constraintSystem = it
                return DecisionTree.Leaf(true)
            }
        }
        val couldBeLabeledFalse =
                universalDps.none { it in constraintSystem.existentiallyTrue }
                        && existentialDps.none { it in constraintSystem.universallyTrue }
        if (couldBeLabeledFalse) {
            constraintSystem.tryToSetDatapointsFalse(universalDps, existentialDps)?.let {
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

        var bestExpr: Expr<BoolType>? = null
        var bestError: Double? = null

        for ((variable, occurrences) in variableToOccurrences) {
            val (expr, error) = findBestSplitForVariable(variable, occurrences, datapointsToSplit)
            if (bestError == null || error < bestError) {
                bestExpr = expr
                bestError = error
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
            occurrences: Iterable<VariableOccurrence>,
            datapointsToSplit: Set<Datapoint>
    ): Pair<Expr<BoolType>, Double> {
        //In splittableDps, every datapoint occurs at most once, because it assigns at most one value to a variable
        val splittableDps = occurrences.map { it.datapoint }
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


        val occurrencesByLit = occurrences.groupBy { it.value }

        val orderedLiterals = occurrencesByLit.entries.asSequence().sortedBy { it.key.value }

        for ((lit, currentOccurrences) in orderedLiterals) {
            matchingTotal += currentOccurrences.size
            nonMatchingTotal -= currentOccurrences.size

            val currentTrue = currentOccurrences.count { it.datapoint in constraintSystem.universallyTrue }
            val currentFalse = currentOccurrences.count { it.datapoint in constraintSystem.universallyFalse }

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
        if (bestError == null || bestExpr == null) {
            // Expr and error, for which none of the occurrences match
            val smallestLit: IntLitExpr = occurrences.firstOrNull()?.value ?: IntLitExpr.of(0)
            val defExpr = Lt(variable.ref, smallestLit)
            val defError =
                    classificationError(unsplittableTrue, unsplittableFalse, unsplittableDps.size) +
                            classificationError(unsplittableTrue + splittableTrue,
                                    unsplittableFalse + splittableFalse,
                                    datapointsToSplit.size)
            return defExpr to defError
        }
        return bestExpr to bestError
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