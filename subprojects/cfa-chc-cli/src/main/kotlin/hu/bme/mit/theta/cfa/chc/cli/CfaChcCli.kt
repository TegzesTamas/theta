package hu.bme.mit.theta.cfa.chc.cli

import com.beust.jcommander.JCommander
import com.beust.jcommander.ParameterException
import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.cfaToChc
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ContradictoryException
import hu.bme.mit.theta.cfa.analysis.chc.coordinator.SimpleCoordinator
import hu.bme.mit.theta.cfa.analysis.chc.teacher.SimpleTeacher
import hu.bme.mit.theta.cfa.dsl.CfaDslManager
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import java.io.File
import java.io.FileInputStream
import kotlin.system.exitProcess


object CfaChcCli {
    private const val JAR_NAME = "theta-cfa-chc-cli.jar"

    @JvmStatic
    fun main(args: Array<String>) {
        val arguments = Arguments()
        try {
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

            if (arguments.header) {
                println("\"Result\",\"Invariants\",\"Contradictory constraints\"")
                return
            }

            val cfaFile = arguments.cfaFile
            val chcSystem = parseCfaToCHC(cfaFile)

            val configFile = arguments.configFile
            val solverFactory = Z3SolverFactory.getInstance()
            val coordinator = if (configFile == null) {
                val teacher = SimpleTeacher(solverFactory.createSolver())
                val impurityMeasure = arguments.measure.create()
                val patterns = arguments.predicatePatterns.map { it.create(chcSystem) }
                val learners = arguments.learners.map { it.create(impurityMeasure, patterns) }
                val learner = if (learners.size != 1) {
                    arguments.combination.create(learners)
                } else {
                    learners[0]
                }
                SimpleCoordinator(teacher, learner)
            } else {
                val parser = YamlParser(chcSystem, solverFactory)
                parser.createCoordinatorFromYaml(File(configFile))
            }


            try {
                val candidates = coordinator.solveCHCSystem(chcSystem)
                println("\"true\",\"$candidates\",")
            } catch (e: ContradictoryException) {
                println("\"false\",,\"${e.contradictorySubset}\"")
            }
        } catch (e: Throwable) {
            if (arguments.benchmark) {
                println("\"[EX] ${e.javaClass.simpleName} : ${e.message}\",,")
            } else {
                e.printStackTrace()
            }
        }
    }


    private fun parseCfaToCHC(cfaFile: String): CHCSystem {
        FileInputStream(cfaFile).use {
            val cfa = CfaDslManager.createCfa(it) ?: error("Could not parse model")
            return cfaToChc(cfa)
        }
    }
}