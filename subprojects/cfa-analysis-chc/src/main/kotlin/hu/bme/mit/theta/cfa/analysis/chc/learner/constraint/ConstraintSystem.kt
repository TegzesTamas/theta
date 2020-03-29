package hu.bme.mit.theta.cfa.analysis.chc.learner.constraint

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.core.model.MutableValuation


class ConstraintSystem private constructor(
        val constraints: List<Constraint>,
        val datapoints: Set<Datapoint>,
        var ambiguousDatapoints: Set<Datapoint>,
        val existentiallyTrue: Map<Datapoint?, Constraint>,
        val universallyTrue: Map<Datapoint?, Constraint>,
        val existentiallyFalse: Set<Datapoint?>,
        val universallyFalse: Set<Datapoint?>
) {
    class Builder {
        private val constraints: MutableList<Constraint>
        private val datapoints: MutableSet<Datapoint>

        private var ambiguousDatapoints: MutableSet<Datapoint>

        private val existentiallyTrue: MutableMap<Datapoint?, Constraint>
        private val universallyTrue: MutableMap<Datapoint?, Constraint>
        private val existentiallyFalse: MutableSet<Datapoint?>
        private val universallyFalse: MutableSet<Datapoint?>

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
            datapoints.addAll(constraint.source)
            for (newDp in constraint.source) {
                classifyNewDatapoint(newDp)
            }
            constraint.target?.let { target ->
                datapoints += target
                classifyNewDatapoint(constraint.target)
            }
            makePositiveDeductions()
            return this
        }

        @Throws(ContradictoryException::class)
        fun setDatapointsTrue(dpsToSetTrue: Collection<Datapoint>): Builder {
            require(datapoints.containsAll(dpsToSetTrue))
            if (dpsToSetTrue.any { it in universallyFalse }) {
                throw ContradictoryException(emptyList(), "Cannot set datapoint true, because it is already forced false")
            }
            val dummyDp = Datapoint(Invariant("setDatapointsTrue"), MutableValuation())
            val dummyConstraint = Constraint(emptyList(), dummyDp)
            universallyTrue[dummyDp] = dummyConstraint
            existentiallyTrue[dummyDp] = dummyConstraint
            val dummySource = listOf(dummyDp)
            dpsToSetTrue.asSequence()
                    .map { it to Constraint(dummySource, it) }
                    .toMap(existentiallyTrue)
            makePositiveDeductions()
            return this
        }

        fun setDatapointsFalse(dpsToSetFalse: Collection<Datapoint>): Builder {
            require(datapoints.containsAll(dpsToSetFalse))
            if (dpsToSetFalse.any { it in universallyTrue }) {
                throw ContradictoryException(emptyList(), "Cannot set datapoint false because it is already forced true")
            }
            existentiallyFalse.addAll(dpsToSetFalse)
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
                val newForcedTrue = constraints.asSequence()
                        .filter { existentiallyTrue.keys.containsAll(it.source) }
                        .map { it.target to it }
                        .toMap()
                newForcedTrue[null]?.let {
                    throw ContradictoryException(retraceDeductions(it))
                }
                existentiallyTrue.putAll(newForcedTrue)
                universallyTrue.putAll(newForcedTrue)
                val stillAmbiguous = mutableSetOf<Datapoint>()
                for (ambiguous in ambiguousDatapoints) {
                    val superSetEntry = newForcedTrue.entries
                            .firstOrNull { (dp, _) -> dp != null && ambiguous.subsetOf(dp) }
                    if (superSetEntry != null) {
                        universallyTrue[ambiguous] = superSetEntry.value
                        existentiallyTrue[ambiguous] = superSetEntry.value
                    } else {
                        stillAmbiguous.add(ambiguous)
                        val nonDisjointEntry = newForcedTrue.entries
                                .firstOrNull { (dp, _) -> dp != null && !dp.disjoint(ambiguous) }
                        if (nonDisjointEntry != null) {
                            existentiallyTrue[ambiguous] = nonDisjointEntry.value
                        }
                    }
                }
                ambiguousDatapoints = stillAmbiguous
                constraints.removeAll { universallyTrue.contains(it.target) }
            } while (newForcedTrue.isNotEmpty())
        }

        private fun makeNegativeDeductions() {
            do {
                val newForcedFalse = constraints.asSequence()
                        .filter { it.target in existentiallyFalse }
                        .flatMap {
                            it.source.singleOrNull { dp -> !universallyFalse.contains(dp) }
                                    ?.let { source -> sequenceOf(source) }
                                    ?: emptySequence()
                        }
                        .toSet()
                val stillAmbiguous = mutableSetOf<Datapoint>()
                for (ambiguous in ambiguousDatapoints) {
                    if (newForcedFalse.any { ambiguous.subsetOf(it) }) {
                        universallyFalse.add(ambiguous)
                        existentiallyFalse.add(ambiguous)
                    } else {
                        stillAmbiguous.add(ambiguous)
                        if (newForcedFalse.any { !ambiguous.disjoint(it) }) {
                            existentiallyFalse.add(ambiguous)
                        }
                    }
                }
            } while (newForcedFalse.isNotEmpty())
        }

        private fun retraceDeductions(constraint: Constraint): List<Constraint> {
            val questions = mutableListOf(constraint)
            val reasons = mutableListOf<Constraint>()
            while (questions.isNotEmpty()) {
                val question = questions.removeAt(questions.lastIndex)
                question.source.mapTo(questions) { dp -> existentiallyTrue[dp] ?: error("Unreasoned deduction") }
                reasons.add(question)
            }
            reasons.reverse()
            return reasons
        }

        private fun classifyNewDatapoint(newDp: Datapoint) {
            val trueSuperSetEntry = universallyTrue
                    .entries
                    .firstOrNull { (trueDp, _) -> trueDp != null && newDp.subsetOf(trueDp) }
            if (trueSuperSetEntry != null) {
                universallyTrue[newDp] = trueSuperSetEntry.value
                existentiallyTrue[newDp] = trueSuperSetEntry.value

            } else {
                val falseSuperSet = universallyFalse
                        .firstOrNull { falseDp -> falseDp != null && newDp.subsetOf(falseDp) }
                if (falseSuperSet != null) {
                    universallyFalse.add(newDp)
                    existentiallyFalse.add(newDp)
                } else {
                    ambiguousDatapoints.add(newDp)
                    val trueSubsetEntry = existentiallyTrue
                            .entries
                            .firstOrNull { (trueDp, _) -> trueDp != null && trueDp.subsetOf(newDp) }
                    if (trueSubsetEntry != null) {
                        existentiallyTrue[newDp] = trueSubsetEntry.value
                    } else {
                        val trueNonDisjointEntry = universallyTrue
                                .entries
                                .firstOrNull { (trueDp, _) -> trueDp != null && !trueDp.disjoint(newDp) }
                        if (trueNonDisjointEntry != null) {
                            existentiallyTrue[newDp] = trueNonDisjointEntry.value
                        }
                    }
                    val falseSubset = existentiallyFalse
                            .firstOrNull { falseDp -> falseDp != null && falseDp.subsetOf(newDp) }
                    if (falseSubset != null) {
                        existentiallyFalse.add(newDp)
                    } else {
                        val falseNonDisjointEntry = universallyFalse
                                .firstOrNull { falseDp -> falseDp != null && !falseDp.disjoint(newDp) }
                        if (falseNonDisjointEntry != null) {
                            existentiallyFalse.add(newDp)
                        }
                    }
                }
            }
        }
    }
}

fun ConstraintSystem.tryToSetDatapointsTrue(dpsToSetTrue: Collection<Datapoint>): ConstraintSystem? {
    val builder = ConstraintSystem.Builder(this)
    return try {
        builder.setDatapointsTrue(dpsToSetTrue)
        builder.build()
    } catch (e: ContradictoryException) {
        null
    }
}

fun ConstraintSystem.tryToSetDatapointsFalse(dpsToSetFalse: Collection<Datapoint>): ConstraintSystem? {
    if (dpsToSetFalse.any { it in universallyTrue }) {
        return null
    }
    val builder = ConstraintSystem.Builder(this)
    return try {
        builder.setDatapointsFalse(dpsToSetFalse)
        builder.build()
    } catch (e: ContradictoryException) {
        null
    }
}

class ContradictoryException(val contradictorySubset: List<Constraint>, s: String? = null) : Throwable(s)