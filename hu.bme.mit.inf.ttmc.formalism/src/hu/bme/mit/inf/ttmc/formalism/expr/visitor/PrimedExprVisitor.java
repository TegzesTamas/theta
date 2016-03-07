package hu.bme.mit.inf.ttmc.formalism.expr.visitor;

import hu.bme.mit.inf.ttmc.constraint.type.Type;
import hu.bme.mit.inf.ttmc.constraint.utils.ExprVisitor;
import hu.bme.mit.inf.ttmc.formalism.expr.PrimedExpr;

public interface PrimedExprVisitor<P, R> extends ExprVisitor<P, R> {
	public <ExprType extends Type> R visit(PrimedExpr<ExprType> expr, P param);
}
