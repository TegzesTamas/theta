package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.teacher.findInvariantsFor
import hu.bme.mit.theta.cfa.dsl.CfaDslManager
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import java.io.FileInputStream

const val DEBUG = false

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
            println("*********** CHC SYSTEM ***********")
            println("INVARIANTS : ${chcSystem.invariants}")
            println()
            println("CHCS: ")
            for (chc in chcSystem.chcs) {
                println("\t $chc")
            }
            println()
        }
        val solver = Z3SolverFactory.getInstance().createSolver()
        try {
            val invariants = findInvariantsFor(chcSystem, solver)
            println("SAFE, \"$invariants\"")
        } catch (e: ContradictoryException) {
            if (DEBUG) {
                println("*********** Contradictory constraints ***********")
                println(e.contradictorySubset)
            }
            println("UNSAFE, \"${e.contradictorySubset}\"")
        }
    } ?: error("Could not parse model")
}