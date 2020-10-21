package hu.bme.mit.theta.cfa.chc.cli

import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.coordinator.Coordinator
import hu.bme.mit.theta.cfa.analysis.chc.coordinator.MultiThreadedCoordinator
import hu.bme.mit.theta.cfa.analysis.chc.coordinator.SimpleCoordinator
import hu.bme.mit.theta.cfa.analysis.chc.learner.*
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ClassificationError
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ImpurityMeasure
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.LeqPattern
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.ListPattern
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.ModulusPattern
import hu.bme.mit.theta.cfa.analysis.chc.learner.predicates.PredicatePattern
import hu.bme.mit.theta.cfa.analysis.chc.teacher.Teacher
import hu.bme.mit.theta.cfa.analysis.chc.utilities.getExprsOfSubType
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import hu.bme.mit.theta.core.utils.ExprUtils

interface LearnerType {
    fun create(learners: Collection<Learner>, measure: ImpurityMeasure, patterns: Collection<PredicatePattern>): Learner
}

enum class StandaloneLearnerType : LearnerType {

    Sorcar {
        override fun create(measure: ImpurityMeasure, patterns: Collection<PredicatePattern>) = SorcarLearner(patterns)
    },
    DecisionTree {
        override fun create(measure: ImpurityMeasure,
                            patterns: Collection<PredicatePattern>) = DecisionTreeLearner(measure = measure, predicatePatterns = patterns)
    };


    abstract fun create(measure: ImpurityMeasure, patterns: Collection<PredicatePattern>): Learner
    override fun create(learners: Collection<Learner>,
                        measure: ImpurityMeasure,
                        patterns: Collection<PredicatePattern>) = create(measure, patterns)
}

enum class LearnerCombination : LearnerType {
    RoundRobin {
        override fun create(learners: Collection<Learner>) = RoundRobinLearner(learners)
    },

    Fallback {
        override fun create(learners: Collection<Learner>): Learner = FallbackLearner(learners)
    };

    abstract fun create(learners: Collection<Learner>): Learner
    override fun create(learners: Collection<Learner>,
                        measure: ImpurityMeasure,
                        patterns: Collection<PredicatePattern>): Learner = create(learners)
}

enum class ImpurityMeasureType {
    CE {
        override fun create() = ClassificationError
    };

    abstract fun create(): ImpurityMeasure
}

enum class PredicatePatternType {
    IntLEQ {
        fun create() = LeqPattern
        override fun create(chcs: CHCSystem) = LeqPattern
    },
    Atoms {
        override fun create(chcs: CHCSystem): ListPattern {
            val atoms = mutableSetOf<Expr<BoolType>>()
            for (chc in chcs.chcs) {
                ExprUtils.collectAtoms(chc.body, atoms)
            }
            return ListPattern(atoms)
        }
    },
    Modulus {
        override fun create(chcs: CHCSystem): PredicatePattern {
            val intExprs = getExprsOfSubType(Int(), chcs.chcs.map { it.body })
            return ModulusPattern(intExprs)
        }

    };

    abstract fun create(chcs: CHCSystem): PredicatePattern
}

enum class CoordinatorType {
    MultiThreaded {
        override fun create(teachers: Collection<Teacher>, learners: Collection<Learner>) =
                MultiThreadedCoordinator(learners, teachers)
    },
    Simple {
        override fun create(teachers: Collection<Teacher>, learners: Collection<Learner>): Coordinator {
            require(teachers.size == 1) { "Simple coordinator can only handle a single teacher" }
            require(learners.size == 1) { "Simple coordinator can only handle a single learner" }
            return SimpleCoordinator(teachers.single(), learners.single())
        }
    };

    abstract fun create(teachers: Collection<Teacher>, learners: Collection<Learner>): Coordinator
}
