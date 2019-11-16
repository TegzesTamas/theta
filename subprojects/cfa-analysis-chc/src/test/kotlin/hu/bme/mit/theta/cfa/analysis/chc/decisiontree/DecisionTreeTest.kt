package hu.bme.mit.theta.cfa.analysis.chc.decisiontree

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.core.model.MutableValuation
import org.junit.Test


internal class DecisionTreeTest {

    @Test(expected = DecisionTree.ContradictoryException::class)
    fun contradictoryTest() {
        val dpA = Datapoint(Invariant("A"), MutableValuation())
        val dpB = Datapoint(Invariant("B"), MutableValuation())
        val dpC = Datapoint(Invariant("C"), MutableValuation())
        val dpD = Datapoint(Invariant("D"), MutableValuation())
        val datapoints = setOf(dpA, dpB, dpC, dpD)
        val constraints = sequenceOf(
                Constraint(emptyList(), dpA),
                Constraint(listOf(dpA, dpB, dpC, dpD), null),
                Constraint(listOf(dpA), dpB),
                Constraint(listOf(dpA, dpB), dpD),
                Constraint(listOf(dpD, dpB), dpC)
        )
        DecisionTree(datapoints, constraints)
    }
}