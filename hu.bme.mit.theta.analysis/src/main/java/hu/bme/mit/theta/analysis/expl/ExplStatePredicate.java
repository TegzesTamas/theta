package hu.bme.mit.theta.analysis.expl;

import static com.google.common.base.Preconditions.checkNotNull;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.False;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;

import java.util.function.Predicate;

import hu.bme.mit.theta.common.ObjectUtils;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.core.utils.ExprUtils;
import hu.bme.mit.theta.core.utils.PathUtils;
import hu.bme.mit.theta.solver.Solver;
import hu.bme.mit.theta.solver.utils.WithPushPop;

public class ExplStatePredicate implements Predicate<ExplState> {

	private final Expr<BoolType> expr;
	private final Solver solver;

	public ExplStatePredicate(final Expr<BoolType> expr, final Solver solver) {
		this.expr = checkNotNull(expr);
		this.solver = checkNotNull(solver);
	}

	@Override
	public boolean test(final ExplState state) {
		final Expr<BoolType> simplified = ExprUtils.simplify(expr, state);
		if (simplified.equals(True())) {
			return true;
		}
		if (simplified.equals(False())) {
			return false;
		}
		try (WithPushPop wpp = new WithPushPop(solver)) {
			solver.add(PathUtils.unfold(simplified, 0));
			return solver.check().isSat();
		}
	}

	@Override
	public String toString() {
		return ObjectUtils.toStringBuilder(getClass().getSimpleName()).add(expr).toString();
	}
}
