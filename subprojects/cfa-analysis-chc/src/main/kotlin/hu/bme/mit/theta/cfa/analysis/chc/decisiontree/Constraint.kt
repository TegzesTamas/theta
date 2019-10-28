package hu.bme.mit.theta.cfa.analysis.chc.decisiontree

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.core.model.Valuation

data class Constraint(val source: List<Datapoint>, val target: Datapoint) {

}

data class Datapoint(val invariant: Invariant, val valuation: Valuation)