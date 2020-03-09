package hu.bme.mit.theta.cfa.analysis.chc.learner

import kotlin.streams.toList


class ConstraintSystem private constructor(initAmbiguousDatapoints: Set<Datapoint>,
                                           initConstraints: Collection<Constraint>,
                                           initExistentiallyForcedTrue: Set<Datapoint?>,
                                           initUniversallyForcedTrue: Set<Datapoint?>,
                                           initExistentiallyForcedFalse: Set<Datapoint?>,
                                           initUniversallyForcedFalse: Set<Datapoint?>) {

    constructor(datapoints: Set<Datapoint>, constraints: Collection<Constraint>) : this(
            datapoints,
            constraints,
            emptySet(),
            emptySet(),
            emptySet(),
            setOf(null)
    )

    private val ambiguousDatapoints: Set<Datapoint>
    val filteredConstraints: List<Constraint>


    val existentiallyForcedTrue: Set<Datapoint?>
    val universallyForcedTrue: Set<Datapoint?>

    val existentiallyForcedFalse: Set<Datapoint?>
    val universallyForcedFalse: Set<Datapoint?>


    init {
        val universallyForcedTrue = initUniversallyForcedTrue.toMutableSet()
        val existentiallyForcedTrue = initExistentiallyForcedTrue.toMutableSet()
        val existentiallyForcedFalse = initExistentiallyForcedFalse.toMutableSet()
        val universallyForcedFalse = initUniversallyForcedFalse.toMutableSet()
        var filteredConstraints = filterConstraints(initConstraints, universallyForcedTrue, existentiallyForcedTrue,
                universallyForcedFalse, existentiallyForcedFalse)
        var ambiguousDatapoints = initAmbiguousDatapoints
        do {
            val newUniversallyForcedTrue = filteredConstraints
                    .stream()
                    .filter { it.source.isEmpty() }
                    .map { it.target }
                    .toList()
            if (null in newUniversallyForcedTrue) {
                throw ContradictoryException("Unsatisfiable constraints")
            }
            universallyForcedTrue.addAll(newUniversallyForcedTrue)
            existentiallyForcedTrue.addAll(newUniversallyForcedTrue)
            val stillAmbiguous = mutableSetOf<Datapoint>()
            for (ambiguous in ambiguousDatapoints) {
                if (ambiguous !in newUniversallyForcedTrue) {
                    if (newUniversallyForcedTrue.any { it != null && ambiguous.subsetOf(it) }) {
                        universallyForcedTrue.add(ambiguous)
                        existentiallyForcedTrue.add(ambiguous)
                    } else {
                        stillAmbiguous += ambiguous
                        if (ambiguous !in existentiallyForcedTrue && newUniversallyForcedTrue.any { it != null && !ambiguous.disjoint(it) }) {
                            existentiallyForcedTrue.add(ambiguous)
                        }
                    }
                }
            }
            ambiguousDatapoints = stillAmbiguous
            filteredConstraints = filterConstraints(filteredConstraints, universallyForcedTrue, existentiallyForcedTrue,
                    universallyForcedFalse, existentiallyForcedFalse)
        } while (newUniversallyForcedTrue.isNotEmpty())

        do {
            val newUniversallyForcedFalse = filteredConstraints
                    .stream()
                    .filter { it.target == null && it.source.size == 1 }
                    .map { it.source.single() }
                    .toList()
            universallyForcedFalse.addAll(newUniversallyForcedFalse)
            existentiallyForcedFalse.addAll(newUniversallyForcedFalse)

            if (newUniversallyForcedFalse.any { it in existentiallyForcedTrue }) {
                throw ContradictoryException("Unsatisfiable constraints")
            }

            val stillAmbiguous = mutableSetOf<Datapoint>()
            for (ambiguous in ambiguousDatapoints) {
                if (ambiguous !in newUniversallyForcedFalse) {
                    if (newUniversallyForcedFalse.any { it != null && ambiguous.subsetOf(it) }) {
                        if (ambiguous in existentiallyForcedTrue) {
                            throw ContradictoryException("Unsatisfiable constraints")
                        }
                        universallyForcedFalse.add(ambiguous)
                        existentiallyForcedFalse.add(ambiguous)
                    } else {
                        stillAmbiguous += ambiguous
                        if (newUniversallyForcedFalse.any { it != null && !ambiguous.disjoint(it) }) {
                            existentiallyForcedFalse.add(ambiguous)
                        }
                    }
                }
            }
            ambiguousDatapoints = stillAmbiguous
            filteredConstraints = filterConstraints(filteredConstraints, universallyForcedTrue, existentiallyForcedTrue,
                    universallyForcedFalse, existentiallyForcedFalse)

        } while (newUniversallyForcedFalse.isNotEmpty())
        this.ambiguousDatapoints = ambiguousDatapoints
        this.filteredConstraints = filteredConstraints
        this.existentiallyForcedTrue = existentiallyForcedTrue
        this.universallyForcedTrue = universallyForcedTrue
        this.existentiallyForcedFalse = existentiallyForcedFalse
        this.universallyForcedFalse = universallyForcedFalse
    }

    fun tryToSetDatapointsTrue(dpsToSetTrue: Collection<Datapoint>): ConstraintSystem? =
            try {
                ConstraintSystem(ambiguousDatapoints,
                        filteredConstraints,
                        existentiallyForcedTrue + dpsToSetTrue,
                        universallyForcedTrue,
                        existentiallyForcedFalse,
                        universallyForcedFalse)
            } catch (e: ContradictoryException) {
                null
            }

    fun tryToSetDatapointsFalse(dpsToSetFalse: Collection<Datapoint>): ConstraintSystem? =
            try {
                ConstraintSystem(ambiguousDatapoints,
                        filteredConstraints,
                        existentiallyForcedTrue,
                        universallyForcedTrue,
                        existentiallyForcedFalse + dpsToSetFalse,
                        universallyForcedFalse)
            } catch (e: ContradictoryException) {
                null
            }

    private companion object {
        private fun filterConstraints(constraints: Collection<Constraint>,
                                      universallyForcedTrue: Set<Datapoint?>,
                                      existentiallyForcedTrue: Set<Datapoint?>,
                                      universallyForcedFalse: Set<Datapoint?>,
                                      existentiallyForcedFalse: Set<Datapoint?>) =
                constraints.stream()
                        .filter { constraint ->
                            constraint.target !in universallyForcedTrue
                                    && constraint.source.none { dp -> dp in universallyForcedFalse }
                        }
                        .map { c ->
                            Constraint(
                                    c.source.filter { dp -> dp !in existentiallyForcedTrue },
                                    if (c.target in existentiallyForcedFalse) null else c.target
                            )
                        }
                        .toList()
    }
}
