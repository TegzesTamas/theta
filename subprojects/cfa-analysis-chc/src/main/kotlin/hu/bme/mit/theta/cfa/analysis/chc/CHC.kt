package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.anytype.PrimeExpr
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.VarIndexing

abstract class CHC(val body: Expr<BoolType>) {
    open val postIndexing: VarIndexing = VarIndexing.all(0)
    abstract val expr: Expr<BoolType>
    abstract val preInvRef: Expr<BoolType>
    abstract val postInvRef: Expr<BoolType>
    abstract val invariantsToFind: Set<VarDecl<BoolType>>
}

class Fact(body: Expr<BoolType>, override val postIndexing: VarIndexing, val postInv: VarDecl<BoolType>) : CHC(body) {
    override val preInvRef: Expr<BoolType>
        get() = BoolExprs.True()

    override val postInvRef: Expr<BoolType>
        get() = postInv.ref

    override val expr: AndExpr
        get() = BoolExprs.And(body, BoolExprs.Not(PrimeExpr.of(postInv.ref)))

    override val invariantsToFind: Set<VarDecl<BoolType>>
        get() = setOf(postInv)
}

class Query(val preInv: VarDecl<BoolType>, body: Expr<BoolType>) : CHC(body) {
    override val expr: Expr<BoolType>
        get() = BoolExprs.And(preInv.ref, body)

    override val preInvRef: Expr<BoolType>
        get() = preInv.ref

    override val postInvRef: Expr<BoolType>
        get() = BoolExprs.False()

    override val invariantsToFind: Set<VarDecl<BoolType>>
        get() = setOf(preInv)
}

class InductiveClause(val preInv: VarDecl<BoolType>, body: Expr<BoolType>, override val postIndexing: VarIndexing, val postInv: VarDecl<BoolType>) : CHC(body) {
    override val preInvRef: Expr<BoolType>
        get() = preInv.ref
    override val postInvRef: Expr<BoolType>
        get() = postInv.ref
    override val expr: Expr<BoolType>
        get() = BoolExprs.And(preInv.ref, body, BoolExprs.Not(PrimeExpr.of(postInv.ref)))

    override val invariantsToFind: Set<VarDecl<BoolType>>
        get() = setOf(preInv, postInv)
}

class SimpleCHC(body: Expr<BoolType>) : CHC(body) {
    override val expr: Expr<BoolType>
        get() = body
    override val preInvRef: Expr<BoolType>
        get() = BoolExprs.True()
    override val postInvRef: Expr<BoolType>
        get() = BoolExprs.False()
    override val invariantsToFind: Set<VarDecl<BoolType>>
        get() = setOf()
}