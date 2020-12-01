package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.CNFCandidates
import hu.bme.mit.theta.cfa.analysis.chc.DEBUG
import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.*
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.LeqPattern
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.PredicatePattern
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import java.util.*

class DecisionTreeLearner(override val name: String,
                          private val measure: ImpurityMeasure = ClassificationError,
                          private val predicatePatterns: Collection<PredicatePattern> = listOf(LeqPattern)) : Learner {

    override fun suggestCandidates(constraintSystem: ConstraintSystem): InvariantCandidates {
        val invariantMap = DecisionTreeBuilder(constraintSystem).buildTree().candidates
        return CNFCandidates(name, invariantMap.default, invariantMap.candidateMap)
    }


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
                tryToExecuteLabeling(wholeDatapoints, splitDatapoints, true)?.let {
                    return it
                }
            }
            if (wholeDatapoints.none { constraintSystem.forcedTrue.contains(it) } && splitDatapoints.none { constraintSystem.forcedTrue.contains(it) }) {
                tryToExecuteLabeling(wholeDatapoints, splitDatapoints, false)?.let {
                    return it
                }
            }
            return null
        }

        private fun tryToExecuteLabeling(wholeDatapoints: Set<Datapoint>, splitDatapoints: Set<Datapoint>, label: Boolean): DecisionTree.Leaf? {
            val allNewDatapoints = mutableListOf<Pair<Datapoint, Boolean?>>()
            try {
                val builder = constraintSystem.builder()
                when (label) {
                    true -> builder.labelDatapointsTrue(wholeDatapoints)
                    false -> builder.labelDatapointsFalse(wholeDatapoints)
                }
                do {
                    val newDatapoints = builder.getAndResetNewDatapoints().map { it to classifyNewDatapoint(it) }
                    allNewDatapoints.addAll(newDatapoints)
                    builder.labelDatapointsTrue(newDatapoints.filter { it.second == true }.map { it.first })
                    builder.labelDatapointsFalse(newDatapoints.filter { it.second == false }.map { it.first })
                } while (newDatapoints.isNotEmpty())
                assert(allNewDatapoints.toSet().size == allNewDatapoints.size)
                val newCS = builder.build()
                when (label) {
                    true -> if (splitDatapoints.any { it in newCS.forcedFalse }) return null
                    false -> if (splitDatapoints.any { it in newCS.forcedTrue }) return null
                }
                constraintSystem = newCS
                return DecisionTree.Leaf(label)
            } catch (e: ContradictoryException) {
                val builder = constraintSystem.builder()
                builder.addDatapoints(allNewDatapoints.map { it.first })
                builder.getAndResetNewDatapoints()
                var newDatapoints: List<Pair<Datapoint, Boolean?>> = allNewDatapoints
                while (newDatapoints.isNotEmpty()) {
                    builder.labelDatapointsTrue(newDatapoints.filter { it.second == true }.map { it.first })
                    builder.labelDatapointsFalse(newDatapoints.filter { it.second == false }.map { it.first })
                    newDatapoints = builder.getAndResetNewDatapoints().map { it to classifyNewDatapoint(it) }
                }
                constraintSystem = builder.build()
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

            var bestExpr: Expr<BoolType>? = null
            var bestError: Double? = null

            for (pattern in predicatePatterns) {
                val split = pattern.findBestSplit(datapointsToSplit, constraintSystem, measure)
                if (split != null) {
                    val (expr, error) = split
                    if (bestError == null || error < bestError) {
                        bestExpr = expr
                        bestError = error
                    }
                }
            }

            if (bestExpr != null && bestError != null) {
                return ExprDecision(bestExpr)
            } else {
                error("No viable split was found")
            }
        }

        private fun classifyNewDatapoint(dp: Datapoint): Boolean? = root.classifyNewDatapoint(dp, false)
    }


    private interface BuildNode {
        val built: DecisionTree.Node?
        fun classifyNewDatapoint(datapoint: Datapoint, wasSplit: Boolean): Boolean?
    }

    private class BranchBuildNode(val pivot: Decision, var trueChild: BuildNode, var falseChild: BuildNode) : BuildNode {
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

    private data class SetToProcess(

            val wholeDatapoints: MutableSet<Datapoint>,
            val splitDatapoints: MutableSet<Datapoint>, var parentSlot: ParentSlot?) : BuildNode {
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


    private class LeafBuildNode(override val built: DecisionTree.Leaf) : BuildNode {
        override fun classifyNewDatapoint(datapoint: Datapoint, wasSplit: Boolean): Boolean? = built.label
    }
}