package hu.bme.mit.theta.cfa.chc.cli

import com.amihaiemil.eoyaml.Yaml
import com.amihaiemil.eoyaml.YamlMapping
import com.amihaiemil.eoyaml.YamlNode
import com.amihaiemil.eoyaml.YamlSequence
import com.amihaiemil.eoyaml.exceptions.YamlReadingException
import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.coordinator.Coordinator
import hu.bme.mit.theta.cfa.analysis.chc.learner.Learner
import hu.bme.mit.theta.cfa.analysis.chc.teacher.SimpleTeacher
import hu.bme.mit.theta.solver.SolverFactory
import java.io.File


class YamlParser(private val chcs: CHCSystem,
                 private val solverFactory: SolverFactory) {

    class YamlException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun createCoordinatorFromYaml(reader: File): Coordinator {
        val root = Yaml.createYamlInput(reader).readYamlMapping()

        val coordinatorType = root.parseChildOrDefault("coordinator", CoordinatorType.Simple) { node: YamlNode ->
            node.asStringOrNull()?.let {
                try {
                    CoordinatorType.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    throw YamlException("Unknown coordinator: $it. Valid values are: ${names<CoordinatorType>()}")
                }
            } ?: throw YamlException("'cordinator' must be one of ${names<CoordinatorType>()}.")

        }


        val teacherNum = root.parseChildOrDefault("teachers", 1) { node ->
            node.asStringOrNull()
                    ?.toIntOrNull()
                    ?: throw YamlException("'teachers' must be an integer.")
        }

        val teachers = List(teacherNum) { SimpleTeacher(solverFactory.createSolver()) }

        val learners = root.yamlSequence("learners")?.map { parseLearner(it.asMappingOrNull()) }
                ?: throw YamlException("'learners' must be a sequence")

        return coordinatorType.create(teachers, learners)
    }

    private fun parseLearner(yaml: YamlMapping?): Learner {
        if (yaml == null) {
            throw YamlException("Learners must be given as mappings.")
        }
        val name = yaml.parseChildOrDefault("name", "") { node ->
            node.asStringOrNull() ?: throw YamlException("'learner.name' must be a string")
        }

        val learnerType = yaml.parseChildOrDefault<LearnerType>("type", StandaloneLearnerType.DecisionTree) { node ->
            val typeString = node.asStringOrNull()
            if (typeString != null) {
                try {
                    if (typeString in names<LearnerCombination>()) {
                        return@parseChildOrDefault LearnerCombination.valueOf(typeString)
                    } else {
                        return@parseChildOrDefault StandaloneLearnerType.valueOf(typeString)
                    }
                } catch (e: IllegalArgumentException) {
                    throw YamlException("Unknown learner type: $typeString, valid options are: ${names<LearnerCombination>() + names<StandaloneLearnerType>()}", e)
                }
            } else {
                throw YamlException("'learner.type' must point to one of ${names<LearnerCombination>() + names<StandaloneLearnerType>()}")
            }
        }

        val measureType = yaml.parseChildOrDefault("measure", ImpurityMeasureType.CE) { node ->
            node.asStringOrNull()?.let {
                try {
                    ImpurityMeasureType.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    throw YamlException("Unknown measure type: $it, valid options are: ${names<ImpurityMeasureType>()}")
                }
            } ?: throw YamlException("'learner.measure' must point to one of ${names<ImpurityMeasureType>()}")
        }


        val patterns =
                yaml.parseChildOrDefault("predicatePatterns",
                        PredicatePatternType.values().toList()) { node ->
                    node.asSequenceOrNull()?.map { patternNode ->
                        patternNode.asStringOrNull()?.let { patternString ->
                            try {
                                PredicatePatternType.valueOf(patternString)
                            } catch (e: IllegalArgumentException) {
                                throw YamlException("Unknown predicate pattern: $patternString. Valid options are: ${names<PredicatePatternType>()}")
                            }
                        }
                                ?: throw YamlException("'learner.predicatePatterns' must be a subset of ${names<PredicatePatternType>()}")
                    } ?: throw YamlException("'learner.predicatePatterns' must be a sequence.")
                }

        val children = yaml.parseChildOrDefault("children", emptyList()) { node ->
            node.asSequenceOrNull()?.map {
                parseLearner(it.asMappingOrNull())
            } ?: throw YamlException("'learner.children' must be a sequence.")
        }
        return learnerType.create(
                name,
                children,
                measureType.create(),
                patterns.map { it.create(chcs) }
        )
    }

    private fun YamlMapping.containsKey(key: String): Boolean = keys().any { it.asScalar()?.value() == key }

    private fun <T> YamlMapping.parseChildOrDefault(key: String, default: T, convert: (YamlNode) -> T): T =
            if (containsKey(key)) {
                convert(this.value(key))
            } else {
                default
            }

    private fun YamlNode.asStringOrNull(): String? =
            try {
                asScalar().value()
            } catch (e: Exception) {
                null
            }

    private fun YamlNode.asSequenceOrNull(): YamlSequence? =
            try {
                asSequence()
            } catch (e: YamlReadingException) {
                null
            } catch (e: ClassCastException) {
                null
            }

    private fun YamlNode.asMappingOrNull(): YamlMapping? =
            try {
                asMapping()
            } catch (e: YamlReadingException) {
                null
            } catch (e: ClassCastException) {
                null
            }

    private inline fun <reified T : Enum<T>> names() = enumValues<T>().map { it.name }
}
