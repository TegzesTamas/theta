package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.coordinator.SimpleCoordinator
import hu.bme.mit.theta.cfa.analysis.chc.learner.DecisionTreeLearner
import hu.bme.mit.theta.cfa.analysis.chc.learner.RoundRobinLearner
import hu.bme.mit.theta.cfa.analysis.chc.learner.SorcarLearner
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.LeqPattern
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.ListPattern
import hu.bme.mit.theta.cfa.analysis.chc.teacher.SimpleTeacher
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.cfa.analysis.chc.utilities.removePrimes
import hu.bme.mit.theta.core.stmt.Stmts.*
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.And
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Or
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.inttype.IntExprs.*
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.SolverStatus
import hu.bme.mit.theta.solver.utils.WithPushPop
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Assert
import org.junit.Before
import org.junit.Test


class TeacherLearnerTest {
    private lateinit var solver: Solver

    private fun findInvariantsFor(chcSystem: CHCSystem, solver: Solver): InvariantCandidates {
        val teacher = SimpleTeacher(solver)
        val atoms = mutableSetOf<Expr<BoolType>>()
        for (chc in chcSystem.chcs) {
            val rawAtoms = mutableListOf<Expr<BoolType>>()
            ExprUtils.collectAtoms(chc.body, rawAtoms)
            rawAtoms.mapTo(atoms) { removePrimes(it) }
        }
        val sorcarLearner = SorcarLearner(ListPattern(atoms))
        val dtLearner = DecisionTreeLearner(predicatePatterns = listOf(LeqPattern, ListPattern(atoms)))
        val learner = RoundRobinLearner(listOf(sorcarLearner, dtLearner))
        val coordinator = SimpleCoordinator(teacher, learner)
        return coordinator.solveCHCSystem(chcSystem)
    }

    @Before
    fun before() {
        solver = Z3SolverFactory.getInstance().createSolver()
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

    @Test(expected = ContradictoryException::class)
    fun unsplittableDatapointsTest() {
        val cfaBuilder = CFA.builder()

        val initL = cfaBuilder.createLoc("init")
        cfaBuilder.initLoc = initL
        val initX = cfaBuilder.createLoc("initX")
        val initY = cfaBuilder.createLoc("initY")
        val l1 = cfaBuilder.createLoc("Loop_1")
        val initZ = cfaBuilder.createLoc("initZ")
        val l2 = cfaBuilder.createLoc("Loop_2")
        val errorLoc = cfaBuilder.createLoc("error")
        cfaBuilder.errorLoc = errorLoc
        val finalLoc = cfaBuilder.createLoc("final")
        cfaBuilder.finalLoc = finalLoc

        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())
        val z = DeclManager.getVar("z", Int())


        cfaBuilder.createEdge(initL, initX, Assign(x, Int(2)))
        cfaBuilder.createEdge(initX, initY, Assign(y, Int(2)))
        cfaBuilder.createEdge(initY, l1, Skip())
        cfaBuilder.createEdge(l1, l1, Skip())
        cfaBuilder.createEdge(l1, initZ, Assign(z, x.ref))
        cfaBuilder.createEdge(initZ, l2, Skip())
        cfaBuilder.createEdge(l2, l2, Skip())
        cfaBuilder.createEdge(l2, errorLoc, Assume(And(Eq(x.ref, Int(2)), Eq(y.ref, Int(2)), Eq(z.ref, Int(2)))))
        cfaBuilder.createEdge(l2, finalLoc, Assume(Or(Neq(x.ref, Int(2)), Neq(y.ref, Int(2)), Neq(z.ref, Int(2)))))

        val cfa = cfaBuilder.build()
        val chcSystem = cfaToChc(cfa)

        findInvariantsFor(chcSystem, solver)
    }
}