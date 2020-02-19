package hu.bme.mit.theta.cfa.analysis.chc.learner

import kotlin.streams.toList


class ConstraintSystem private constructor(initAmbiguousDatapoints: Set<Datapoint>,
                                           initConstraints: Collection<Constraint>,
                                           initExistentiallyForcedTrue: Set<Datapoint?>,
                                           initUniversallyForcedTrue: Set<Datapoint?>) {

    constructor(datapoints: Set<Datapoint>, constraints: Collection<Constraint>) : this(datapoints, constraints, emptySet(), emptySet())

    private val ambiguousDatapoints: Set<Datapoint>
    val filteredConstraints: List<Constraint>


    val existentiallyForcedTrue: Set<Datapoint?>
    val universallyForcedTrue: Set<Datapoint?>


    init {
        val universallyForcedTrue = initUniversallyForcedTrue.toMutableSet()
        val existentiallyForcedTrue = initExistentiallyForcedTrue.toMutableSet()
        var filteredConstraints = filterConstraints(initConstraints, universallyForcedTrue, existentiallyForcedTrue)
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
            filteredConstraints = filterConstraints(filteredConstraints, universallyForcedTrue, existentiallyForcedTrue)
        } while (newUniversallyForcedTrue.isNotEmpty())
        this.ambiguousDatapoints = ambiguousDatapoints
        this.filteredConstraints = filteredConstraints
        this.existentiallyForcedTrue = existentiallyForcedTrue
        this.universallyForcedTrue = universallyForcedTrue
    }

    fun tryToSetDatapointsTrue(dpsToSetTrue: Collection<Datapoint>): ConstraintSystem? =
            try {
                ConstraintSystem(ambiguousDatapoints,
                        filteredConstraints,
                        existentiallyForcedTrue + dpsToSetTrue,
                        universallyForcedTrue)
            } catch (e: ContradictoryException) {
                null
            }

    private companion object {
        private fun filterConstraints(constraints: Collection<Constraint>,
                                      universallyForcedTrue: Set<Datapoint?>,
                                      existentiallyForcedTrue: Set<Datapoint?>) = constraints.stream()
                .filter { it.target !in universallyForcedTrue }
                .map { c -> Constraint(c.source.filter { dp -> dp !in existentiallyForcedTrue }, c.target) }
                .toList()
    }
}
