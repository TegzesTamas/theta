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

data class Invariant(val name: String)

data class CHCSystem(val facts: List<Fact> = emptyList(),
                     val inductiveClauses: List<InductiveClause> = emptyList(),
                     val queries: List<Query> = emptyList(),
                     val simpleCHCs: List<SimpleCHC> = emptyList()) {

    val invariants: Set<Invariant> by lazy {
        val invariants = HashSet<Invariant>()
        invariants += (facts.map { it.postInvariant })
        invariants += inductiveClauses.flatMap { listOf(it.preInvariant, it.postInvariant) }
        invariants += queries.map { it.preInvariant }
        return@lazy invariants
    }

    val chcs: List<CHC> by lazy { facts + inductiveClauses + queries + simpleCHCs }

    val invToCHC: Map<Invariant, List<CHC>> by lazy {
        val invToCHC = HashMap<Invariant, List<CHC>>()
        for (chc in facts) {
            invToCHC.merge(chc.postInvariant, listOf(chc)) { t, u -> t + u }
        }
        for (chc in inductiveClauses) {
            invToCHC.merge(chc.preInvariant, listOf(chc)) { t, u -> t + u }
            invToCHC.merge(chc.postInvariant, listOf(chc)) { t, u -> t + u }
        }
        for (chc in queries) {
            invToCHC.merge(chc.preInvariant, listOf(chc)) { t, u -> t + u }
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

fun Solver.addCHC(chc: CHC, candidates: Map<Invariant, Expr<BoolType>>, default: Expr<BoolType> = BoolExprs.True()) {
    add(chc.solverExpr(candidates, default))
}

fun Solver.addSimpleCHC(simpleCHC: SimpleCHC) {
    add(simpleCHC.solverExpr())
}

interface CHC {
    val body: AndExpr
    val postIndexing: VarIndexing
    val invariantsToFind: Set<Invariant>
    fun solverExpr(candidates: Map<Invariant, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType>
    fun append(query: Query): CHC?
}

data class Fact(override val body: AndExpr,
                override val postIndexing: VarIndexing,
                val postInvariant: Invariant) : CHC {
    override val invariantsToFind: Set<Invariant>
        get() = setOf(postInvariant)

    override fun solverExpr(candidates: Map<Invariant, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> =
            BoolExprs.And(PathUtils.unfold(body, 0),
                    BoolExprs.Not(PathUtils.unfold(candidates[postInvariant] ?: default, postIndexing)))

    override fun append(query: Query): SimpleCHC {
        val newBody = Iterables.concat(body.ops,
                query.body.ops.map { ExprUtils.applyPrimes(it, postIndexing) })
        val newPostIndexing = postIndexing.transform().add(query.postIndexing.transform()).build()
        return SimpleCHC(AndExpr.of(newBody), newPostIndexing)
    }

    override fun toString(): String = "CHC(($body) -> $postInvariant)"
}

data class Query(val preInvariant: Invariant,
                 override val body: AndExpr,
                 override val postIndexing: VarIndexing) : CHC {
    override val invariantsToFind: Set<Invariant>
        get() = setOf(preInvariant)

    override fun toString(): String = "CHC($preInvariant and ($body) -> false)"

    override fun solverExpr(candidates: Map<Invariant, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> =
            BoolExprs.And(PathUtils.unfold(candidates[preInvariant] ?: default, 0),
                    PathUtils.unfold(body, 0))

    override fun append(query: Query): Nothing? = null
}

data class InductiveClause(val preInvariant: Invariant,
                           override val body: AndExpr,
                           override val postIndexing: VarIndexing,
                           val postInvariant: Invariant) : CHC {

    override val invariantsToFind: Set<Invariant>
        get() = setOf(preInvariant, postInvariant)

    override fun toString(): String = "CHC($preInvariant and ($body) -> $postInvariant)"

    override fun solverExpr(candidates: Map<Invariant, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> =
            BoolExprs.And(PathUtils.unfold(candidates[preInvariant] ?: default, 0),
                    PathUtils.unfold(body, 0),
                    BoolExprs.Not(PathUtils.unfold(candidates[postInvariant] ?: default, postIndexing)))

    override fun append(query: Query): Query {
        val newBody = Iterables.concat(body.ops,
                query.body.ops.map { ExprUtils.applyPrimes(it, postIndexing) })
        val newPostIndexing = postIndexing.transform().add(query.postIndexing.transform()).build()
        return Query(preInvariant, AndExpr.of(newBody), newPostIndexing)
    }
}

data class SimpleCHC(override val body: AndExpr,
                     override val postIndexing: VarIndexing) : CHC {
    override val invariantsToFind: Set<Invariant>
        get() = setOf()

    override fun toString(): String = "CHC(($body) -> false)"

    fun solverExpr(): Expr<BoolType> = PathUtils.unfold(body, 0)
    override fun solverExpr(candidates: Map<Invariant, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> = solverExpr()


    override fun append(query: Query): Nothing? = null
}