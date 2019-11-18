package hu.bme.mit.theta.cfa.analysis.chc.decisiontree

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.type.booltype.BoolExprs.False
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.core.type.inttype.IntExprs.Eq
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import hu.bme.mit.theta.core.utils.ExprUtils
import org.junit.Assert
import org.junit.Test


internal class DecisionTreeTest {

    @Test(expected = DecisionTree.ContradictoryException::class)
    fun contradictoryTest() {
        val dpA = Datapoint(Invariant("A"), MutableValuation())
        val dpB = Datapoint(Invariant("B"), MutableValuation())
        val dpC = Datapoint(Invariant("C"), MutableValuation())
        val dpD = Datapoint(Invariant("D"), MutableValuation())
        val datapoints = setOf(dpA, dpB, dpC, dpD)
        val constraints = listOf(
                Constraint(emptyList(), dpA),
                Constraint(listOf(dpA, dpB, dpC, dpD), null),
                Constraint(listOf(dpA), dpB),
                Constraint(listOf(dpA, dpB), dpD),
                Constraint(listOf(dpD, dpB), dpC)
        )
        DecisionTree(datapoints, constraints)
    }

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

        val constraints = listOf(
                Constraint(emptyList(), dpA),
                Constraint(listOf(dpB), null)
        )

        val tree = DecisionTree(setOf(dpA, dpB), constraints)
        val expr = tree.candidates[invariant]
        Assert.assertEquals(True(), ExprUtils.simplify(expr, valuationA))
        Assert.assertEquals(False(), ExprUtils.simplify(expr, valuationB))
        Assert.assertEquals(1, expr.arity)
        val notConstantOps = expr.ops.first().ops.filter { it != True() }
        Assert.assertEquals(1, notConstantOps.size)
        Assert.assertEquals(Eq(x.ref, Int(12)), notConstantOps.first())
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

        val constraints = listOf(
                Constraint(emptyList(), dpA),
                Constraint(listOf(dpB), null)
        )

        val tree = DecisionTree(setOf(dpA, dpB), constraints)
        val exprA = tree.candidates[invariantA]
        val exprB = tree.candidates[invariantB]
        Assert.assertEquals(True(), ExprUtils.simplify(exprA, valuationA))
        Assert.assertEquals(1, exprA.arity)
        Assert.assertEquals(0, exprB.arity)
        val notConstantOps = exprA.ops.first().ops.filter { it != True() }
        Assert.assertEquals(0, notConstantOps.size)
    }
}