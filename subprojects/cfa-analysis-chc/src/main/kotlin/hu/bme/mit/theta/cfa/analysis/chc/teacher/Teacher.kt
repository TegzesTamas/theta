package hu.bme.mit.theta.cfa.analysis.chc.teacher

import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.addCHC
import hu.bme.mit.theta.cfa.analysis.chc.constraint.Constraint
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.SolverStatus.SAT
import hu.bme.mit.theta.solver.utils.WithPushPop

interface Teacher {
    fun checkCandidates(chcSystem: CHCSystem, candidates: InvariantCandidates): Iterable<Constraint>?
}

class SimpleTeacher(private val solver: Solver) : Teacher {
    override fun checkCandidates(chcSystem: CHCSystem, candidates: InvariantCandidates): List<Constraint>? {
        val constraints = mutableListOf<Constraint>()
        for (chc in chcSystem.chcs) {
            WithPushPop(solver).use {
                solver.addCHC(chc, candidates)
                if (solver.check() == SAT) {
                    val model = solver.model
                    val (source, target) = chc.datapoints(model)
                    val newConstraint = Constraint(source, target, chc)
                    constraints.add(newConstraint)
                }
            }
        }
        return if (constraints.isNotEmpty()) {
            constraints
        } else {
            null
        }
    }
}