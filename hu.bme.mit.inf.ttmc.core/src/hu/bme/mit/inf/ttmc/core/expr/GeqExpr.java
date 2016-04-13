package hu.bme.mit.inf.ttmc.core.expr;

import hu.bme.mit.inf.ttmc.core.type.BoolType;
import hu.bme.mit.inf.ttmc.core.type.RatType;

public interface GeqExpr extends BinaryExpr<RatType, RatType, BoolType> {
	
	@Override
	public GeqExpr withOps(final Expr<? extends RatType> leftOp, final Expr<? extends RatType> rightOp);
	
	@Override
	public GeqExpr withLeftOp(final Expr<? extends RatType> leftOp);

	@Override
	public GeqExpr withRightOp(final Expr<? extends RatType> rightOp);

}