package hu.bme.mit.inf.ttmc.formalism.cfa.impl;

import java.util.List;

import com.google.common.collect.ImmutableList;

import hu.bme.mit.inf.ttmc.formalism.cfa.CFAEdge;
import hu.bme.mit.inf.ttmc.formalism.cfa.CFALoc;
import hu.bme.mit.inf.ttmc.formalism.cfa.impl.ImmutableCFALoc.CFALocBuilder;
import hu.bme.mit.inf.ttmc.formalism.stmt.Stmt;

final class ImmutableCFAEdge implements CFAEdge {

	final CFALoc source;
	final CFALoc target;
	final List<Stmt> stmts;

	private ImmutableCFAEdge(final CFAEdgeBuilder builder) {
		builder.edge = this;

		source = builder.source.build();
		target = builder.target.build();
		stmts = ImmutableList.copyOf(builder.stmts);
	}


	@Override
	public CFALoc getSource() {
		return source;
	}

	@Override
	public CFALoc getTarget() {
		return target;
	}

	@Override
	public List<Stmt> getStmts() {
		return stmts;
	}

	////

	final static class CFAEdgeBuilder {

		private ImmutableCFAEdge edge;

		private CFALocBuilder source;
		private CFALocBuilder target;
		private List<Stmt> stmts;

		CFAEdgeBuilder(final List<Stmt> stmts) {
			this.stmts = stmts;
		}

		public ImmutableCFAEdge build() {
			if (edge == null) {
				new ImmutableCFAEdge(this);
			}

			return edge;
		}

		public void setSource(final CFALocBuilder source) {
			this.source = source;
		}

		public void setTarget(final CFALocBuilder target) {
			this.target = target;
		}
	}

}
