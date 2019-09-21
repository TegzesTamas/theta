package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.stmt.AssignStmt
import hu.bme.mit.theta.core.stmt.AssumeStmt
import hu.bme.mit.theta.core.stmt.Stmts
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntNeqExpr
import hu.bme.mit.theta.core.utils.PathUtils
import hu.bme.mit.theta.solver.SolverStatus
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Assert
import org.junit.Test

class CfaToChcTest {

    @Test
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

        val solver = Z3SolverFactory.getInstace().createSolver()
        solver.push()
        solver.add(PathUtils.unfold(chcs.first().expr, 0))
        Assert.assertEquals("CHC should be unsatisfiable, but it is satisfiable: ${chcs.first()}", SolverStatus.UNSAT, solver.check())
        solver.pop()
    }

    @Test
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

        val solver = Z3SolverFactory.getInstace().createSolver()
        for (chc in chcs) {
            solver.push()
            solver.add(PathUtils.unfold(chc.expr, 0))
            Assert.assertEquals("CHC should be unsatisfiable, but it is satisfiable: $chc", SolverStatus.UNSAT, solver.check())
            solver.pop()
        }
    }

    @Test
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

        val solver = Z3SolverFactory.getInstace().createSolver()
        var satCount = 0
        var unsatCount = 0
        for (chc in chcs) {
            solver.push()
            solver.add(PathUtils.unfold(chc.expr, 0))
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

    @Test
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


        val solver = Z3SolverFactory.getInstace().createSolver()
        for (chc in chcs) {
            solver.push()
            solver.add(PathUtils.unfold(chc.expr, 0))
            Assert.assertEquals("CHC unsatisfiable with unbound invariant: $chc", SolverStatus.SAT, solver.check())
            solver.pop()
        }

        Assert.assertEquals("Incorrect number of chcs: $chcs", 3, chcs.size)
        val invariants = chcSystem.invariants

        Assert.assertEquals("Incorrect number of loop invariants: $invariants", 1, invariants.size)
        val invariantExpr = IntExprs.Eq(x.ref, IntExprs.Int(0))
        for (chc in chcs) {
            solver.push()
            for (invariant in invariants) {
                solver.add(BoolExprs.Iff(invariant.getConstDecl(0).ref, PathUtils.unfold(invariantExpr, 0)))
                solver.add(BoolExprs.Iff(invariant.getConstDecl(1).ref, PathUtils.unfold(invariantExpr, chc.postIndexing)))
            }
            solver.add(PathUtils.unfold(chc.expr, 0))
            Assert.assertEquals("CHC satisfiable with bound invariant that should prove safety", SolverStatus.UNSAT, solver.check())
            solver.pop()
        }
    }

    @Test
    fun multiplePathsMultipleLoopsSafeTest() {
        val cfaBuilder = CFA.builder()
        val initLoc = cfaBuilder.createLoc("init")
        cfaBuilder.initLoc = initLoc
        val errorLoc = cfaBuilder.createLoc("error")
        cfaBuilder.finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.errorLoc = errorLoc

        val e = cfaBuilder.createLoc("e")
        val a = cfaBuilder.createLoc("a")
        val b = cfaBuilder.createLoc("b")
        val c = cfaBuilder.createLoc("c")
        val d = cfaBuilder.createLoc("d")

        cfaBuilder.createEdge(initLoc, e, Stmts.Skip())
        cfaBuilder.createEdge(e, a, Stmts.Skip())
        cfaBuilder.createEdge(a, c, Stmts.Skip())
        cfaBuilder.createEdge(c, c, Stmts.Skip())
        cfaBuilder.createEdge(c, d, Stmts.Skip())
        cfaBuilder.createEdge(e, b, Stmts.Skip())
        cfaBuilder.createEdge(b, d, Stmts.Skip())
        cfaBuilder.createEdge(d, e, Stmts.Skip())
        cfaBuilder.createEdge(d, errorLoc, Stmts.Assume(BoolExprs.False()))

        val cfa = cfaBuilder.build()
        val chcSystem = cfaToChc(cfa)
        val chcs = chcSystem.chcs
        Assert.assertEquals("Incorrect number of chcs", 7, chcs.size)

        val invariants = chcs.flatMap { it.invariantsToFind }.toSet()
        Assert.assertEquals("Incorrect number of invariants", 2, invariants.size)

    }
}