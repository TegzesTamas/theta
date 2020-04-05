package hu.bme.mit.theta.cfa.analysis.chc.learner.constraint

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstraintSystemTest {

    @Test(expected = ContradictoryException::class)
    fun contradictoryBySimpleDeductionTest() {
        val dpA = Datapoint(Invariant("A"), MutableValuation())
        val dpB = Datapoint(Invariant("B"), MutableValuation())
        val builder = ConstraintSystem.Builder()
        builder.addConstraint(Constraint(emptyList(), dpA))
        builder.addConstraint(Constraint(emptyList(), dpB))
        builder.addConstraint(Constraint(listOf(dpA, dpB), null))
        builder.build()
    }

    @Test
    fun simpleDeductionTest() {
        val dpA = Datapoint(Invariant("A"), MutableValuation())
        val dpB = Datapoint(Invariant("B"), MutableValuation())
        val dpC = Datapoint(Invariant("C"), MutableValuation())
        val builder = ConstraintSystem.Builder()
        builder.addConstraint(Constraint(emptyList(), dpA))
        builder.addConstraint(Constraint(emptyList(), dpB))
        builder.addConstraint(Constraint(listOf(dpA, dpB, dpC), null))
        val constraintSystem = builder.build()
        constraintSystem.assertValid()
        constraintSystem.assertUniversallyTrue(dpA)
        constraintSystem.assertUniversallyTrue(dpB)
        constraintSystem.assertUniversallyFalse(dpC)
    }

    @Test(expected = ContradictoryException::class)
    fun subsetContradictoryTest() {
        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())
        val dpA = Datapoint(Invariant("A"), MutableValuation().apply { put(x, Int(0)) })
        val dpB = Datapoint(Invariant("A"), MutableValuation().apply { put(y, Int(1)) })

        val builder = ConstraintSystem.Builder()

        builder.addConstraint(Constraint(emptyList(), dpA))
        builder.addConstraint(Constraint(listOf(dpB), null))
        builder.build()
    }

    @Test
    fun nonDisjointTest() {
        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())
        val dpA = Datapoint(Invariant("L0"), MutableValuation().apply { put(y, Int(2)) })
        val dpB = Datapoint(Invariant("L0"), MutableValuation().apply { put(x, Int(2)) })
        val dpC = Datapoint(Invariant("L1"), MutableValuation().apply { put(x, Int(2)) })
        val dpD = Datapoint(Invariant("L1"), MutableValuation().apply { put(y, Int(4)) })

        val builder = ConstraintSystem.Builder()

        builder.addConstraint(Constraint(emptyList(), dpA))
        builder.addConstraint(Constraint(listOf(dpB), dpC))
        builder.addConstraint(Constraint(listOf(dpD), null))
        val constraintSystem = builder.build()
        constraintSystem.assertValid()
        constraintSystem.assertUniversallyTrue(dpA)
        constraintSystem.assertExistentiallyTrue(dpB)
        constraintSystem.assertExistentiallyTrue(dpC)

        constraintSystem.assertUniversallyFalse(dpD)
        constraintSystem.assertExistentiallyFalse(dpC)
        constraintSystem.assertExistentiallyFalse(dpB)
    }

    private fun ConstraintSystem.assertValid() {
        assertTrue("Some datapoints are universally true, but not existentially true",
                existentiallyTrue.keys.containsAll(universallyTrue.keys))
        assertTrue("Some datapoints are universally false, but not existentially false",
                existentiallyFalse.containsAll(universallyFalse))
    }

    private fun ConstraintSystem.assertUniversallyTrue(dp: Datapoint) {
        assertTrue("Some datapoint must be universally true, but was not found to be",
                dp in universallyTrue.keys)
    }

    private fun ConstraintSystem.assertExistentiallyTrue(dp: Datapoint) {
        assertTrue("Some datapoint must be existentially true, but was not found to be",
                dp in existentiallyTrue.keys)
    }

    private fun ConstraintSystem.assertUniversallyFalse(dp: Datapoint) {
        assertTrue("Some datapoint must be universally false, but was not found to be",
                dp in universallyFalse)
    }

    private fun ConstraintSystem.assertExistentiallyFalse(dp: Datapoint) {
        assertTrue("Some datapoint must be existentially false, but was not found to be",
                dp in existentiallyFalse)
    }

}