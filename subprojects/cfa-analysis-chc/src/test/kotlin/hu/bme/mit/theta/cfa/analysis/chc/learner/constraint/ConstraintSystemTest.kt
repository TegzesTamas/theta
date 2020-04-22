package hu.bme.mit.theta.cfa.analysis.chc.learner.constraint

import hu.bme.mit.theta.cfa.analysis.chc.DummyCHC
import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstraintSystemTest {

    private fun ConstraintSystem.assertValid() {
        assertTrue("Some datapoint forced also true and false", forcedTrue.keys.none { forcedFalse.contains(it) })
        assertTrue("Some datapoint forced also true and false", forcedFalse.none { forcedTrue.containsKey(it) })
    }

    private fun ConstraintSystem.assertUniversallyTrue(dp: Datapoint) {
        assertTrue("Some datapoint must be universally true, but was not found to be",
                forcedTrue.containsKey(dp))
    }

    private fun ConstraintSystem.assertExistentiallyTrue(dp: Datapoint) {
        assertTrue("Some datapoint must be existentially true, but was not found to be",
                forcedTrue.containsKey(dp)||datapoints[dp]?.any { forcedTrue.containsKey(it) } ?: false)
    }

    private fun ConstraintSystem.assertUniversallyFalse(dp: Datapoint) {
        assertTrue("Some datapoint must be universally false, but was not found to be",
                forcedFalse.contains(dp))
    }

    private fun ConstraintSystem.assertExistentiallyFalse(dp: Datapoint) {
        assertTrue("Some datapoint must be existentially false, but was not found to be",
                datapoints[dp]?.any { forcedFalse.contains(it) } ?: false)
    }

    @Test(expected = ContradictoryException::class)
    fun contradictoryBySimpleDeductionTest() {
        val dpA = Datapoint(Invariant("A"), MutableValuation())
        val builder = ConstraintSystem.Builder()
        builder.addConstraint(Constraint(null, dpA, DummyCHC))
        builder.addConstraint(Constraint(dpA, null, DummyCHC))
        builder.build()
    }

    @Test
    fun simpleDeductionTest() {
        val dpA = Datapoint(Invariant("A"), MutableValuation())
        val dpB = Datapoint(Invariant("B"), MutableValuation())
        val dpC = Datapoint(Invariant("C"), MutableValuation())
        val builder = ConstraintSystem.Builder()
        builder.addConstraint(Constraint(null, dpA, DummyCHC))
        builder.addConstraint(Constraint(null, dpB, DummyCHC))
        builder.addConstraint(Constraint(dpC, null, DummyCHC))
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

        builder.addConstraint(Constraint(null, dpA, DummyCHC))
        builder.addConstraint(Constraint(dpB, null, DummyCHC))
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

        builder.addConstraint(Constraint(null, dpA, DummyCHC))
        builder.addConstraint(Constraint(dpB, dpC, DummyCHC))
        builder.addConstraint(Constraint(dpD, null, DummyCHC))
        val constraintSystem = builder.build()
        constraintSystem.assertValid()
        constraintSystem.assertUniversallyTrue(dpA)
        constraintSystem.assertExistentiallyTrue(dpB)
        constraintSystem.assertExistentiallyTrue(dpC)

        constraintSystem.assertUniversallyFalse(dpD)
        constraintSystem.assertExistentiallyFalse(dpC)
        constraintSystem.assertExistentiallyFalse(dpB)
    }

    @Test(expected = ContradictoryException::class)
    fun intersectionContradiction() {
        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())
        val z = DeclManager.getVar("z", Int())

        val inv = Invariant("A")

        val valA = MutableValuation().apply {
            put(x, Int(2))
            put(y, Int(3))
        }
        val dpA = Datapoint(inv, valA)
        val valB = MutableValuation().apply {
            put(x, Int(2))
            put(z, Int(32))
        }
        val dpB = Datapoint(inv, valB)
        val valC = MutableValuation().apply {
            put(x, Int(18))
            put(y, Int(12))
            put(z, Int(98))
        }
        val dpC = Datapoint(inv, valC)
        val valD = MutableValuation().apply {
            put(y, Int(12))
            put(z, Int(98))
        }
        val dpD = Datapoint(inv, valD)

        ConstraintSystem.Builder()
                .addConstraint(Constraint(null, dpA, DummyCHC))
                .addConstraint(Constraint(dpB, dpC, DummyCHC))
                .addConstraint(Constraint(dpD, null, DummyCHC))
                .build()
    }

}