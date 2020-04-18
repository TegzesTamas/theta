package hu.bme.mit.theta.cfa.analysis.chc.learner.constraint

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.core.model.MutableValuation


class ConstraintSystem private constructor(
        val constraints: List<Constraint>,
        val datapoints: Set<Datapoint>,
        var ambiguousDatapoints: Set<Datapoint>,
        val existentiallyTrue: Map<Datapoint, Constraint>,
        val universallyTrue: Map<Datapoint, Constraint>,
        val existentiallyFalse: Set<Datapoint>,
        val universallyFalse: Set<Datapoint>
) {
    class Builder {
        private val constraints: MutableList<Constraint>
        private val datapoints: MutableSet<Datapoint>

        private var ambiguousDatapoints: MutableSet<Datapoint>

        private val existentiallyTrue: MutableMap<Datapoint, Constraint>
        private val universallyTrue: MutableMap<Datapoint, Constraint>
        private val existentiallyFalse: MutableSet<Datapoint>
        private val universallyFalse: MutableSet<Datapoint>

        constructor() {
            constraints = mutableListOf()
            datapoints = mutableSetOf()
            ambiguousDatapoints = mutableSetOf()
            existentiallyTrue = mutableMapOf()
            universallyTrue = mutableMapOf()
            existentiallyFalse = mutableSetOf()
            universallyFalse = mutableSetOf()
        }

        constructor(c: ConstraintSystem) {
            constraints = c.constraints.toMutableList()
            datapoints = c.datapoints.toMutableSet()
            ambiguousDatapoints = c.ambiguousDatapoints.toMutableSet()
            existentiallyTrue = c.existentiallyTrue.toMutableMap()
            universallyTrue = c.universallyTrue.toMutableMap()
            existentiallyFalse = c.existentiallyFalse.toMutableSet()
            universallyFalse = c.universallyFalse.toMutableSet()

        }

        @Throws(ContradictoryException::class)
        fun addConstraint(constraint: Constraint): Builder {
            constraints.add(constraint)
            constraint.source?.let { source ->
                datapoints += source
                classifyNewDatapoint(source)
            }
            constraint.target?.let { target ->
                datapoints += target
                classifyNewDatapoint(target)
            }
            makePositiveDeductions()
            return this
        }

        @Throws(ContradictoryException::class)
        fun setDatapointsTrue(universalDps: Collection<Datapoint>, existentialDps: Collection<Datapoint>): Builder {
            require(datapoints.containsAll(universalDps))
            require(datapoints.containsAll(existentialDps))
            if (existentialDps.any { it in universallyFalse } || universalDps.any { it in existentiallyFalse }) {
                throw ContradictoryException(emptyList(), "Cannot set datapoint true, because it is already forced false")
            }
            val dummyDp = Datapoint(Invariant("setDatapointsTrue"), MutableValuation())
            val dummyConstraint = Constraint(null, dummyDp)
            universallyTrue[dummyDp] = dummyConstraint
            existentiallyTrue[dummyDp] = dummyConstraint
            existentialDps.asSequence()
                    .map { it to Constraint(dummyDp, it) }
                    .toMap(existentiallyTrue)
            val universalMap = universalDps.asSequence()
                    .map { it to Constraint(dummyDp, it) }
            existentiallyTrue.putAll(universalMap)
            universallyTrue.putAll(universalMap)
            makePositiveDeductions()
            return this
        }

        @Throws(ContradictoryException::class)
        fun setDatapointsFalse(universalDps: Collection<Datapoint>, existentialDps: Collection<Datapoint>): Builder {
            require(datapoints.containsAll(universalDps))
            require(datapoints.containsAll(existentialDps))
            if (existentialDps.any { it in universallyTrue } || universalDps.any { it in existentiallyTrue }) {
                throw ContradictoryException(emptyList(), "Cannot set datapoint false because it is already forced true")
            }
            existentiallyFalse.addAll(existentialDps)
            existentiallyFalse.addAll(universalDps)
            universallyFalse.addAll(universalDps)
            return this
        }

        fun build(): ConstraintSystem {
            makeNegativeDeductions()
            return ConstraintSystem(
                    constraints.toList(),
                    datapoints.toSet(),
                    ambiguousDatapoints.toSet(),
                    existentiallyTrue.toMap(),
                    universallyTrue.toMap(),
                    existentiallyFalse.toSet(),
                    universallyFalse.toSet()
            )
        }

        @Throws(ContradictoryException::class)
        private fun makePositiveDeductions() {
            do {
                val newExistentiallyTrue = constraints.asSequence()
                        .filter { !existentiallyTrue.containsKey(it.target) }
                        .filter { it.source == null || existentiallyTrue.containsKey(it.source) }
                        .map {
                            it.target?.let { target -> target to it }
                                    ?: throw ContradictoryException(retraceDeductions(it))
                        }
                        .toMap()
                val newUniversallyTrue = constraints.asSequence()
                        .filter { !universallyTrue.containsKey(it.target) }
                        .filter { it.source == null || universallyTrue.containsKey(it.source) }
                        .map {
                            it.target?.let { target -> target to it }
                                    ?: throw ContradictoryException(retraceDeductions(it))
                        }
                        .toMap()
                existentiallyTrue.putAll(newExistentiallyTrue)
                universallyTrue.putAll(newUniversallyTrue)
                val stillAmbiguous = mutableSetOf<Datapoint>()
                for (ambiguous in ambiguousDatapoints) {
                    val superSetEntry = newUniversallyTrue.entries
                            .firstOrNull { (dp, _) -> ambiguous.subsetOf(dp) }
                    if (superSetEntry != null) {
                        universallyTrue[ambiguous] = superSetEntry.value
                        existentiallyTrue[ambiguous] = superSetEntry.value
                    } else {
                        stillAmbiguous.add(ambiguous)
                        val nonDisjointEntry = newUniversallyTrue.entries
                                .firstOrNull { (dp, _) -> !dp.disjoint(ambiguous) }
                        if (nonDisjointEntry != null) {
                            existentiallyTrue[ambiguous] = nonDisjointEntry.value
                        } else {
                            val subsetEntry = newExistentiallyTrue.entries
                                    .firstOrNull { (dp, _) -> dp.subsetOf(ambiguous) }
                            if (subsetEntry != null) {
                                existentiallyTrue[ambiguous] = subsetEntry.value
                            }
                        }
                    }
                }
                ambiguousDatapoints = stillAmbiguous
                constraints.removeAll { universallyTrue.containsKey(it.target) }
            } while (newExistentiallyTrue.isNotEmpty())
        }

        private fun makeNegativeDeductions() {
            do {
                val newExistentiallyFalse = constraints.asSequence()
                        .filter { !existentiallyFalse.contains(it.source) }
                        .filter { it.target == null || it.target in existentiallyFalse }
                        .map { it.source ?: error("Negative deductions revealed inconsistency.") }
                        .toSet()
                val newUniversallyFalse = constraints.asSequence()
                        .filter { !universallyFalse.contains(it.source) }
                        .filter { it.target == null || it.target in universallyFalse }
                        .map { it.source ?: error("Negative deductions revealed inconsistency.") }
                        .toSet()
                val stillAmbiguous = mutableSetOf<Datapoint>()
                for (ambiguous in ambiguousDatapoints) {
                    if (newUniversallyFalse.any { ambiguous.subsetOf(it) }) {
                        universallyFalse.add(ambiguous)
                        existentiallyFalse.add(ambiguous)
                    } else {
                        stillAmbiguous.add(ambiguous)
                        if (newUniversallyFalse.any { !ambiguous.disjoint(it) }) {
                            existentiallyFalse.add(ambiguous)
                        } else {
                            if (newExistentiallyFalse.any { it.subsetOf(ambiguous) }) {
                                existentiallyFalse.add(ambiguous)
                            }
                        }
                    }
                }
            } while (newExistentiallyFalse.isNotEmpty())
        }

        private fun retraceDeductions(constraint: Constraint): List<Constraint> {
            val questions = mutableListOf(constraint)
            val reasons = mutableListOf<Constraint>()
            while (questions.isNotEmpty()) {
                val question = questions.removeAt(questions.lastIndex)
                question.source?.let { source ->
                    questions += existentiallyTrue[source] ?: error("Unreasoned deduction")
                }
                reasons.add(question)
            }
            reasons.reverse()
            return reasons
        }

        private fun classifyNewDatapoint(newDp: Datapoint) {
            val trueSuperSetEntry = universallyTrue
                    .entries
                    .firstOrNull { (trueDp, _) -> newDp.subsetOf(trueDp) }
            if (trueSuperSetEntry != null) {
                universallyTrue[newDp] = trueSuperSetEntry.value
                existentiallyTrue[newDp] = trueSuperSetEntry.value

            } else {
                val falseSuperSet = universallyFalse
                        .firstOrNull { falseDp -> newDp.subsetOf(falseDp) }
                if (falseSuperSet != null) {
                    universallyFalse.add(newDp)
                    existentiallyFalse.add(newDp)
                } else {
                    ambiguousDatapoints.add(newDp)
                    val trueSubsetEntry = existentiallyTrue
                            .entries
                            .firstOrNull { (trueDp, _) -> trueDp.subsetOf(newDp) }
                    if (trueSubsetEntry != null) {
                        existentiallyTrue[newDp] = trueSubsetEntry.value
                    } else {
                        val trueNonDisjointEntry = universallyTrue
                                .entries
                                .firstOrNull { (trueDp, _) -> !trueDp.disjoint(newDp) }
                        if (trueNonDisjointEntry != null) {
                            existentiallyTrue[newDp] = trueNonDisjointEntry.value
                        }
                    }
                    val falseSubset = existentiallyFalse
                            .firstOrNull { falseDp -> falseDp.subsetOf(newDp) }
                    if (falseSubset != null) {
                        existentiallyFalse.add(newDp)
                    } else {
                        val falseNonDisjointEntry = universallyFalse
                                .firstOrNull { falseDp -> !falseDp.disjoint(newDp) }
                        if (falseNonDisjointEntry != null) {
                            existentiallyFalse.add(newDp)
                        }
                    }
                }
            }
        }
    }
}

fun ConstraintSystem.tryToSetDatapointsTrue(universalDps: Collection<Datapoint>, existentialDps: Collection<Datapoint>): ConstraintSystem? {
    val builder = ConstraintSystem.Builder(this)
    return try {
        builder.setDatapointsTrue(universalDps, existentialDps)
        builder.build()
    } catch (e: ContradictoryException) {
        null
    }
}

fun ConstraintSystem.tryToSetDatapointsFalse(universalDps: Collection<Datapoint>, existentialDps: Collection<Datapoint>): ConstraintSystem? {
    val builder = ConstraintSystem.Builder(this)
    return try {
        builder.setDatapointsFalse(universalDps, existentialDps)
        builder.build()
    } catch (e: ContradictoryException) {
        null
    }
}

class ContradictoryException(val contradictorySubset: List<Constraint>, s: String? = null) : Throwable(s)