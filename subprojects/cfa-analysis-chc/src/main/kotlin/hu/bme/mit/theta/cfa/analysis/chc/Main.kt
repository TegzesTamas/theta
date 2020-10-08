package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.coordinator.SimpleCoordinator
import hu.bme.mit.theta.cfa.analysis.chc.learner.DecisionTreeLearner
import hu.bme.mit.theta.cfa.analysis.chc.learner.RoundRobinLearner
import hu.bme.mit.theta.cfa.analysis.chc.learner.SorcarLearner
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.predicates.LeqPattern
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.predicates.ListPattern
import hu.bme.mit.theta.cfa.analysis.chc.teacher.SimpleTeacher
import hu.bme.mit.theta.cfa.analysis.chc.utilities.removePrimes
import hu.bme.mit.theta.cfa.dsl.CfaDslManager
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprUtils
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
        val teacher = SimpleTeacher(solver)
        val atoms = mutableSetOf<Expr<BoolType>>()
        for (chc in chcSystem.chcs) {
            val rawAtoms = mutableListOf<Expr<BoolType>>()
            ExprUtils.collectAtoms(chc.body, rawAtoms)
            rawAtoms.mapTo(atoms) { removePrimes(it) }
        }
        val sorcarLearner = SorcarLearner(atoms)
        val dtLearner = DecisionTreeLearner(predicatePatterns = listOf(LeqPattern, ListPattern(atoms)))
        val learner = RoundRobinLearner(listOf(sorcarLearner, dtLearner))
        val coordinator = SimpleCoordinator(teacher, learner)
        try {
            val invariants = coordinator.solveCHCSystem(chcSystem)
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