package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.DummyCHC
import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Constraint
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.constraint.eval
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import org.junit.Assert
import org.junit.Test

class SimpleLearnerTest {
    @Test
    fun simpleTest() {
        val invariantA = Invariant("A")
        val invariantB = Invariant("B")
        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())
        val valuationA = MutableValuation().apply {
            put(x, Int(2))
            put(y, Int(3))
        }
        val valuationB = MutableValuation().apply {
            put(y, Int(4))
        }
        val valuationC = MutableValuation().apply {
            put(x, Int(2))
            put(y, Int(4))
        }
        val valuationD = MutableValuation().apply {
            put(x, Int(1))
        }

        val dp = listOf(
            Datapoint(invariantA, valuationA),
            Datapoint(invariantB, valuationB),
            Datapoint(invariantA, valuationC),
            Datapoint(invariantB, valuationD)
        )

        val constraintSystem = ConstraintSystem.Builder()
            .addConstraint(Constraint(null, dp[0], DummyCHC()))
            .addConstraint(Constraint(null, dp[1], DummyCHC()))
            .addConstraint(Constraint(dp[2], dp[3], DummyCHC()))
            .build()
        val learner = SimpleLearner("simpleLearner")
        val candidates = learner.suggestCandidates(constraintSystem)

        Assert.assertEquals("Forced-true datapoint not classified true", true, dp[0].eval(candidates[dp[0].invariant]))
        Assert.assertEquals("Forced-true datapoint not classified true", true, dp[1].eval(candidates[dp[1].invariant]))
        Assert.assertNotEquals("Forced-true datapoint not classified true", true, dp[2].eval(candidates[dp[2].invariant]))
        Assert.assertNotEquals("Forced-true datapoint not classified true", true, dp[3].eval(candidates[dp[3].invariant]))
    }
}