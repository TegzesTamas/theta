package hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree

import kotlin.math.min

interface ImpurityMeasure {
    /**
     * Assigns a number to how bad a set of datapoints, worse sets get larger numbers.
     * A set of datapoints is considered good if most of the datapoints can be labeled the same.
     * @param mustBeTrue The number of datapoints that must be labeled true
     * @param mustBeFalse The number of datapoints that must be labeled false
     * @param total The total number of datapoints
     */
    fun impurity(mustBeTrue: Int, mustBeFalse: Int, total: Int): Double
}

object ClassificationError : ImpurityMeasure {
    override fun impurity(mustBeTrue: Int, mustBeFalse: Int, total: Int): Double {
        return min(mustBeTrue, mustBeFalse) + (total - mustBeTrue - mustBeFalse) / 2.0
    }
}

object NullError : ImpurityMeasure {
    override fun impurity(mustBeTrue: Int, mustBeFalse: Int, total: Int): Double = 0.0
}