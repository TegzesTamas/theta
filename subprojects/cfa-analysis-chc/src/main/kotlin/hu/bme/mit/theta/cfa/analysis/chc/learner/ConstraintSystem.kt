package hu.bme.mit.theta.cfa.analysis.chc.learner

class ConstraintSystem(datapoints: Collection<Datapoint>, constraints: Collection<Constraint>) {
    private val mutExistentiallyForcedTrue: MutableSet<Datapoint?> = mutableSetOf()
    private val mutUniversallyForcedTrue: MutableSet<Datapoint?> = mutableSetOf()
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
            filteredConstraints =
                    filteredConstraints.stream()
                            .filter { it.target !in universallyForcedTrue }
                            .map { c -> Constraint(c.source.filter { dp -> dp !in existentiallyForcedTrue }, c.target) }
                            .toList()
        } while (newUniversallyForcedTrue.isNotEmpty())
    }
}
