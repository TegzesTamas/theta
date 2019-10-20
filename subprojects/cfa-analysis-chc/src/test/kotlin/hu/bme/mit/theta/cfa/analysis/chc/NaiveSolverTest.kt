package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.stmt.AssignStmt
import hu.bme.mit.theta.core.stmt.AssumeStmt
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntNeqExpr
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Assert
import org.junit.Test

class NaiveSolverTest() {
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
}