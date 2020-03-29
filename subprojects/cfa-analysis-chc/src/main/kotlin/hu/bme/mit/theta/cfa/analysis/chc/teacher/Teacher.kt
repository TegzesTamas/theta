package hu.bme.mit.theta.cfa.analysis.chc.teacher

import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.DEBUG
import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.addCHC
import hu.bme.mit.theta.cfa.analysis.chc.learner.Constraint
import hu.bme.mit.theta.cfa.analysis.chc.learner.ConstraintSystem
import hu.bme.mit.theta.cfa.analysis.chc.learner.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.learner.DecisionTreeBuilder
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.SolverStatus.SAT
import hu.bme.mit.theta.solver.utils.WithPushPop

fun findInvariantsFor(chcSystem: CHCSystem, solver: Solver): InvariantCandidates {
    val constraintSystemBuilder = ConstraintSystem.Builder()
    var candidates: InvariantCandidates
    do {
        val constraintSystem = constraintSystemBuilder.build()
        var allUnsat = true
        if (DEBUG) {
            println()
            println("*** Trying to find candidates ***")
            for (constraint in constraintSystem.constraints) {
                println("\t\t$constraint")
            }
            println("*** END OF CONSTRAINTS ***")
        }
        val decTree = DecisionTreeBuilder(constraintSystem).build()
        candidates = decTree.candidates
        if (DEBUG) println("Found candidates: $candidates")
        if (DEBUG) println()
        for (chc in chcSystem.chcs) {
            WithPushPop(solver).use {
                solver.addCHC(chc, candidates)
                if (solver.check() == SAT) {
                    if (DEBUG) println("Unsatisfiable CHC: $chc")
                    allUnsat = false
                    val model = solver.model
                    val (preDatapoint, postDatapoint) = chc.datapoints(model)
                    val source: List<Datapoint>
                    if (preDatapoint != null) {
                        source = listOf(preDatapoint)
                    } else {
                        source = emptyList()
                    }
                    val newConstraint = Constraint(source, postDatapoint)
                    if (DEBUG) {
                        println("Adding Constraint: $newConstraint")
                        println()
                    }
                    constraintSystemBuilder.addConstraint(newConstraint)
                }
            }
        }
    } while (!allUnsat)
    if (DEBUG) println("Everything unsatisfiable")
    return candidates
}