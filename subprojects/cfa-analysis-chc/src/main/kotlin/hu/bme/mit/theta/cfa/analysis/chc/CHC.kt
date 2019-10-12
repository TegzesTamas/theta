package hu.bme.mit.theta.cfa.analysis.chc

import com.google.common.collect.Iterables
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.core.utils.PathUtils
import hu.bme.mit.theta.core.utils.VarIndexing
import hu.bme.mit.theta.solver.Solver

data class CHCSystem(val facts: List<Fact> = emptyList(),
                     val inductiveClauses: List<InductiveClause> = emptyList(),
                     val queries: List<Query> = emptyList(),
                     val simpleCHCs: List<SimpleCHC> = emptyList()) {

    val invariants: Set<CfaLoop> by lazy {
        val invariants = HashSet<CfaLoop>()
        invariants += (facts.map { it.postEndpoint })
        invariants += inductiveClauses.flatMap { listOf(it.preEndpoint, it.postEndpoint) }
        invariants += queries.map { it.preEndpoint }
        return@lazy invariants
    }

    val chcs: List<CHC> by lazy { facts + inductiveClauses + queries + simpleCHCs }

    val invToCHC: Map<CfaLoop, List<CHC>> by lazy {
        val invToCHC = HashMap<CfaLoop, List<CHC>>()
        for (chc in facts) {
            invToCHC.merge(chc.postEndpoint, listOf(chc)) { t, u -> t + u }
        }
        for (chc in inductiveClauses) {
            invToCHC.merge(chc.preEndpoint, listOf(chc)) { t, u -> t + u }
            invToCHC.merge(chc.postEndpoint, listOf(chc)) { t, u -> t + u }
        }
        for (chc in queries) {
            invToCHC.merge(chc.preEndpoint, listOf(chc)) { t, u -> t + u }
        }
        return@lazy invToCHC
    }

    fun createMerged(other: CHCSystem): CHCSystem =
            CHCSystem(
                    facts + other.facts,
                    inductiveClauses + other.inductiveClauses,
                    queries + other.queries,
                    simpleCHCs + other.simpleCHCs)

    companion object {
        fun mergeAll(systems: Iterable<CHCSystem>): CHCSystem {
            val facts = ArrayList<Fact>()
            val inductiveClauses = ArrayList<InductiveClause>()
            val queries = ArrayList<Query>()
            val simpleCHCs = ArrayList<SimpleCHC>()
            for (system in systems) {
                facts += system.facts
                inductiveClauses += system.inductiveClauses
                queries += system.queries
                simpleCHCs += system.simpleCHCs
            }
            return CHCSystem(facts, inductiveClauses, queries, simpleCHCs)
        }
    }
}

fun Solver.addCHC(chc: CHC, candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType> = BoolExprs.True()) {
    add(chc.solverExpr(candidates, default))
}

fun Solver.addSimpleCHC(simpleCHC: SimpleCHC) {
    add(simpleCHC.solverExpr())
}

interface CHC {
    val body: AndExpr
    val postIndexing: VarIndexing
    val preEndpoint: EdgeEndpoint
    val postEndpoint: EdgeEndpoint
    val invariantsToFind: Set<CfaLoop>
    fun solverExpr(candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType>
    fun append(query: Query): CHC?
}

data class Fact(override val preEndpoint: CfaEndpoint,
                override val body: AndExpr,
                override val postIndexing: VarIndexing,
                override val postEndpoint: CfaLoop) : CHC {
    override val invariantsToFind: Set<CfaLoop>
        get() = setOf(postEndpoint)

    override fun solverExpr(candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> =
            BoolExprs.And(PathUtils.unfold(body, 0),
                    BoolExprs.Not(PathUtils.unfold(candidates[postEndpoint] ?: default, postIndexing)))

    override fun append(query: Query): SimpleCHC {
        val newBody = Iterables.concat(body.ops,
                query.body.ops.map { ExprUtils.applyPrimes(it, postIndexing) })
        val newPostIndexing = postIndexing.transform().add(query.postIndexing.transform()).build()
        return SimpleCHC(preEndpoint, AndExpr.of(newBody), newPostIndexing, query.postEndpoint)
    }

    override fun toString(): String = "CHC(($body) -> $postEndpoint)"
}

data class Query(override val preEndpoint: CfaLoop,
                 override val body: AndExpr,
                 override val postIndexing: VarIndexing,
                 override val postEndpoint: CfaEndpoint) : CHC {
    override val invariantsToFind: Set<CfaLoop>
        get() = setOf(preEndpoint)

    override fun toString(): String = "CHC($preEndpoint and ($body) -> false)"

    override fun solverExpr(candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> =
            BoolExprs.And(PathUtils.unfold(candidates[preEndpoint] ?: default, 0),
                    PathUtils.unfold(body, 0))

    override fun append(query: Query): Nothing? = null
}

data class InductiveClause(override val preEndpoint: CfaLoop,
                           override val body: AndExpr,
                           override val postIndexing: VarIndexing,
                           override val postEndpoint: CfaLoop) : CHC {

    override val invariantsToFind: Set<CfaLoop>
        get() = setOf(preEndpoint, postEndpoint)

    override fun toString(): String = "CHC($preEndpoint and ($body) -> $postEndpoint)"

    override fun solverExpr(candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> =
            BoolExprs.And(PathUtils.unfold(candidates[preEndpoint] ?: default, 0),
                    PathUtils.unfold(body, 0),
                    BoolExprs.Not(PathUtils.unfold(candidates[postEndpoint] ?: default, postIndexing)))

    override fun append(query: Query): Query {
        val newBody = Iterables.concat(body.ops,
                query.body.ops.map { ExprUtils.applyPrimes(it, postIndexing) })
        val newPostIndexing = postIndexing.transform().add(query.postIndexing.transform()).build()
        return Query(preEndpoint, AndExpr.of(newBody), newPostIndexing, query.postEndpoint)
    }
}

data class SimpleCHC(override val preEndpoint: CfaEndpoint,
                     override val body: AndExpr,
                     override val postIndexing: VarIndexing,
                     override val postEndpoint: CfaEndpoint) : CHC {
    override val invariantsToFind: Set<CfaLoop>
        get() = setOf()

    override fun toString(): String = "CHC(($body) -> false)"

    fun solverExpr(): Expr<BoolType> = PathUtils.unfold(body, 0)
    override fun solverExpr(candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> = solverExpr()


    override fun append(query: Query): Nothing? = null
}