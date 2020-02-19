package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.learner.DecisionTree
import hu.bme.mit.theta.cfa.analysis.chc.teacher.findInvariantsFor
import hu.bme.mit.theta.cfa.dsl.CfaDslManager
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import java.io.FileInputStream

fun main(args: Array<String>) {
    val model = args[0]
    var cfa: CFA? = null
    FileInputStream(model).use {
        cfa = CfaDslManager.createCfa(it)
    }
    cfa?.let {
        val chcSystem = cfaToChc(it)
        val solver = Z3SolverFactory.getInstace().createSolver()
        try {
            findInvariantsFor(chcSystem, solver)
            println("SAFE")
        } catch (e: DecisionTree.ContradictoryException) {
            println("UNSAFE")
        }
    } ?: error("Could not parse model")
}