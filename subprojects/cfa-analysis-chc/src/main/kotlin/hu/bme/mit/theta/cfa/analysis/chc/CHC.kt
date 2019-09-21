package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.anytype.PrimeExpr
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.VarIndexing

interface CHC {
    val body: Expr<BoolType>
    val postIndexing: VarIndexing
    val expr: Expr<BoolType>
    val preInvRef: Expr<BoolType>
    val postInvRef: Expr<BoolType>
    val invariantsToFind: Set<VarDecl<BoolType>>
    override fun toString(): String
}

data class Fact(override val body: Expr<BoolType>, override val postIndexing: VarIndexing, val postInv: VarDecl<BoolType>) : CHC {
    override val preInvRef: Expr<BoolType>
        get() = BoolExprs.True()
    override val postInvRef: Expr<BoolType>
        get() = postInv.ref
    override val expr: AndExpr
        get() = BoolExprs.And(body, BoolExprs.Not(PrimeExpr.of(postInv.ref)))
    override val invariantsToFind: Set<VarDecl<BoolType>>
        get() = setOf(postInv)

    override fun toString(): String = "CHC(($body) -> $postInv)"
}

data class Query(val preInv: VarDecl<BoolType>, override val body: Expr<BoolType>, override val postIndexing: VarIndexing) : CHC {
    override val expr: Expr<BoolType>
        get() = BoolExprs.And(preInv.ref, body)
    override val preInvRef: Expr<BoolType>
        get() = preInv.ref
    override val postInvRef: Expr<BoolType>
        get() = BoolExprs.False()
    override val invariantsToFind: Set<VarDecl<BoolType>>
        get() = setOf(preInv)

    override fun toString(): String = "CHC($preInv and ($body) -> false)"
}

data class InductiveClause(val preInv: VarDecl<BoolType>,
                           override val body: Expr<BoolType>,
                           override val postIndexing: VarIndexing,
                           val postInv: VarDecl<BoolType>) : CHC {
    override val preInvRef: Expr<BoolType>
        get() = preInv.ref
    override val postInvRef: Expr<BoolType>
        get() = postInv.ref
    override val expr: Expr<BoolType>
        get() = BoolExprs.And(preInv.ref, body, BoolExprs.Not(PrimeExpr.of(postInv.ref)))
    override val invariantsToFind: Set<VarDecl<BoolType>>
        get() = setOf(preInv, postInv)

    override fun toString(): String = "CHC($preInv and ($body) -> $postInv)"
}

data class SimpleCHC(override val body: Expr<BoolType>, override val postIndexing: VarIndexing) : CHC {
    override val expr: Expr<BoolType>
        get() = body
    override val preInvRef: Expr<BoolType>
        get() = BoolExprs.True()
    override val postInvRef: Expr<BoolType>
        get() = BoolExprs.False()
    override val invariantsToFind: Set<VarDecl<BoolType>>
        get() = setOf()

    override fun toString(): String = "CHC(($body) -> false)"
}

data class CHCSystem(val invarToCHC: Map<VarDecl<BoolType>?, List<CHC>>) {
    val chcs: Set<CHC> by lazy { invarToCHC.entries.flatMap { it.value }.toSet() }
    val invariants: Set<VarDecl<BoolType>> by lazy {
        val nonNull = mutableSetOf<VarDecl<BoolType>>()
        invarToCHC.keys.filterNotNullTo(nonNull)
    }
}