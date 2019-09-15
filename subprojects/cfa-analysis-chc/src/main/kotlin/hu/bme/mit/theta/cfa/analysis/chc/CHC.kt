package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.AndExpr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.core.utils.VarIndexing

abstract class CHC(val body: Expr<BoolType>) {
    abstract fun getExpr(): Expr<BoolType>
    abstract fun getPreInvRef(): Expr<BoolType>
    abstract fun getPostInvRef(): Expr<BoolType>
}

class Fact(body: Expr<BoolType>, val postIndexing: VarIndexing, val postInv: VarDecl<BoolType>) : CHC(body) {
    override fun getPreInvRef(): Expr<BoolType> = BoolExprs.True()

    override fun getPostInvRef(): Expr<BoolType> = postInv.ref

    override fun getExpr(): AndExpr = BoolExprs.And(body, BoolExprs.Not(ExprUtils.applyPrimes(postInv.ref, postIndexing)))
}

class Query(val preInv: VarDecl<BoolType>, body: Expr<BoolType>) : CHC(body) {
    override fun getExpr(): Expr<BoolType> = BoolExprs.And(preInv.ref, body)

    override fun getPreInvRef(): Expr<BoolType> = preInv.ref

    override fun getPostInvRef(): Expr<BoolType> = BoolExprs.False()
}

class InductiveClause(val preInv: VarDecl<BoolType>, body: Expr<BoolType>, val postIndexing: VarIndexing, val postInv: VarDecl<BoolType>) : CHC(body) {
    override fun getPreInvRef(): Expr<BoolType> = preInv.ref
    override fun getPostInvRef(): Expr<BoolType> = postInv.ref
    override fun getExpr(): Expr<BoolType> = BoolExprs.And(preInv.ref, body, BoolExprs.Not(ExprUtils.applyPrimes(postInv.ref, postIndexing)))
}

class SimpleCHC(body: Expr<BoolType>) : CHC(body) {
    override fun getExpr(): Expr<BoolType> = body
    override fun getPreInvRef(): Expr<BoolType> = BoolExprs.True()
    override fun getPostInvRef(): Expr<BoolType> = BoolExprs.False()
}