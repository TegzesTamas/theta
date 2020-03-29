package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.teacher.findInvariantsFor
import hu.bme.mit.theta.cfa.dsl.CfaDslManager
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import java.io.FileInputStream

public const val DEBUG = true

fun main(args: Array<String>) {
    val model = args[0]
    var cfa: CFA? = null
    FileInputStream(model).use {
        cfa = CfaDslManager.createCfa(it)
    }
    cfa?.let {
        if (DEBUG) println(cfa)
        val chcSystem = cfaToChc(it)
        if (DEBUG) {
            println("********* CHC SYSTEM ***********")
            println("INVARIANTS : ${chcSystem.invariants}")
            println()
            println("CHCS: ")
            for (chc in chcSystem.chcs) {
                println("\t $chc")
            }
            println()
        }
        val solver = Z3SolverFactory.getInstace().createSolver()
        try {
            findInvariantsFor(chcSystem, solver)
            println("SAFE")
        } catch (e: ContradictoryException) {
            println("UNSAFE")
        }
    } ?: error("Could not parse model")
}