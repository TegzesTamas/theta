package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.DEBUG
import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Datapoint
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
import java.util.*
import kotlin.math.min

class DecisionTreeLearner(private val atoms: Set<Expr<BoolType>> = emptySet()) : Learner {

    override fun suggestCandidates(constraintSystem: ConstraintSystem): InvariantCandidates = DecisionTreeBuilder(constraintSystem).buildTree().candidates


    inner class DecisionTreeBuilder(private var constraintSystem: ConstraintSystem) {
        private lateinit var root: BuildNode
        fun buildTree(): DecisionTree {
            val toProcess = LinkedList(Collections.singleton(SetToProcess(constraintSystem.datapoints.toMutableSet(), mutableSetOf(), null)))
            do {
                val (wholeDatapoints, splitDatapoints, parentSlot) = toProcess.removeFirst()

                val leaf = tryToLabel(wholeDatapoints, splitDatapoints)

                val node: BuildNode
                if (leaf != null) {
                    node = LeafBuildNode(leaf)
                } else {
                    val decision = findSplittingDecision(wholeDatapoints + splitDatapoints)
                    val trueSideWhole = wholeDatapoints
                            .filterTo(mutableSetOf()) { decision.datapointCanBeTrue(it) && !decision.datapointCanBeFalse(it) }
                    val falseSideWhole = wholeDatapoints
                            .filterTo(mutableSetOf()) { !decision.datapointCanBeTrue(it) && decision.datapointCanBeFalse(it) }
                    val bothSides: Set<Datapoint> = wholeDatapoints
                            .filterTo(mutableSetOf()) { decision.datapointCanBeTrue(it) && decision.datapointCanBeFalse(it) }
                    val trueSideSplit = splitDatapoints
                            .filterTo(mutableSetOf()) { decision.datapointCanBeTrue(it) }
                            .apply { addAll(bothSides) }
                    val falseSideSplit = splitDatapoints
                            .filterTo(mutableSetOf()) { decision.datapointCanBeFalse(it) }
                            .apply { addAll(bothSides) }

                    val trueProcess = SetToProcess(trueSideWhole, trueSideSplit, null)
                    val falseProcess = SetToProcess(falseSideWhole, falseSideSplit, null)

                    node = BranchBuildNode(decision, trueProcess, falseProcess)
                    trueProcess.parentSlot = SetToProcess.ParentSlot(node, true)
                    falseProcess.parentSlot = SetToProcess.ParentSlot(node, false)
                    if (DEBUG) {
                        if ((trueSideSplit == splitDatapoints && trueSideWhole == wholeDatapoints) || (falseSideSplit == splitDatapoints && falseSideWhole == wholeDatapoints)) {
                            error("Tried to split datapoints, but one of the new nodes have the same datapoints as before")
                        }
                    }
                    toProcess += trueProcess
                    toProcess += falseProcess
                }
                if (parentSlot == null) {
                    root = node
                } else {
                    val (parent, side) = parentSlot
                    when (side) {
                        true -> parent.trueChild = node
                        false -> parent.falseChild = node
                    }
                }
            } while (toProcess.isNotEmpty())
            return DecisionTree(root.built!!)
        }

        private fun tryToLabel(wholeDatapoints: Set<Datapoint>, splitDatapoints: Set<Datapoint>): DecisionTree.Leaf? {
            if (wholeDatapoints.all { constraintSystem.forcedTrue.contains(it) } && splitDatapoints.all { constraintSystem.forcedTrue.contains(it) }) {
                return DecisionTree.Leaf(true)
            }
            if (wholeDatapoints.all { constraintSystem.forcedFalse.contains(it) } && splitDatapoints.all { constraintSystem.forcedFalse.contains(it) }) {
                return DecisionTree.Leaf(false)
            }

            if (wholeDatapoints.none { constraintSystem.forcedFalse.contains(it) } && splitDatapoints.none { constraintSystem.forcedFalse.contains(it) }) {
                tryToExecuteLabeling(wholeDatapoints, true)?.let {
                    return it
                }
            }
            if (wholeDatapoints.none { constraintSystem.forcedTrue.contains(it) } && splitDatapoints.none { constraintSystem.forcedTrue.contains(it) }) {
                tryToExecuteLabeling(wholeDatapoints, false)?.let {
                    return it
                }
            }
            return null
        }

        private fun tryToExecuteLabeling(wholeDatapoints: Set<Datapoint>, label: Boolean): DecisionTree.Leaf? {
            val allNewDatapoints = mutableListOf<Pair<Datapoint, Boolean?>>()
            try {
                val builder = constraintSystem.builder()
                when (label) {
                    true -> builder.labelDatapointsTrue(wholeDatapoints)
                    false -> builder.labelDatapointsFalse(wholeDatapoints)

                }
                do {
                    val newDatapoints = builder.getAndResetNewDatapoints().map { it to root.classifyNewDatapoint(it, false) }
                    builder.labelDatapointsTrue(newDatapoints.filter { it.second == true }.map { it.first })
                    builder.labelDatapointsFalse(newDatapoints.filter { it.second == false }.map { it.first })
                    allNewDatapoints.addAll(newDatapoints)
                } while (newDatapoints.isNotEmpty())
                assert(allNewDatapoints.toSet().size == allNewDatapoints.size)
                constraintSystem = builder.build()
                return DecisionTree.Leaf(label)
            } catch (e: ContradictoryException) {
                val builder = constraintSystem.builder()
                builder.addDatapoints(allNewDatapoints.map { it.first })
                builder.getAndResetNewDatapoints()
                var newDatapoints: List<Pair<Datapoint, Boolean?>> = allNewDatapoints
                while (newDatapoints.isNotEmpty()) {
                    builder.labelDatapointsTrue(newDatapoints.filter { it.second == true }.map { it.first })
                    builder.labelDatapointsFalse(newDatapoints.filter { it.second == false }.map { it.first })
                    newDatapoints = builder.getAndResetNewDatapoints().map { it to root.classifyNewDatapoint(it, false) }
                }
                return null
            }

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

            for (atom in atoms) {
                val decision = ExprDecision(atom)
                val trueDatapoints = datapointsToSplit.filterTo(mutableSetOf()) { decision.datapointCanBeTrue(it) }
                val falseDatapoints = datapointsToSplit.filterTo(mutableSetOf()) { decision.datapointCanBeFalse(it) }
                val trueError = classificationError(
                        trueDatapoints.count { constraintSystem.forcedTrue.contains(it) },
                        trueDatapoints.count { constraintSystem.forcedFalse.contains(it) },
                        trueDatapoints.count()
                )
                val falseError = classificationError(
                        falseDatapoints.count { constraintSystem.forcedTrue.contains(it) },
                        falseDatapoints.count { constraintSystem.forcedFalse.contains(it) },
                        falseDatapoints.count()
                )
                val newError = trueError + falseError
                if (bestError == null || newError < bestError) {
                    bestError = newError
                    bestExpr = atom
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

    }


    private data class VariableOccurrence(val variable: Decl<IntType>, val lit: IntLitExpr, val datapoint: Datapoint)
    private data class ForcedLabeling(val mustBeTrue: Int, val mustBeFalse: Int)
    private interface BuildNode {
        val built: DecisionTree.Node?
        fun classifyNewDatapoint(datapoint: Datapoint, wasSplit: Boolean): Boolean?
    }

    private data class BranchBuildNode(val pivot: Decision, var trueChild: BuildNode, var falseChild: BuildNode) : BuildNode {
        override val built: DecisionTree.Branch?
            get() {
                return DecisionTree.Branch(pivot, trueChild.built ?: return null, falseChild.built ?: return null)
            }

        override fun classifyNewDatapoint(datapoint: Datapoint, wasSplit: Boolean): Boolean? {
            val datapointCanBeTrue = pivot.datapointCanBeTrue(datapoint)
            val datapointCanBeFalse = pivot.datapointCanBeFalse(datapoint)
            return when {
                datapointCanBeTrue && datapointCanBeFalse -> {
                    val aLabel = trueChild.classifyNewDatapoint(datapoint, true)
                    val bLabel = falseChild.classifyNewDatapoint(datapoint, true)
                    if (aLabel == bLabel) {
                        aLabel
                    } else {
                        null
                    }
                }
                datapointCanBeTrue -> trueChild.classifyNewDatapoint(datapoint, wasSplit)
                datapointCanBeFalse -> falseChild.classifyNewDatapoint(datapoint, wasSplit)
                else -> error("Datapoint neither true, nor false")
            }
        }
    }

    private data class SetToProcess(val wholeDatapoints: MutableSet<Datapoint>, val splitDatapoints: MutableSet<Datapoint>, var parentSlot: ParentSlot?) : BuildNode {
        data class ParentSlot(val node: BranchBuildNode, val side: Boolean)

        override val built: DecisionTree.Node?
            get() = null

        override fun classifyNewDatapoint(datapoint: Datapoint, wasSplit: Boolean): Boolean? {
            if (wasSplit) {
                splitDatapoints.add(datapoint)
            } else {
                wholeDatapoints.add(datapoint)
            }
            return null
        }
    }


    private data class LeafBuildNode(override val built: DecisionTree.Leaf) : BuildNode {
        override fun classifyNewDatapoint(datapoint: Datapoint, wasSplit: Boolean): Boolean? = built.label
    }
}