package hu.bme.mit.theta.cfa.analysis.chc.teacher

import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.DEBUG
import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.addCHC
import hu.bme.mit.theta.cfa.analysis.chc.learner.DecisionTreeLearner
import hu.bme.mit.theta.cfa.analysis.chc.learner.Learner
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.Constraint
import hu.bme.mit.theta.cfa.analysis.chc.learner.constraint.ConstraintSystem
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.SolverStatus.SAT
import hu.bme.mit.theta.solver.utils.WithPushPop

fun findInvariantsFor(chcSystem: CHCSystem, solver: Solver): InvariantCandidates {
    val constraintSystemBuilder = ConstraintSystem.Builder()
    var candidates: InvariantCandidates
    val atoms = mutableSetOf<Expr<BoolType>>()
    for (chc in chcSystem.chcs) {
        ExprUtils.collectAtoms(chc.body, atoms)
    }
    val learner: Learner = DecisionTreeLearner(atoms)
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
        candidates = learner.suggestCandidates(constraintSystem)
        if (DEBUG) println("Found candidates: $candidates")
        if (DEBUG) println()
        for (chc in chcSystem.chcs) {
            WithPushPop(solver).use {
                solver.addCHC(chc, candidates)
                if (solver.check() == SAT) {
                    if (DEBUG) println("Unsatisfiable CHC: $chc")
                    allUnsat = false
                    val model = solver.model
                    val (source, target) = chc.datapoints(model)
                    val newConstraint = Constraint(source, target, chc)
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