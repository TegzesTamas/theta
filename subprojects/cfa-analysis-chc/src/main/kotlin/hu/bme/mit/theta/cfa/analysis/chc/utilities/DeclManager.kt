package hu.bme.mit.theta.cfa.analysis.chc.utilities

import hu.bme.mit.theta.core.decl.Decls
import hu.bme.mit.theta.core.type.Type

object DeclManager {
    private var num: Long = 0
    fun <TYPE : Type> getConst(prefix: String = "DeclManager", type: TYPE) = synchronized(this) { Decls.Const("_${prefix}_#${num++}", type)!! }
    fun <TYPE : Type> getVar(prefix: String = "DeclManager", type: TYPE) = synchronized(this) { Decls.Var("_${prefix}_#${num++}", type)!! }
}