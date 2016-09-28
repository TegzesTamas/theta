package hu.bme.mit.theta.formalism.tcfa.utils;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import hu.bme.mit.theta.common.visualization.Graph;
import hu.bme.mit.theta.common.visualization.LineStyle;
import hu.bme.mit.theta.formalism.tcfa.TCFA;
import hu.bme.mit.theta.formalism.tcfa.TcfaEdge;
import hu.bme.mit.theta.formalism.tcfa.TcfaLoc;

public class TcfaVisualizer {

	private static final String TCFA_LABEL = "";
	private static final String TCFA_ID = "tcfa";
	private static final String LOC_ID_PREFIX = "loc_";
	private static final Color FILL_COLOR = Color.WHITE;
	private static final Color EDGE_COLOR = Color.BLACK;
	private static final LineStyle LOC_LINE_STYLE = LineStyle.NORMAL;
	private static final LineStyle EDGE_LINE_STYLE = LineStyle.NORMAL;
	private static final int LOC_PERIPHERIES = 1;
	private static final String PHANTOM_INIT_ID = "phantom_init";

	public static Graph visualize(final TCFA tcfa) {
		final Graph graph = new Graph(TCFA_ID, TCFA_LABEL);
		final Map<TcfaLoc, String> ids = new HashMap<>();
		traverse(graph, tcfa.getInitLoc(), ids);
		graph.addNode(PHANTOM_INIT_ID, "", FILL_COLOR, FILL_COLOR, LOC_LINE_STYLE, LOC_PERIPHERIES);
		graph.addEdge(PHANTOM_INIT_ID, ids.get(tcfa.getInitLoc()), "", EDGE_COLOR, EDGE_LINE_STYLE);
		return graph;
	}

	private static void traverse(final Graph graph, final TcfaLoc loc, final Map<TcfaLoc, String> ids) {
		if (!ids.containsKey(loc)) {
			addLocation(graph, loc, ids);
			for (final TcfaEdge outEdge : loc.getOutEdges()) {
				final TcfaLoc target = outEdge.getTarget();
				traverse(graph, target, ids);
				addEdge(graph, ids, outEdge);
			}
		}
	}

	private static void addLocation(final Graph graph, final TcfaLoc loc, final Map<TcfaLoc, String> ids) {
		final String id = LOC_ID_PREFIX + ids.size();
		ids.put(loc, id);
		final StringJoiner locLabel = new StringJoiner("\n", loc.getName() + "\n", "");
		loc.getInvars().forEach(expr -> locLabel.add(expr.toString()));
		graph.addNode(id, locLabel.toString(), FILL_COLOR, EDGE_COLOR, LOC_LINE_STYLE, LOC_PERIPHERIES);
	}

	private static void addEdge(final Graph graph, final Map<TcfaLoc, String> ids, final TcfaEdge outEdge) {
		final StringJoiner edgeLabel = new StringJoiner("\n");
		outEdge.getStmts().stream().forEach(stmt -> edgeLabel.add(stmt.toString()));
		graph.addEdge(ids.get(outEdge.getSource()), ids.get(outEdge.getTarget()), edgeLabel.toString(), EDGE_COLOR,
				EDGE_LINE_STYLE);
	}

}
