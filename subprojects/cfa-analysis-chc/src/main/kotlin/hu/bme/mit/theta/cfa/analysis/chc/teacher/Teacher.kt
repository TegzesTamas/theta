package hu.bme.mit.theta.cfa.analysis.chc.teacher

import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.InvariantCandidates
import hu.bme.mit.theta.cfa.analysis.chc.addCHC
import hu.bme.mit.theta.cfa.analysis.chc.decisiontree.Constraint
import hu.bme.mit.theta.cfa.analysis.chc.decisiontree.Datapoint
import hu.bme.mit.theta.cfa.analysis.chc.decisiontree.DecisionTree
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.SolverStatus.UNSAT
import hu.bme.mit.theta.solver.utils.WithPushPop

fun findInvariantsFor(chcSystem: CHCSystem, solver: Solver): InvariantCandidates {
    var datapoints: Set<Datapoint> = setOf()
    var constraints: List<Constraint> = listOf()
    do {
        val decTree = DecisionTree(datapoints, constraints)
        val nextDatapoints = datapoints.toMutableSet()
        val nextConstraints = constraints.toMutableList()
        val candidates = decTree.candidates
        for (chc in chcSystem.chcs) {
            WithPushPop(solver).use {
                solver.addCHC(chc, candidates)
                if (solver.check() == UNSAT) {
                    val model = solver.model
                    val (preDatapoint, postDatapoint) = chc.datapoints(model)
                    val source: List<Datapoint>
                    if (preDatapoint != null) {
                        nextDatapoints += preDatapoint
                        source = listOf(preDatapoint)
                    } else {
                        source = emptyList()
                    }
                    postDatapoint?.let {
                        nextDatapoints += it
                    }
                    nextConstraints += Constraint(source, postDatapoint)
                }
            }
        }
        datapoints = nextDatapoints
        constraints = nextConstraints
    } while (true)
}