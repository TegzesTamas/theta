package hu.bme.mit.theta.cfa.analysis.chc.constraint

import hu.bme.mit.theta.cfa.analysis.chc.DEBUG
import hu.bme.mit.theta.cfa.analysis.chc.DummyCHC


class ConstraintSystem private constructor(
        val constraints: List<Constraint>,
        val subsets: Map<Datapoint, List<Datapoint>>,
        private val positiveDeductions: Map<Datapoint, DeductionTrace>,
        val forcedFalse: Set<Datapoint>
) {
    val forcedTrue: Set<Datapoint>
        get() = positiveDeductions.keys
    val datapoints: Set<Datapoint>
        get() = subsets.keys

    private data class DeductionTrace(val constraint: Constraint, val usedSource: Datapoint?)
    class Builder {
        private var newDatapoints: MutableSet<Datapoint> = mutableSetOf()

        private val constraints: MutableList<Constraint>
        private val subsets: MutableMap<Datapoint, MutableList<Datapoint>>

        private val positiveDeductions: MutableMap<Datapoint, DeductionTrace>
        private val forcedFalse: MutableSet<Datapoint>

        constructor() {
            constraints = mutableListOf()
            subsets = mutableMapOf()
            positiveDeductions = mutableMapOf()
            forcedFalse = mutableSetOf()
        }

        constructor(c: ConstraintSystem) {
            constraints = c.constraints.toMutableList()
            subsets = c.subsets.asSequence().map { it.key to it.value.toMutableList() }.toMap(mutableMapOf())
            positiveDeductions = c.positiveDeductions.toMutableMap()
            forcedFalse = c.forcedFalse.toMutableSet()
        }

        fun getAndResetNewDatapoints(): Set<Datapoint> {
            val ret = newDatapoints
            newDatapoints = mutableSetOf()
            return ret
        }

        @Throws(ContradictoryException::class)
        fun addConstraint(constraint: Constraint): Builder {
            constraints.add(constraint)
            classifyNewDatapoints(listOf(constraint.source, constraint.target))
            makeDeductions()
            return this
        }

        @Throws(ContradictoryException::class)
        fun labelDatapointsTrue(dps: Collection<Datapoint>): Builder {
            require(subsets.keys.containsAll(dps))
            if (dps.any { forcedFalse.contains(it) }) {
                throw ContradictoryException(emptyList(), "Cannot label datapoint true, because it is already forced false")
            }

            for (dp in dps) {
                if (!positiveDeductions.containsKey(dp))
                    addConstraint(Constraint(null, dp, DummyCHC.unchanging))
            }
            makeDeductions()
            return this
        }

        @Throws(ContradictoryException::class)
        fun labelDatapointsFalse(dps: Collection<Datapoint>): Builder {
            require(subsets.keys.containsAll(dps))
            if (dps.any { positiveDeductions.containsKey(it) }) {
                throw ContradictoryException(emptyList(), "Cannot label datapoint false because it is already forced true")
            }

            for (dp in dps) {
                if (!forcedFalse.contains(dp))
                    addConstraint(Constraint(dp, null, DummyCHC.unchanging))
            }
            makeDeductions()
            return this
        }

        fun addDatapoints(datapoints : Iterable<Datapoint>) : Builder{
            classifyNewDatapoints(datapoints)
            return this
        }

        @Throws(ContradictoryException::class)
        fun build(): ConstraintSystem {
            makeDeductions()
            return ConstraintSystem(
                    constraints.toList(),
                    subsets.toMap(),
                    positiveDeductions.toMap(),
                    forcedFalse.toSet()
            )
        }

        private fun makeDeductions() {
            makePositiveDeductions()
            makeNegativeDeductions()
        }

        @Throws(ContradictoryException::class)
        private fun makePositiveDeductions() {
            do {
                val deductions = constraints.asSequence()
                        .filter { !positiveDeductions.containsKey(it.target) }
                        .flatMap { constraint ->
                            if (constraint.source == null || positiveDeductions.containsKey(constraint.source)) {
                                sequenceOf(constraint.target
                                        ?.let { target -> target to DeductionTrace(constraint, constraint.source) }
                                        ?: throw ContradictoryException(retraceDeductions(DeductionTrace(constraint, constraint.source))))
                            } else {
                                subsets[constraint.source]!!.asSequence()         //Subsets
                                        .filter { subset -> positiveDeductions.containsKey(subset) }     //Forced true subsets
                                        .map { subset ->                           //Target subset to constraint
                                            constraint.deducePositively(subset)
                                                    ?.let { subsetTarget -> subsetTarget to DeductionTrace(constraint, subset) }
                                                    ?: throw ContradictoryException(retraceDeductions(DeductionTrace(constraint, subset)))
                                        }
                            }
                        }
                var deducedSomething = false
                for ((dp, trace) in deductions) {
                    if (!positiveDeductions.containsKey(dp)) {
                        deducedSomething = true
                        if (!subsets.containsKey(dp)) {
                            classifyNewDatapoints(listOf(dp))
                        }
//                        if (forcedFalse.contains(dp)) {
//                            throw ContradictoryException(emptyList(), "Datapoint forced both false and true: $dp") //TODO maybe retrace deductions
//                        }
                        positiveDeductions[dp] = trace
                        subsets[dp]?.forEach { subset ->
//                            if (forcedFalse.contains(subset)) {
//                                throw ContradictoryException(emptyList(), "Subset forced both false and true: $subset") //TODO maybe retrace deductions
//                            }
                            positiveDeductions.putIfAbsent(subset, trace)
                        }
                    }
                }
            } while (deducedSomething)
        }

        private fun makeNegativeDeductions() {
            do {
                val deductions = constraints.asSequence()
                        .filter { !forcedFalse.contains(it.source) }
                        .flatMap { constraint ->
                            if (constraint.target == null || forcedFalse.contains(constraint.target)) {
                                sequenceOf(constraint.source
                                        ?: error("Negative deductions revealed contradiction")
                                )
                            } else {
                                subsets[constraint.target]!!.asSequence()     //Subsets
                                        .filter { subset -> forcedFalse.contains(subset) }   //Forced false subsets
                                        .map { subset ->
                                            constraint.deduceNegatively(subset)
                                                    ?: error("Negative deductions revealed contradiction with subset")
                                        }
                            }
                        }
                var deducedSomething = false
                for (deduction in deductions) {
                    if (!forcedFalse.contains(deduction)) {
                        deducedSomething = true
                        if (!subsets.containsKey(deduction)) {
                            classifyNewDatapoints(listOf(deduction))
                        }
                        if (positiveDeductions.containsKey(deduction)) {
                            error("Negative deduction already forced true")
//                            throw ContradictoryException(emptyList(), "Datapoint forced both true and false: $deduction")
                        }
                        forcedFalse.add(deduction)
                        subsets[deduction]?.firstOrNull { positiveDeductions.containsKey(it) }?.let {
                            error("Subset of negative deduction already forced true")
//                            throw ContradictoryException(emptyList(), "Subset forced both true and false: $it")
                        }
                        forcedFalse.addAll(subsets[deduction] ?: emptyList())
                    }
                }
            } while (deducedSomething)
        }

        private fun classifyNewDatapoints(newDps: Iterable<Datapoint?>) {
            val toProcess = newDps.filterNotNullTo(mutableListOf())
            while (toProcess.isNotEmpty()) {
                val newDp = toProcess.removeAt(toProcess.lastIndex)
                if (!subsets.containsKey(newDp)) {
                    newDatapoints.add(newDp)
                    val newList = mutableListOf<Datapoint>()
                    for ((oldDp, oldList) in subsets.entries) {
                        if (!newDp.disjoint(oldDp)) {
                            when {
                                oldDp.subsetOf(newDp) -> {
                                    newList.add(oldDp)
                                    newList.addAll(oldList)
                                }
                                newDp.subsetOf(oldDp) -> {
                                    oldList.add(newDp)
                                    positiveDeductions[oldDp]?.let {
                                        positiveDeductions[newDp] = it
                                    }
                                    if (forcedFalse.contains(oldDp)) {
                                        forcedFalse.add(newDp)
                                    }
                                }
                                else -> {
                                    val commonSubset = intersection(newDp, oldDp)
                                    toProcess.add(commonSubset)
                                }
                            }
                        }
                        if (DEBUG) {
                            assert(oldList.toSet().size == oldList.size)
                        }
                    }
                    subsets[newDp] = newList
                    if (DEBUG) {
                        assert(newList.toSet().size == newList.size)
                    }
                }
            }
        }

        private fun retraceDeductions(trace: DeductionTrace): List<Constraint> {
            require(trace.constraint.target == null)
            require(trace.constraint.source == null || (trace.usedSource != null && trace.usedSource.subsetOf(trace.constraint.source)))
            var usedSource: Datapoint? = trace.usedSource
            val reasons = mutableListOf(trace.constraint)
            while (usedSource != null) {
                val reason = positiveDeductions[usedSource] ?: error("Unreasoned deduction")
                assert(reason.constraint.source == null || (reason.usedSource != null && reason.usedSource.subsetOf(reason.constraint.source)))
                reasons.add(reason.constraint)
                usedSource = reason.usedSource
            }
            reasons.reverse()
            return reasons
        }
    }
}

class ContradictoryException(val contradictorySubset: List<Constraint>, s: String? = null) : Throwable(s)