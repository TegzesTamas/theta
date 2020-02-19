package hu.bme.mit.theta.cfa.analysis.chc.learner

import kotlin.streams.toList


class ConstraintSystem(datapoints: Set<Datapoint>, constraints: Collection<Constraint>) {
    private var mutExistentiallyForcedTrue: MutableSet<Datapoint?> = mutableSetOf()
    private var mutUniversallyForcedTrue: MutableSet<Datapoint?> = mutableSetOf()
    private var ambiguousDatapoints = datapoints
    var filteredConstraints: Collection<Constraint> = constraints
        private set


    val existentiallyForcedTrue: Set<Datapoint?>
        get() = mutExistentiallyForcedTrue
    val universallyForcedTrue: Set<Datapoint?>
        get() = mutUniversallyForcedTrue


    init {
        calcForced()
    }

    fun tryToSetDatapointsTrue(dpsToSetTrue: Collection<Datapoint>): Boolean {
        val bckpMutExistentiallyForcedTrue = mutExistentiallyForcedTrue.toMutableSet()
        val bckpMutUniversallyForcedTrue = mutUniversallyForcedTrue.toMutableSet()
        val bckpAmbigousDatapoints = ambiguousDatapoints.toSet()
        val bckpFilteredConstraints = filteredConstraints.toList()

        mutExistentiallyForcedTrue.addAll(dpsToSetTrue)
        try {
            filterConstraints()
            calcForced()
        } catch (e: ContradictoryException) {
            mutExistentiallyForcedTrue = bckpMutExistentiallyForcedTrue
            mutUniversallyForcedTrue = bckpMutUniversallyForcedTrue
            ambiguousDatapoints = bckpAmbigousDatapoints
            filteredConstraints = bckpFilteredConstraints
            return false
        }
        return true
    }

    @Throws(ContradictoryException::class)
    private fun calcForced() {
        do {
            val newUniversallyForcedTrue = filteredConstraints
                    .stream()
                    .filter { it.source.isEmpty() }
                    .map { it.target }
                    .toList()
            if (null in newUniversallyForcedTrue) {
                throw ContradictoryException("Unsatisfiable constraints")
            }
            mutUniversallyForcedTrue.addAll(newUniversallyForcedTrue)
            mutExistentiallyForcedTrue.addAll(newUniversallyForcedTrue)
            val stillAmbiguous = mutableSetOf<Datapoint>()
            for (ambiguous in ambiguousDatapoints) {
                if (ambiguous !in newUniversallyForcedTrue) {
                    if (newUniversallyForcedTrue.any { it != null && ambiguous.subsetOf(it) }) {
                        mutUniversallyForcedTrue.add(ambiguous)
                        mutExistentiallyForcedTrue.add(ambiguous)
                    } else {
                        stillAmbiguous += ambiguous
                        if (ambiguous !in mutExistentiallyForcedTrue && newUniversallyForcedTrue.any { it != null && !ambiguous.disjoint(it) }) {
                            mutExistentiallyForcedTrue.add(ambiguous)
                        }
                    }
                }
            }
            ambiguousDatapoints = stillAmbiguous
            filterConstraints()
        } while (newUniversallyForcedTrue.isNotEmpty())
    }

    private fun filterConstraints() {
        filteredConstraints =
                filteredConstraints.stream()
                        .filter { it.target !in universallyForcedTrue }
                        .map { c -> Constraint(c.source.filter { dp -> dp !in existentiallyForcedTrue }, c.target) }
                        .toList()
    }
}
