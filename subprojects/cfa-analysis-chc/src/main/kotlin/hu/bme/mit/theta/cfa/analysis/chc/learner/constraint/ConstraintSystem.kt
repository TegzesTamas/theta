package hu.bme.mit.theta.cfa.analysis.chc.learner.constraint

import hu.bme.mit.theta.cfa.analysis.chc.DummyCHC


class ConstraintSystem private constructor(
        val constraints: List<Constraint>,
        val datapoints: Map<Datapoint, List<Datapoint>>,
        val forcedTrue: Map<Datapoint, Constraint>,
        val forcedFalse: Set<Datapoint>
) {
    class Builder {
        private val constraints: MutableList<Constraint>
        private val datapoints: MutableMap<Datapoint, MutableList<Datapoint>>


        private val forcedTrue: MutableMap<Datapoint, Constraint>
        private val forcedFalse: MutableSet<Datapoint>

        constructor() {
            constraints = mutableListOf()
            datapoints = mutableMapOf()
            forcedTrue = mutableMapOf()
            forcedFalse = mutableSetOf()
        }

        constructor(c: ConstraintSystem) {
            constraints = c.constraints.toMutableList()
            datapoints = c.datapoints.asSequence().map { it.key to it.value.toMutableList() }.toMap(mutableMapOf())
            forcedTrue = c.forcedTrue.toMutableMap()
            forcedFalse = c.forcedFalse.toMutableSet()
        }

        fun addConstraint(constraint: Constraint): Builder {
            constraints.add(constraint)
            classifyNewDatapoints(listOf(constraint.source, constraint.target))
            return this
        }

        @Throws(ContradictoryException::class)
        fun setDatapointsTrue(dps: Collection<Datapoint>): Builder {
            require(datapoints.keys.containsAll(dps))
            if (dps.any { forcedFalse.contains(it) }) {
                throw ContradictoryException(emptyList(), "Cannot set datapoint true, because it is already forced false")
            }

            for (dp in dps) {
                if (!forcedTrue.containsKey(dp))
                    addConstraint(Constraint(null, dp, DummyCHC))
            }
            return this
        }

        @Throws(ContradictoryException::class)
        fun setDatapointsFalse(dps: Collection<Datapoint>): Builder {
            require(datapoints.keys.containsAll(dps))
            if (dps.any { forcedTrue.containsKey(it) }) {
                throw ContradictoryException(emptyList(), "Cannot set datapoint false because it is already forced true")
            }

            for (dp in dps) {
                if (!forcedFalse.contains(dp))
                    addConstraint(Constraint(dp, null, DummyCHC))
            }

            return this
        }

        @Throws(ContradictoryException::class)
        fun build(): ConstraintSystem {
            makePositiveDeductions()
            makeNegativeDeductions()
            return ConstraintSystem(
                    constraints.toList(),
                    datapoints.toMap(),
                    forcedTrue.toMap(),
                    forcedFalse.toSet()
            )
        }

        @Throws(ContradictoryException::class)
        private fun makePositiveDeductions() {
            do {
                val deductions = constraints.asSequence()
                        .filter { !forcedTrue.containsKey(it.target) }
                        .flatMap { constraint ->
                            if (constraint.source == null || forcedTrue.containsKey(constraint.source)) {
                                sequenceOf(constraint.target
                                        ?.let { target -> target to constraint }
                                        ?: throw ContradictoryException(retraceDeductions(constraint)))
                            } else {
                                datapoints[constraint.source]?.asSequence()         //Subsets
                                        ?.filter { forcedTrue.containsKey(it) }     //Forced true subsets
                                        ?.map { subset ->                           //Target subset to constraint
                                            constraint.deducePositively(subset)
                                                    ?.let { subsetTarget -> subsetTarget to constraint }
                                                    ?: throw ContradictoryException(retraceDeductions(constraint))
                                        }
                                        ?: emptySequence()
                            }
                        }
                var deducedSomething = false
                for ((dp, cause) in deductions) {
                    if (!forcedTrue.containsKey(dp)) {
                        if (forcedFalse.contains(dp)) {
                            throw ContradictoryException(emptyList(), "Datapoint forced both false and true: $dp")
                        }
                        deducedSomething = true
                        forcedTrue[dp] = cause
                        datapoints[dp]?.forEach { subset ->
                            if (forcedFalse.contains(subset)) {
                                throw ContradictoryException(emptyList(), "Subset forced both false and true: $subset")
                            }
                            forcedTrue.putIfAbsent(subset, cause)
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
                                datapoints[constraint.target]?.asSequence()     //Subsets
                                        ?.filter { forcedFalse.contains(it) }   //Forced false subsets
                                        ?.map { subset ->
                                            constraint.deduceNegatively(subset)
                                                    ?: error("Negative deductions revealed contradiction with subset")
                                        }
                                        ?: emptySequence()
                            }
                        }
                var deducedSomething = false
                for (deduction in deductions) {
                    if (!forcedFalse.contains(deduction)) {
                        if (forcedTrue.containsKey(deduction)) {
                            error("Negative deduction already forced true")
//                            throw ContradictoryException(emptyList(), "Datapoint forced both true and false: $deduction")
                        }
                        deducedSomething = true
                        forcedFalse.add(deduction)
                        datapoints[deduction]?.firstOrNull { forcedTrue.containsKey(it) }?.let {
                            error("Subset of negative deduction already forced true")
//                            throw ContradictoryException(emptyList(), "Subset forced both true and false: $it")
                        }
                        forcedFalse.addAll(datapoints[deduction] ?: emptyList())
                    }
                }
            } while (deducedSomething)
        }

        private fun classifyNewDatapoints(newDps: List<Datapoint?>) {
            val toProcess = mutableListOf<Datapoint>()
            newDps.filterNotNullTo(toProcess)
            while (toProcess.isNotEmpty()) {
                val newDp = toProcess.removeAt(0)
                if (datapoints.containsKey(newDp)) {
                    return
                }
                val newList = mutableListOf<Datapoint>()
                datapoints[newDp] = newList
                for ((oldDp, oldList) in datapoints.entries) {
                    if (!newDp.disjoint(oldDp)) {
                        when {
                            oldDp.subsetOf(newDp) -> {
                                newList.add(oldDp)
                                newList.addAll(oldList)
                            }
                            newDp.subsetOf(oldDp) -> {
                                oldList.add(newDp)
                                forcedTrue[oldDp]?.let {
                                    forcedTrue[newDp] = it
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
                }
            }
        }

        private fun retraceDeductions(constraint: Constraint): List<Constraint> {
            var question: Datapoint? = constraint.source
            val reasons = mutableListOf<Constraint>(constraint)
            while (question != null) {
                val reason: Constraint = forcedTrue[question] ?: error("Unreasoned deduction")
                reasons.add(reason)
                question = reason.source
            }
            reasons.reverse()
            return reasons
        }
    }
}

fun ConstraintSystem.tryToSetDatapointsTrue(dps: Collection<Datapoint>): ConstraintSystem? {
    val builder = ConstraintSystem.Builder(this)
    return try {
        builder.setDatapointsTrue(dps)
        builder.build()
    } catch (e: ContradictoryException) {
        null
    }
}

fun ConstraintSystem.tryToSetDatapointsFalse(dps: Collection<Datapoint>): ConstraintSystem? {
    val builder = ConstraintSystem.Builder(this)
    return try {
        builder.setDatapointsFalse(dps)
        builder.build()
    } catch (e: ContradictoryException) {
        null
    }
}

class ContradictoryException(val contradictorySubset: List<Constraint>, s: String? = null) : Throwable(s)