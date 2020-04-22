package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.DummyCHC
import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.Constraint
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.type.booltype.BoolExprs.False
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import hu.bme.mit.theta.core.type.inttype.IntExprs.Leq
import hu.bme.mit.theta.core.utils.ExprUtils
import org.junit.Assert
import org.junit.Test


internal class DecisionTreeTest {

    @Test
    fun splitByVariableValueTest() {
        val valuationA = MutableValuation()
        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())
        valuationA.put(x, Int(12))
        valuationA.put(y, Int(50))
        val invariant = Invariant("A")
        val dpA = Datapoint(invariant, valuationA)

        val valuationB = MutableValuation()
        valuationB.put(x, Int(90))
        valuationB.put(y, Int(50))
        val dpB = Datapoint(invariant, valuationB)

        val constraintsSystem = ConstraintSystem.Builder()
                .addConstraint(Constraint(null, dpA, DummyCHC.unchanging))
                .addConstraint(Constraint(dpB, null, DummyCHC.unchanging))
                .build()

        val tree = Learner(constraintsSystem).buildTree()
        val expr = tree.candidates[invariant]
        Assert.assertEquals(True(), ExprUtils.simplify(expr, valuationA))
        Assert.assertEquals(False(), ExprUtils.simplify(expr, valuationB))
        Assert.assertEquals(1, expr.arity)
        val notConstantOps = expr.ops.first().ops.filter { it != True() }
        Assert.assertEquals(1, notConstantOps.size)
        Assert.assertEquals(Leq(x.ref, Int(12)), notConstantOps.first())
    }


    @Test
    fun splitByInvariantTest() {
        val valuationA = MutableValuation()
        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())
        valuationA.put(x, Int(120))
        valuationA.put(y, Int(660))
        val invariantA = Invariant("A")
        val dpA = Datapoint(invariantA, valuationA)

        val valuationB = MutableValuation()
        valuationB.put(x, Int(120))
        valuationB.put(y, Int(660))
        val invariantB = Invariant("B")
        val dpB = Datapoint(invariantB, valuationB)

        val constraintSystem = ConstraintSystem.Builder()
                .addConstraint(Constraint(null, dpA, DummyCHC.unchanging))
                .addConstraint(Constraint(dpB, null, DummyCHC.unchanging))
                .build()

        val tree = Learner(constraintSystem).buildTree()
        val exprA = tree.candidates[invariantA]
        val exprB = tree.candidates[invariantB]
        Assert.assertEquals(True(), ExprUtils.simplify(exprA, valuationA))
        Assert.assertEquals(1, exprA.arity)
        Assert.assertEquals(0, exprB.arity)
        val notConstantOps = exprA.ops.first().ops.filter { it != True() }
        Assert.assertEquals(0, notConstantOps.size)
    }
}