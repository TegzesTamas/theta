package hu.bme.mit.inf.ttmc.formalism.utils;

import hu.bme.mit.inf.ttmc.constraint.type.Type;
import hu.bme.mit.inf.ttmc.formalism.stmt.AssertStmt;
import hu.bme.mit.inf.ttmc.formalism.stmt.AssignStmt;
import hu.bme.mit.inf.ttmc.formalism.stmt.AssumeStmt;
import hu.bme.mit.inf.ttmc.formalism.stmt.BlockStmt;
import hu.bme.mit.inf.ttmc.formalism.stmt.DoStmt;
import hu.bme.mit.inf.ttmc.formalism.stmt.HavocStmt;
import hu.bme.mit.inf.ttmc.formalism.stmt.IfElseStmt;
import hu.bme.mit.inf.ttmc.formalism.stmt.IfStmt;
import hu.bme.mit.inf.ttmc.formalism.stmt.ReturnStmt;
import hu.bme.mit.inf.ttmc.formalism.stmt.SkipStmt;
import hu.bme.mit.inf.ttmc.formalism.stmt.WhileStmt;

public interface StmtVisitor<P, R> {
	
	public R visit(SkipStmt stmt, P param);
	public R visit(AssumeStmt stmt, P param);
	public R visit(AssertStmt stmt, P param);
	public <DeclType extends Type, ExprType extends DeclType> R visit(AssignStmt<DeclType, ExprType> stmt, P param);
	public <DeclType extends Type> R visit(HavocStmt<DeclType> stmt, P param);
	public R visit(BlockStmt stmt, P param);
	public <ReturnType extends Type> R visit(ReturnStmt<ReturnType> stmt, P param);
	public R visit(IfStmt stmt, P param);
	public R visit(IfElseStmt stmt, P param);
	public R visit(WhileStmt stmt, P param);
	public R visit(DoStmt stmt, P param);
	
}
