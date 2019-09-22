package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.PathUtils
import hu.bme.mit.theta.core.utils.VarIndexing
import hu.bme.mit.theta.solver.Solver

data class CHCSystem(val invarToCHC: Map<CfaLoop?, List<CHC>>) {
    val chcs: Set<CHC> by lazy { invarToCHC.entries.flatMap { it.value }.toSet() }
    val invariants: Set<CfaLoop> by lazy {
        val nonNull = mutableSetOf<CfaLoop>()
        invarToCHC.keys.filterNotNullTo(nonNull)
    }
}

fun Solver.addCHC(chc: CHC, candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType> = BoolExprs.True()) {
    add(chc.solverExpr(candidates, default))
}

interface CHC {
    val body: Expr<BoolType>
    val postIndexing: VarIndexing
    val preEndpoint: EdgeEndpoint
    val postEndpoint: EdgeEndpoint
    val invariantsToFind: Set<CfaLoop>
    fun solverExpr(candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType>
}

data class Fact(override val preEndpoint: CfaEndpoint,
                override val body: Expr<BoolType>,
                override val postIndexing: VarIndexing,
                override val postEndpoint: CfaLoop) : CHC {
    override val invariantsToFind: Set<CfaLoop>
        get() = setOf(postEndpoint)

    override fun solverExpr(candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> =
            BoolExprs.And(PathUtils.unfold(body, 0),
                    BoolExprs.Not(PathUtils.unfold(candidates[postEndpoint] ?: default, postIndexing)))

    override fun toString(): String = "CHC(($body) -> $postEndpoint)"
}

data class Query(override val preEndpoint: CfaLoop,
                 override val body: Expr<BoolType>,
                 override val postIndexing: VarIndexing,
                 override val postEndpoint: CfaEndpoint) : CHC {
    override val invariantsToFind: Set<CfaLoop>
        get() = setOf(preEndpoint)

    override fun toString(): String = "CHC($preEndpoint and ($body) -> false)"

    override fun solverExpr(candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> =
            BoolExprs.And(PathUtils.unfold(candidates[preEndpoint] ?: default, 0),
                    PathUtils.unfold(body, 0))
}

data class InductiveClause(override val preEndpoint: CfaLoop,
                           override val body: Expr<BoolType>,
                           override val postIndexing: VarIndexing,
                           override val postEndpoint: CfaLoop) : CHC {

    override val invariantsToFind: Set<CfaLoop>
        get() = setOf(preEndpoint, postEndpoint)

    override fun toString(): String = "CHC($preEndpoint and ($body) -> $postEndpoint)"

    override fun solverExpr(candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> =
            BoolExprs.And(PathUtils.unfold(candidates[preEndpoint] ?: default, 0),
                    PathUtils.unfold(body, 0),
                    BoolExprs.Not(PathUtils.unfold(candidates[postEndpoint] ?: default, postIndexing)))
}

data class SimpleCHC(override val preEndpoint: CfaEndpoint,
                     override val body: Expr<BoolType>,
                     override val postIndexing: VarIndexing,
                     override val postEndpoint: CfaEndpoint) : CHC {
    override val invariantsToFind: Set<CfaLoop>
        get() = setOf()

    override fun toString(): String = "CHC(($body) -> false)"

    override fun solverExpr(candidates: Map<CfaLoop, Expr<BoolType>>, default: Expr<BoolType>): Expr<BoolType> =
            PathUtils.unfold(body, 0)
}