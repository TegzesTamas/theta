package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.DummyCHC
import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.Constraint
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.type.anytype.Exprs.Prime
import hu.bme.mit.theta.core.type.booltype.BoolExprs.*
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import hu.bme.mit.theta.core.type.inttype.IntExprs.Leq
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.solver.utils.WithPushPop
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals(True(), ExprUtils.simplify(expr, valuationA))
        assertEquals(False(), ExprUtils.simplify(expr, valuationB))
        assertEquals(1, expr.arity)
        val notConstantOps = expr.ops.first().ops.filter { it != True() }
        assertEquals(1, notConstantOps.size)
        assertEquals(Leq(x.ref, Int(12)), notConstantOps.first())
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
        assertEquals(True(), ExprUtils.simplify(exprA, valuationA))
        assertEquals(1, exprA.arity)
        assertEquals(0, exprB.arity)
        val notConstantOps = exprA.ops.first().ops.filter { it != True() }
        assertEquals(0, notConstantOps.size)
    }

    @Test
    fun subsetsWithDifferentLabelsTest() {
        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())
        val invA = Invariant("invA")
        val invB = Invariant("invB")

        val dpTrue = Datapoint(invA, MutableValuation().apply { put(x, Int(1)); put(y, Int(1)) })
        val dpFalse = Datapoint(invA, MutableValuation().apply { put(x, Int(1)); put(y, Int(2)) })
        val dpSuperSet = Datapoint(invA, MutableValuation().apply { put(x, Int(1)) })
        val dpUnrelated = Datapoint(invB, MutableValuation().apply { put(x, Int(12)); put(y, Int(300)) })

        val constraintSystem = ConstraintSystem.Builder()
                .addConstraint(Constraint(null, dpTrue, DummyCHC.unchanging))
                .addConstraint(Constraint(dpFalse, null, DummyCHC.unchanging))
                .addConstraint(Constraint(dpSuperSet, dpUnrelated, DummyCHC(x, y)))
                .build()

        val tree = Learner(constraintSystem).buildTree()
        val candidates = tree.candidates
        val invACandidate = candidates[invA]
        val invBCandidate = candidates[invB]
        assertEquals("Constraint 1 not honored.", True(), ExprUtils.simplify(invACandidate, dpTrue.valuation))
        assertEquals("Constraint 2 not honored.", False(), ExprUtils.simplify(invACandidate, dpFalse.valuation))
        val solver = Z3SolverFactory.getInstace().createSolver()
        WithPushPop(solver).use {
            solver.add(And(invACandidate, dpSuperSet.valuation.toExpr(), Prime(Not(invBCandidate)), dpUnrelated.valuation.toExpr()))
            assertTrue("Constraint 3 not honored", solver.check().isUnsat)
        }
    }
}