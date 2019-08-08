/*
 *  Copyright 2017 Budapest University of Technology and Economics
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package hu.bme.mit.theta.xta.analysis.expl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.zip;
import static hu.bme.mit.theta.core.stmt.Stmts.Assume;
import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Eq;
import static hu.bme.mit.theta.core.type.booltype.BoolExprs.False;
import static hu.bme.mit.theta.core.type.booltype.SmartBoolExprs.And;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import com.google.common.collect.Lists;

import hu.bme.mit.theta.analysis.expl.ExplState;
import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.model.MutableValuation;
import hu.bme.mit.theta.core.model.Valuation;
import hu.bme.mit.theta.core.stmt.AssignStmt;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.LitExpr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.core.type.booltype.FalseExpr;
import hu.bme.mit.theta.core.utils.ExprUtils;
import hu.bme.mit.theta.core.utils.WpState;
import hu.bme.mit.theta.xta.Guard;
import hu.bme.mit.theta.xta.Update;
import hu.bme.mit.theta.xta.XtaProcess.Edge;
import hu.bme.mit.theta.xta.XtaProcess.Loc;
import hu.bme.mit.theta.xta.analysis.XtaAction;
import hu.bme.mit.theta.xta.analysis.XtaAction.BasicXtaAction;
import hu.bme.mit.theta.xta.analysis.XtaAction.BinaryXtaAction;

public final class XtaExplUtils {

	private XtaExplUtils() {
	}

	public static Valuation interpolate(final Valuation valA, final Expr<BoolType> exprB) {
		final Collection<VarDecl<?>> vars = ExprUtils.getVars(exprB).stream().filter(valA.getDecls()::contains)
				.collect(toList());
		final MutableValuation valI = new MutableValuation();
		for (final VarDecl<?> var : vars) {
			final LitExpr<?> val = valA.eval(var).get();
			valI.put(var, val);
		}

		assert ExprUtils.simplify(exprB, valI).equals(False());

		for (final VarDecl<?> var : vars) {
			valI.remove(var);
			final Expr<BoolType> simplifiedExprB = ExprUtils.simplify(exprB, valI);
			if (simplifiedExprB.equals(False())) {
				continue;
			} else {
				final LitExpr<?> val = valA.eval(var).get();
				valI.put(var, val);
			}
		}

		return valI;
	}

	////

	public static ExplState post(final Valuation val, final XtaAction action) {
		checkNotNull(val);
		checkNotNull(action);

		if (action.isBasic()) {
			return postForBasicAction(val, action.asBasic());
		} else if (action.isBinary()) {
			return postForBinaryAction(val, action.asBinary());
		} else {
			throw new AssertionError();
		}
	}

	private static ExplState postForBasicAction(final Valuation val, final BasicXtaAction action) {
		final Edge edge = action.getEdge();
		final List<Loc> targetLocs = action.getTargetLocs();

		if (!checkDataGuards(edge, val)) {
			return ExplState.bottom();
		}

		final MutableValuation succVal = MutableValuation.copyOf(val);
		applyDataUpdates(action.getEdge(), succVal);

		if (!checkDataInvariants(targetLocs, succVal)) {
			return ExplState.bottom();
		}

		return ExplState.of(succVal);
	}

	private static ExplState postForBinaryAction(final Valuation val, final BinaryXtaAction action) {
		final Edge emitEdge = action.getEmitEdge();
		final Edge recvEdge = action.getRecvEdge();
		final List<Loc> targetLocs = action.getTargetLocs();

		if (!checkSync(emitEdge, recvEdge, val)) {
			return ExplState.bottom();
		}

		if (!checkDataGuards(emitEdge, val)) {
			return ExplState.bottom();
		}

		if (!checkDataGuards(recvEdge, val)) {
			return ExplState.bottom();
		}

		final MutableValuation succVal = MutableValuation.copyOf(val);
		applyDataUpdates(emitEdge, succVal);
		applyDataUpdates(recvEdge, succVal);

		if (!checkDataInvariants(targetLocs, succVal)) {
			return ExplState.bottom();
		}

		return ExplState.of(succVal);
	}

	private static boolean checkSync(final Edge emitEdge, final Edge recvEdge, final Valuation val) {
		final List<Expr<?>> emitArgs = emitEdge.getSync().get().getArgs();
		final List<Expr<?>> recvArgs = recvEdge.getSync().get().getArgs();
		return zip(emitArgs.stream(), recvArgs.stream(), (e, r) -> e.eval(val).equals(r.eval(val))).allMatch(x -> x);
	}

	private static boolean checkDataGuards(final Edge edge, final Valuation val) {
		for (final Guard guard : edge.getGuards()) {
			if (guard.isDataGuard()) {
				final Expr<BoolType> expr = ExprUtils.simplify(guard.asDataGuard().toExpr(), val);
				if (expr instanceof FalseExpr) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean checkDataInvariants(final List<Loc> locs, final Valuation val) {
		for (final Loc loc : locs) {
			final Collection<Guard> invars = loc.getInvars();
			for (final Guard invar : invars) {
				if (invar.isDataGuard()) {
					final Expr<BoolType> expr = ExprUtils.simplify(invar.asDataGuard().toExpr(), val);
					if (expr instanceof FalseExpr) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static void applyDataUpdates(final Edge edge, final MutableValuation val) {
		final List<Update> updates = edge.getUpdates();
		for (final Update update : updates) {
			if (update.isDataUpdate()) {
				final AssignStmt<?> stmt = (AssignStmt<?>) update.toStmt();
				final VarDecl<?> varDecl = stmt.getVarDecl();
				final Expr<?> expr = ExprUtils.simplify(stmt.getExpr(), val);
				if (expr instanceof LitExpr) {
					final LitExpr<?> value = (LitExpr<?>) expr;
					val.put(varDecl, value);
				} else {
					val.remove(varDecl);
				}
			}
		}
	}

	////

	public static Expr<BoolType> pre(final Expr<BoolType> expr, final XtaAction action) {
		checkNotNull(expr);
		checkNotNull(action);

		if (action.isBasic()) {
			return preForBasicAction(expr, action.asBasic());
		} else if (action.isBinary()) {
			return preForBinaryAction(expr, action.asBinary());
		} else {
			throw new AssertionError();
		}
	}

	private static Expr<BoolType> preForBasicAction(final Expr<BoolType> expr, final BasicXtaAction action) {
		final Edge edge = action.getEdge();
		final WpState wp0 = WpState.of(expr);
		final WpState wp1 = applyInverseUpdates(wp0, edge);
		final WpState wp2 = applyGuards(wp1, edge);
		return wp2.getExpr();
	}

	private static Expr<BoolType> preForBinaryAction(final Expr<BoolType> expr, final BinaryXtaAction action) {
		final Edge emitEdge = action.getEmitEdge();
		final Edge recvEdge = action.getRecvEdge();
		final WpState wp0 = WpState.of(expr);
		final WpState wp1 = applyInverseUpdates(wp0, recvEdge);
		final WpState wp2 = applyInverseUpdates(wp1, emitEdge);
		final WpState wp3 = applyGuards(wp2, recvEdge);
		final WpState wp4 = applyGuards(wp3, emitEdge);
		final WpState wp5 = applySync(wp4, emitEdge, recvEdge);
		return wp5.getExpr();
	}

	private static WpState applyInverseUpdates(final WpState state, final Edge edge) {
		WpState res = state;
		for (final Update update : Lists.reverse(edge.getUpdates())) {
			if (update.isDataUpdate()) {
				res = res.wep(update.asDataUpdate().toStmt());
			}
		}
		return res;
	}

	private static WpState applyGuards(final WpState state, final Edge edge) {
		WpState res = state;
		for (final Guard guard : edge.getGuards()) {
			if (guard.isDataGuard()) {
				res = res.wep(Assume(guard.asDataGuard().toExpr()));
			}
		}
		return res;
	}

	private static WpState applySync(final WpState state, final Edge emitEdge, final Edge recvEdge) {
		final Stream<Expr<?>> emitArgs = emitEdge.getSync().get().getArgs().stream();
		final Stream<Expr<?>> recvArgs = recvEdge.getSync().get().getArgs().stream();
		final List<Expr<BoolType>> exprs = zip(emitArgs, recvArgs, (e, r) -> (Expr<BoolType>) Eq(e, r))
				.collect(toImmutableList());
		final Expr<BoolType> andExpr = And(exprs);
		return state.wep(Assume(andExpr));
	}

}
