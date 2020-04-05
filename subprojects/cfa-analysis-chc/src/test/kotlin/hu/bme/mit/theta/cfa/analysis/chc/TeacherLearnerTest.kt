package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.teacher.findInvariantsFor
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.stmt.Stmts.*
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Or
import hu.bme.mit.theta.core.type.inttype.IntExprs.*
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
        cfaBuilder.errorLoc = errorLoc
        val finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.finalLoc = finalLoc

        val midLoc = cfaBuilder.createLoc("middle")

        val x = DeclManager.getVar("x", Int())

        cfaBuilder.createEdge(initLoc, midLoc, Assign(x, Int(0)))
        cfaBuilder.createEdge(midLoc, errorLoc, Assume(Neq(x.ref, Int(0))))
        cfaBuilder.createEdge(midLoc, finalLoc, Assume(Eq(x.ref, Int(0))))

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

    @Test(timeout = 10000)
    fun multiPathLooplessSafeTest() {
        val cfaBuilder = CFA.builder()
        val initLoc = cfaBuilder.createLoc("init")
        cfaBuilder.initLoc = initLoc
        val errorLoc = cfaBuilder.createLoc("error")
        cfaBuilder.errorLoc = errorLoc
        val finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.finalLoc = finalLoc

        val leftLoc = cfaBuilder.createLoc("left")
        val rightLoc = cfaBuilder.createLoc("right")

        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())


        cfaBuilder.createEdge(initLoc, leftLoc, Assign(x, Int(0)))
        cfaBuilder.createEdge(leftLoc, errorLoc, Assume(Neq(x.ref, Int(0))))
        cfaBuilder.createEdge(leftLoc, finalLoc, Assume(Eq(x.ref, Int(0))))

        cfaBuilder.createEdge(initLoc, rightLoc, Assign(y, Int(0)))
        cfaBuilder.createEdge(rightLoc, errorLoc, Assume(Neq(y.ref, Int(0))))
        cfaBuilder.createEdge(rightLoc, finalLoc, Assume(Eq(y.ref, Int(0))))


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

    @Test(timeout = 10000, expected = ContradictoryException::class)
    fun multiPathLooplessUnsafeTest() {

        val cfaBuilder = CFA.builder()
        val initLoc = cfaBuilder.createLoc("init")
        cfaBuilder.initLoc = initLoc
        val errorLoc = cfaBuilder.createLoc("error")
        cfaBuilder.errorLoc = errorLoc
        val finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.finalLoc = finalLoc

        val midLoc = cfaBuilder.createLoc("middle")

        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())


        cfaBuilder.createEdge(initLoc, midLoc, Assign(x, Int(0)))
        cfaBuilder.createEdge(midLoc, errorLoc, Assume(Neq(x.ref, Int(0))))

        cfaBuilder.createEdge(initLoc, midLoc, Assign(y, Int(0)))
        cfaBuilder.createEdge(midLoc, errorLoc, Assume(Neq(y.ref, Int(0))))

        cfaBuilder.createEdge(midLoc, finalLoc, Assume(And(Eq(x.ref, Int(0)), Eq(y.ref, Int(0)))))


        val cfa = cfaBuilder.build()

        val chcSystem = cfaToChc(cfa)

        findInvariantsFor(chcSystem, solver)
        error("Unsafe CFA found safe")
    }

    @Test(timeout = 20000)
    fun singlePathSingleLoopSafeTest() {
        val cfaBuilder = CFA.builder()
        val initLoc = cfaBuilder.createLoc("init")
        cfaBuilder.initLoc = initLoc
        val errorLoc = cfaBuilder.createLoc("error")
        cfaBuilder.errorLoc = errorLoc
        val finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.finalLoc = finalLoc

        val midLoc = cfaBuilder.createLoc("middle")

        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())

        cfaBuilder.createEdge(initLoc, midLoc, Assign(x, Int(0)))
        cfaBuilder.createEdge(midLoc, errorLoc, Assume(Neq(x.ref, Int(0))))
        cfaBuilder.createEdge(midLoc, midLoc, Assign(y, Add(y.ref, Int(1))))
        cfaBuilder.createEdge(midLoc, finalLoc, Assume(Lt(y.ref, Int(0))))

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