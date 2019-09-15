package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.stmt.AssignStmt
import hu.bme.mit.theta.core.stmt.AssumeStmt
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntNeqExpr
import hu.bme.mit.theta.core.utils.PathUtils
import hu.bme.mit.theta.solver.SolverStatus
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import org.junit.Assert
import org.junit.Test

class HelloTest {

    @Test
    fun LooplessProgramTest() {
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

        val chcs = cfaToChc(cfa)
        Assert.assertEquals("Too many chcs: $chcs", 1, chcs.size)

        val solver = Z3SolverFactory.getInstace().createSolver()
        solver.push()
        solver.add(PathUtils.unfold(chcs[0].getExpr(), 0))
        Assert.assertEquals("CHC should be unsatisfiable, but it is satisfiable: ${chcs[0]}", SolverStatus.UNSAT, solver.check())
        solver.pop()
    }
}