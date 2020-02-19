package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.learner.DecisionTree
import hu.bme.mit.theta.cfa.analysis.chc.teacher.findInvariantsFor
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.stmt.AssignStmt
import hu.bme.mit.theta.core.stmt.AssumeStmt
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntNeqExpr
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.SolverStatus
import hu.bme.mit.theta.solver.utils.WithPushPop
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class TeacherLearnerTest {
    var solver: Solver = Z3SolverFactory.getInstace().createSolver()

    @Before
    fun before() {
        solver = Z3SolverFactory.getInstace().createSolver()
    }

    @Test(timeout = 10000)
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

        val chcsystem = cfaToChc(cfa)

        val invariantCandidates = findInvariantsFor(chcsystem, solver)

        for (chc in chcsystem.chcs) {
            WithPushPop(solver).use {
                solver.addCHC(chc, invariantCandidates)
                Assert.assertEquals(SolverStatus.UNSAT, solver.check())
            }
        }
    }

    @Test(timeout = 10000)
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

        val chcsystem = cfaToChc(cfa)

        val invariantCandidates = findInvariantsFor(chcsystem, solver)

        for (chc in chcsystem.chcs) {
            WithPushPop(solver).use {
                solver.addCHC(chc, invariantCandidates)
                Assert.assertEquals(SolverStatus.UNSAT, solver.check())
            }
        }
    }

    @Test(timeout = 10000, expected = DecisionTree.ContradictoryException::class)
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

        val chcsystem = cfaToChc(cfa)

        findInvariantsFor(chcsystem, solver)
        error("Unsafe CFA found safe")
    }

    @Test(timeout = 20000)
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

        val invariantCandidates = findInvariantsFor(chcSystem, solver)

        for (chc in chcSystem.chcs) {
            WithPushPop(solver).use {
                solver.addCHC(chc, invariantCandidates)
                Assert.assertEquals(SolverStatus.UNSAT, solver.check())
            }
        }
    }
}