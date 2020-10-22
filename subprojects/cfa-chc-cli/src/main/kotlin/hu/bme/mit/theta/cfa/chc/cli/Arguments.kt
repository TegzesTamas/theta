package hu.bme.mit.theta.cfa.chc.cli

import com.beust.jcommander.Parameter

class Arguments {
    @Parameter(names = ["--help"], description = "Print usage information", help = true)
    var help: Boolean = false

    @Parameter(names = ["--learners", "-l"], description = "Learner to use", variableArity = true)
    var learners: MutableList<StandaloneLearnerType> = mutableListOf(StandaloneLearnerType.DecisionTree)

    @Parameter(names = ["--combination"], description = "Combination strategy to use with multiple learners")
    var combination: LearnerCombination = LearnerCombination.RoundRobin

    @Parameter(names = ["--impurityMeasure", "-i"], description = "Impurity measure to use when building a decision tree")
    var measure: ImpurityMeasureType = ImpurityMeasureType.CE

    @Parameter(names = ["--predicatePatterns", "-p"], description = "Strategy to use when building predicates")
    var predicatePatterns: MutableList<PredicatePatternType> = mutableListOf(PredicatePatternType.Atoms, PredicatePatternType.IntLEQ)

    @Parameter(names = ["--configfile", "-c"], description = "YAML file containing the configuration. Overrides other options.")
    var configFile: String? = null

    @Parameter(names = ["--model"], description = "CFA model file to check", required = true)
    var cfaFile: String = ""

    @Parameter(names = ["--benchmark"], description = "No effect")
    var benchmark: Boolean = false

    @Parameter(names = ["--header"], description = "No effect", help = true)
    var header: Boolean = false
}
