package hu.bme.mit.theta.cfa.analysis.chc.learner

class ConstraintSystem(datapoints: Collection<Datapoint>, constraints: Collection<Constraint>) {
    private val mutExistentiallyForcedTrue: MutableSet<Datapoint?> = mutableSetOf()
    private val mutUniversallyForcedTrue: MutableSet<Datapoint?> = mutableSetOf()
    private var ambiguousDatapoints = datapoints
    val existentiallyForcedTrue: Set<Datapoint?>
        get() = mutExistentiallyForcedTrue
    val universallyForcedTrue: Set<Datapoint?>
        get() = mutUniversallyForcedTrue
    var filteredConstraints: Collection<Constraint> = constraints
        private set

    var contradictory: Boolean = false
        private set

    init {
        calcForced()
    }

    private fun calcForced() {
        do {
            val newUniversallyForcedTrue = filteredConstraints
                    .filter { it.source.isEmpty() }
                    .map { it.target }
                    .toMutableList()
            if (null in newUniversallyForcedTrue) {
                throw ContradictoryException("Unsatisfiable constraints")
            }
            mutUniversallyForcedTrue += newUniversallyForcedTrue
            mutExistentiallyForcedTrue += newUniversallyForcedTrue
            val stillAmbiguous = mutableSetOf<Datapoint>()
            for (ambiguous in ambiguousDatapoints) {
                if (ambiguous !in newUniversallyForcedTrue) {
                    if (newUniversallyForcedTrue.any { it != null && ambiguous.subsetOf(it) }) {
                        mutUniversallyForcedTrue += ambiguous
                    } else {
                        stillAmbiguous += ambiguous
                        if (newUniversallyForcedTrue.any { it != null && !ambiguous.disjoint(it) }) {
                            mutExistentiallyForcedTrue += ambiguous
                        }
                    }
                }
            }
            ambiguousDatapoints = stillAmbiguous
            filteredConstraints = filteredConstraints
                    .filter { it.target !in universallyForcedTrue }
                    .map { c -> Constraint(c.source.filter { dp -> dp !in existentiallyForcedTrue }, c.target) }
        } while (newUniversallyForcedTrue.isNotEmpty())
    }

    class ContradictoryException(s: String? = null) : Throwable(s)

}
