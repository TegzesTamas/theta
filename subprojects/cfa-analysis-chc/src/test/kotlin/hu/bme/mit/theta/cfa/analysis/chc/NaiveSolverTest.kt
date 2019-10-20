package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.stmt.AssignStmt
import hu.bme.mit.theta.core.stmt.AssumeStmt
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntNeqExpr
import hu.bme.mit.theta.core.utils.StmtUtils
import hu.bme.mit.theta.core.utils.VarIndexing
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.SolverStatus
import hu.bme.mit.theta.solver.utils.WithPushPop
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Assert
import org.junit.Test

class NaiveSolverTest {
    private val solver: Solver = Z3SolverFactory.getInstace().createSolver()
    @Test(timeout = 5000)
    fun topDownUnsafeTest() {
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
        val chcSystem = cfaToChc(cfa)
        Assert.assertNotNull(chcSystem.findFailingPath(solver))
    }

    @Test(timeout = 500)
    fun topDownLooplessSafeTest() {
        val chcSystem = CHCSystem(
                simpleCHCs = listOf(SimpleCHC(AndExpr.of(listOf(BoolExprs.False())), VarIndexing.all(0)))
        )
        Assert.assertNull(chcSystem.findFailingPath(solver))
    }

    @Test(timeout = 30000)
    fun topDownMultiLoopUnsafeTest() {
        val invA = Invariant("loop_a")
        val invB = Invariant("loop_b")
        val invC = Invariant("loop_c")

        val x = DeclManager.getVar("x", IntExprs.Int())
        val i = DeclManager.getVar("i", IntExprs.Int())
        val j = DeclManager.getVar("j", IntExprs.Int())

        val jEq0AndXEq0 = StmtUtils.toExpr(listOf(AssignStmt.of(j, IntExprs.Int(0)), AssignStmt.of(x, IntExprs.Int(0))), VarIndexing.all(0))
        val xPlusPlus = StmtUtils.toExpr(AssignStmt.of(x, IntExprs.Add(IntExprs.Int(1), x.ref)), VarIndexing.all(0))
        val iEqX = StmtUtils.toExpr(AssignStmt.of(i, x.ref), VarIndexing.all(0))
        val iMinusMinus = StmtUtils.toExpr(AssignStmt.of(i, IntExprs.Add(i.ref, IntExprs.Int(-1))), VarIndexing.all(0))
        val jPlusEqI = StmtUtils.toExpr(AssignStmt.of(j, IntExprs.Add(j.ref, i.ref)), VarIndexing.all(0))
        val jGreaterThanXTimesI = StmtUtils.toExpr(AssumeStmt.of(IntExprs.Lt(IntExprs.Mul(x.ref, i.ref), j.ref)), VarIndexing.all(0))
        val jEq5000 = StmtUtils.toExpr(AssumeStmt.of(IntExprs.Eq(j.ref, IntExprs.Int(50))), VarIndexing.all(0))

        val chcSystem = CHCSystem(
                facts = listOf(Fact(AndExpr.of(jEq0AndXEq0.exprs), jEq0AndXEq0.indexing, invA)),
                inductiveClauses = listOf(
                        InductiveClause(invA, AndExpr.of(xPlusPlus.exprs), xPlusPlus.indexing, invA),
                        InductiveClause(invA, AndExpr.of(iEqX.exprs), iEqX.indexing, invB),
                        InductiveClause(invB, AndExpr.of(iMinusMinus.exprs), iMinusMinus.indexing, invC),
                        InductiveClause(invC, AndExpr.of(jPlusEqI.exprs), jPlusEqI.indexing, invC),
                        InductiveClause(invC, AndExpr.of(jGreaterThanXTimesI.exprs), jGreaterThanXTimesI.indexing, invB)
                ),
                queries = listOf(Query(invB, AndExpr.of(jEq5000.exprs), jEq5000.indexing))
        )
        val findFailingPath = chcSystem.findFailingPath(solver)
        Assert.assertNotNull(findFailingPath)
        findFailingPath?.let { chc ->
            WithPushPop(solver).use {
                solver.addSimpleCHC(chc)
                Assert.assertEquals("Invalid failing path", SolverStatus.SAT, solver.check())
            }
        }
    }
}