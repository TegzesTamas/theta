package hu.bme.mit.theta.cfa.chc.cli

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.cfaToChc
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.coordinator.SimpleCoordinator
import hu.bme.mit.theta.cfa.analysis.chc.learner.DecisionTreeLearner
import hu.bme.mit.theta.cfa.analysis.chc.learner.Learner
import hu.bme.mit.theta.cfa.analysis.chc.learner.RoundRobinLearner
import hu.bme.mit.theta.cfa.analysis.chc.learner.SorcarLearner
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ClassificationError
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.ImpurityMeasure
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.predicates.LeqPattern
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.predicates.ListPattern
import hu.bme.mit.theta.cfa.analysis.chc.learner.decisiontree.predicates.PredicatePattern
import hu.bme.mit.theta.cfa.analysis.chc.teacher.SimpleTeacher
import hu.bme.mit.theta.cfa.analysis.chc.utilities.removePrimes
import hu.bme.mit.theta.cfa.dsl.CfaDslManager
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import java.io.FileInputStream
import kotlin.system.exitProcess

enum class LearnerType {

    Sorcar {
        override fun create(measure: ImpurityMeasure) = SorcarLearner(emptySet())
    },
    DecisionTree {
        override fun create(measure: ImpurityMeasure) = DecisionTreeLearner(measure = measure)
    };


    abstract fun create(measure: ImpurityMeasure): Learner
}

enum class LearnerCombination {
    RoundRobin {
        override fun create(learners: Iterable<Learner>) = RoundRobinLearner(learners)
    };

    abstract fun create(learners: Iterable<Learner>): Learner
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
            val atoms = chcs.chcs.flatMapTo(mutableSetOf()) { chc ->
                ExprUtils.getAtoms(chc.body).map { removePrimes(it) }
            }
            return ListPattern(atoms)
        }
    };

    abstract fun create(chcs: CHCSystem): PredicatePattern
}

private class Arguments {
    @Parameter(names = ["--help"], description = "Print usage information", help = true)
    var help: Boolean = false

    @Parameter(names = ["--learners", "-l"], description = "Learner to use", variableArity = true)
    var learners: MutableList<LearnerType> = mutableListOf(LearnerType.DecisionTree)

    @Parameter(names = ["--learnerCombination", "-c"], description = "Combination strategy to use with multiple learners")
    var combination: LearnerCombination = LearnerCombination.RoundRobin

    @Parameter(names = ["--impurityMeasure", "-i"], description = "Impurity measure to use when building a decision tree")
    var measure: ImpurityMeasureType = ImpurityMeasureType.CE

    @Parameter(names = ["--predicatePatterns", "-p"], description = "Strategy to use when building predicates")
    var predicatePatterns: MutableList<PredicatePatternType> = mutableListOf(PredicatePatternType.Atoms, PredicatePatternType.IntLEQ)

    @Parameter(description = "<model.cfa>", required = true)
    var cfaFile: String = ""
}

object CfaChcCli {
    private const val JAR_NAME = "theta-cfa-chc-cli.jar"

    @JvmStatic
    fun main(args: Array<String>) {
        val arguments = Arguments()
        val jCommander = JCommander.newBuilder()
                .addObject(arguments)
                .programName(JAR_NAME)
                .acceptUnknownOptions(false)
                .build()
        try {
            jCommander.parse(*args)
        } catch (e: ParameterException) {
            println(e.message)
            e.usage()
            exitProcess(1)
        }
        if (arguments.help) {
            jCommander.usage()
            return
        }
        println(arguments.help)
        println(arguments.combination)
        println(arguments.learners)
        println(arguments.measure)
        println(arguments.predicatePatterns)
        val teacher = SimpleTeacher(Z3SolverFactory.getInstance().createSolver())
        val impurityMeasure = arguments.measure.create()
        val learners = arguments.learners.map { it.create(impurityMeasure) }
        val learner = if (learners.size != 1) {
            arguments.combination.create(learners)
        } else {
            learners[0]
        }

        val coordinator = SimpleCoordinator(teacher, learner)

        val cfaFile = arguments.cfaFile

        val chcSystem = parseCfaToCHC(cfaFile)

        try {
            val candidates = coordinator.solveCHCSystem(chcSystem)
            println("SAFE")
            println(candidates)
        } catch (e: ContradictoryException){
            println("UNSAFE")
            println(e.contradictorySubset)
        }
    }

    private fun parseCfaToCHC(cfaFile: String): CHCSystem {
        FileInputStream(cfaFile).use {
            val cfa = CfaDslManager.createCfa(it)?:error("Could not parse model")
            return cfaToChc(cfa)
        }
    }
}