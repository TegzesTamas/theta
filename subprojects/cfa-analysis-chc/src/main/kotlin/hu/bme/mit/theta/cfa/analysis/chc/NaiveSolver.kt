package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.SolverStatus
import hu.bme.mit.theta.solver.utils.WithPushPop
import java.util.*

fun CHCSystem.findFailingPath(solver: Solver): SimpleCHC? {
    for (simpleCHC in simpleCHCs) {
        WithPushPop(solver).use {
            solver.addSimpleCHC(simpleCHC)
            if (solver.check() == SolverStatus.SAT) {
                return simpleCHC
            }
        }
    }
    val chcs = LinkedList<Query>(queries)
    while (chcs.isNotEmpty()) {
        val chc = chcs.pop()
        val toCheck = facts.filter { it.postInvariant == chc.preInvariant }.map { it.append(chc) }
        for (simpleCHC in toCheck) {
            val res = WithPushPop(solver).use {
                solver.addSimpleCHC(simpleCHC)
                solver.check()
            }
            if (res == SolverStatus.SAT) {
                return simpleCHC
            }
        }
        chcs += inductiveClauses.filter { it.postInvariant == chc.preInvariant }.map { it.append(chc) }
    }
    return null
}