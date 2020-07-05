package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.stmt.AssignStmt
import hu.bme.mit.theta.core.stmt.AssumeStmt
import hu.bme.mit.theta.core.stmt.Stmts
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntNeqExpr
import hu.bme.mit.theta.solver.SolverStatus
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Assert
import org.junit.Test

class CfaToChcTest {

    @Test(timeout = 1000)
    fun singlePathLooplessSafeTest() {
        val cfaBuilder = CFA.builder()
        val initLoc = cfaBuilder.createLoc("init")
        cfaBuilder.initLoc = initLoc
        val errorLoc = cfaBuilder.createLoc("error")
        cfaBuilder.finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.errorLoc = errorLoc

        val midLoc = cfaBuilder.createLoc("middle")

        val x = DeclManager.getVar("CfaToCHCTest_loopless_x", IntExprs.Int())

        cfaBuilder.createEdge(initLoc, midLoc, AssignStmt.of(x, IntExprs.Int(0)))
        cfaBuilder.createEdge(midLoc, errorLoc, AssumeStmt.of(IntNeqExpr.of(x.ref, IntExprs.Int(0))))

        val cfa = cfaBuilder.build()

        val chcs = cfaToChc(cfa).chcs
        Assert.assertEquals("Too many chcs: $chcs", 1, chcs.size)

        val solver = Z3SolverFactory.getInstance().createSolver()
        solver.push()
        solver.addCHC(chcs.first(), GenericCandidates(True(), emptyMap()))
        Assert.assertEquals("CHC should be unsatisfiable, but it is satisfiable: ${chcs.first()}", SolverStatus.UNSAT, solver.check())
        solver.pop()
    }

    @Test(timeout = 1000)
    fun multiPathLooplessSafeTest() {
        val cfaBuilder = CFA.builder()
        val initLoc = cfaBuilder.createLoc("init")
        cfaBuilder.initLoc = initLoc
        val errorLoc = cfaBuilder.createLoc("error")
        cfaBuilder.finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.errorLoc = errorLoc

        val leftLoc = cfaBuilder.createLoc("left")
        val rightLoc = cfaBuilder.createLoc("right")

        val x = DeclManager.getVar("CfaToCHCTest_loopless_parallel_x", IntExprs.Int())
        val y = DeclManager.getVar("CfaToCHCTest_loopless_parallel_y", IntExprs.Int())


        cfaBuilder.createEdge(initLoc, leftLoc, AssignStmt.of(x, IntExprs.Int(0)))
        cfaBuilder.createEdge(leftLoc, errorLoc, AssumeStmt.of(IntNeqExpr.of(x.ref, IntExprs.Int(0))))

        cfaBuilder.createEdge(initLoc, rightLoc, AssignStmt.of(y, IntExprs.Int(0)))
        cfaBuilder.createEdge(rightLoc, errorLoc, AssumeStmt.of(IntNeqExpr.of(y.ref, IntExprs.Int(0))))


        val cfa = cfaBuilder.build()
        val chcs = cfaToChc(cfa).chcs

        Assert.assertEquals("Incorrect number of chc: $chcs", 2, chcs.size)

        val solver = Z3SolverFactory.getInstance().createSolver()
        for (chc in chcs) {
            solver.push()
            solver.addCHC(chc, GenericCandidates(True(), emptyMap()))
            Assert.assertEquals("CHC should be unsatisfiable, but it is satisfiable: $chc", SolverStatus.UNSAT, solver.check())
            solver.pop()
        }
    }

    @Test(timeout = 1000)
    fun multiPathLooplessUnsafeTest() {

        val cfaBuilder = CFA.builder()
        val initLoc = cfaBuilder.createLoc("init")
        cfaBuilder.initLoc = initLoc
        val errorLoc = cfaBuilder.createLoc("error")
        cfaBuilder.finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.errorLoc = errorLoc

        val midLoc = cfaBuilder.createLoc("middle")

        val x = DeclManager.getVar("CfaToCHCTest_loopless_parallel_x", IntExprs.Int())
        val y = DeclManager.getVar("CfaToCHCTest_loopless_parallel_y", IntExprs.Int())


        cfaBuilder.createEdge(initLoc, midLoc, AssignStmt.of(x, IntExprs.Int(0)))
        cfaBuilder.createEdge(midLoc, errorLoc, AssumeStmt.of(IntNeqExpr.of(x.ref, IntExprs.Int(0))))

        cfaBuilder.createEdge(initLoc, midLoc, AssignStmt.of(y, IntExprs.Int(0)))
        cfaBuilder.createEdge(midLoc, errorLoc, AssumeStmt.of(IntNeqExpr.of(y.ref, IntExprs.Int(0))))


        val cfa = cfaBuilder.build()
        val chcs = cfaToChc(cfa).chcs

        Assert.assertEquals("Incorrect number of chc: $chcs", 4, chcs.size)

        val solver = Z3SolverFactory.getInstance().createSolver()
        var satCount = 0
        var unsatCount = 0
        for (chc in chcs) {
            solver.push()
            solver.addCHC(chc, GenericCandidates(True(), emptyMap()))
            when (solver.check()) {
                SolverStatus.SAT -> ++satCount
                SolverStatus.UNSAT -> ++unsatCount
                else -> {
                    throw AssertionError("solver.check() returned unknown result")
                }
            }
            solver.pop()
        }
        Assert.assertEquals("Incorrect number of CHCs satisfiable", 2, satCount)
        Assert.assertEquals("Incorrect number of CHCs unsatisfiable", 2, unsatCount)
    }

    @Test(timeout = 2000)
    fun singlePathSingleLoopSafeTest() {
        val cfaBuilder = CFA.builder()
        val initLoc = cfaBuilder.createLoc("init")
        cfaBuilder.initLoc = initLoc
        val errorLoc = cfaBuilder.createLoc("error")
        cfaBuilder.finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.errorLoc = errorLoc

        val midLoc = cfaBuilder.createLoc("middle")

        val x = DeclManager.getVar("CfaToCHCTest_loopless_x", IntExprs.Int())
        val y = DeclManager.getVar("CfaToCHCTest_loopless_y", IntExprs.Int())

        cfaBuilder.createEdge(initLoc, midLoc, AssignStmt.of(x, IntExprs.Int(0)))
        cfaBuilder.createEdge(midLoc, errorLoc, AssumeStmt.of(IntNeqExpr.of(x.ref, IntExprs.Int(0))))
        cfaBuilder.createEdge(midLoc, midLoc, AssignStmt.of(y, IntExprs.Add(y.ref, IntExprs.Int(1))))

        val cfa = cfaBuilder.build()

        val chcSystem = cfaToChc(cfa)
        val chcs = chcSystem.chcs

        Assert.assertEquals("Incorrect number of chcs: $chcs", 3, chcs.size)
        val invariants = chcSystem.invariants
        Assert.assertEquals("Incorrect number of loop invariants: $invariants", 1, invariants.size)

        val solver = Z3SolverFactory.getInstance().createSolver()
        val invariantExpr = IntExprs.Eq(x.ref, IntExprs.Int(0))
        for (chc in chcs) {
            solver.push()
            solver.addCHC(chc, GenericCandidates(invariantExpr, emptyMap()))
            Assert.assertEquals("CHC satisfiable with bound invariant that should prove safety", SolverStatus.UNSAT, solver.check())
            solver.pop()
        }
    }

    @Test(timeout = 2000)
    fun multiplePathsMultipleLoopsSafeTest() {
        val cfaBuilder = CFA.builder()
        val initLoc = cfaBuilder.createLoc("init")
        cfaBuilder.initLoc = initLoc
        val errorLoc = cfaBuilder.createLoc("error")
        cfaBuilder.finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.errorLoc = errorLoc

        val a = cfaBuilder.createLoc("a")
        val b = cfaBuilder.createLoc("b")
        val c = cfaBuilder.createLoc("c")
        val d = cfaBuilder.createLoc("d")

        val x = DeclManager.getVar("multiPathMultiLoop_x", IntExprs.Int())
        val y = DeclManager.getVar("multiPathMultiLoop_y", IntExprs.Int())

        cfaBuilder.createEdge(initLoc, a, Stmts.Assign(y, IntExprs.Int(1)))
        cfaBuilder.createEdge(a, b, Stmts.Assume(IntExprs.Lt(x.ref, y.ref)))
        cfaBuilder.createEdge(a, c, Stmts.Assume(IntExprs.Gt(x.ref, y.ref)))
        cfaBuilder.createEdge(a, d, Stmts.Assume(IntExprs.Eq(x.ref, y.ref)))
        cfaBuilder.createEdge(b, d, Stmts.Assign(y, IntExprs.Sub(y.ref, x.ref)))
        cfaBuilder.createEdge(c, c, Stmts.Assign(x, IntExprs.Add(x.ref, y.ref)))
        cfaBuilder.createEdge(c, d, Stmts.Assign(y, x.ref))
        cfaBuilder.createEdge(d, a, Stmts.Havoc(x))
        cfaBuilder.createEdge(d, errorLoc, Stmts.Assume(IntExprs.Leq(y.ref, IntExprs.Int(0))))

        val cfa = cfaBuilder.build()
        val chcSystem = cfaToChc(cfa)
        val chcs = chcSystem.chcs

        Assert.assertEquals("Incorrect number of chcs", 9, chcs.size)
        val invariants = chcSystem.invariants
        Assert.assertEquals("Incorrect number of invariants", 2, invariants.size)
        Assert.assertTrue("Invariants does not contain required element: $invariants", invariants.any { it.name == c.name })
        Assert.assertTrue("Invariants does not contain required element: $invariants", invariants.any { it.name == d.name })

        val candidates = mapOf(
                Invariant(c.name) to And(
                        IntExprs.Gt(x.ref, IntExprs.Int(0)),
                        IntExprs.Geq(y.ref, IntExprs.Int(0))
                ),
                Invariant(d.name) to IntExprs.Gt(y.ref, IntExprs.Int(0)))

        val solver = Z3SolverFactory.getInstance().createSolver()
        for (chc in chcs) {
            solver.push()
            solver.addCHC(chc, GenericCandidates(True(), candidates))
            Assert.assertEquals("Simple CHC check error: $chc", SolverStatus.UNSAT, solver.check())
            solver.pop()
        }

    }
}