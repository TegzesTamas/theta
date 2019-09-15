package hu.bme.mit.theta.cfa.analysis.chc

import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.utils.StmtUtils
import hu.bme.mit.theta.core.utils.VarIndexing
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.min


private data class CfaStructureEdge(val source: CfaLoop, val target: CfaLoop, val edges: List<CFA.Edge>) {
    fun toCHC(): CHC {
        val stmtUnfoldResult = StmtUtils.toExpr(edges.map { it.stmt }, VarIndexing.all(0))
        val body = BoolExprs.And(stmtUnfoldResult.exprs)
        val nextIndexing = stmtUnfoldResult.indexing
        return when {
            source is InitOrGoalLoop && target is InitOrGoalLoop -> SimpleCHC(body)
            source is InitOrGoalLoop && target is NormalCfaLoop -> Fact(body, nextIndexing, target.invariant)
            source is NormalCfaLoop && target is InitOrGoalLoop -> Query(source.invariant, body)
            source is NormalCfaLoop && target is NormalCfaLoop -> InductiveClause(source.invariant, body, nextIndexing, target.invariant)
            else -> throw AssertionError("Unknown CfaLoop type. Source: $source, Target:$target")
        }
    }
}

private abstract class CfaLoop(val exitLoc: CFA.Loc, val locSet: Set<CFA.Loc>)

private class InitOrGoalLoop(exitLoc: CFA.Loc, locSet: Set<CFA.Loc>) : CfaLoop(exitLoc, locSet)
private class NormalCfaLoop(exitLoc: CFA.Loc, locSet: Set<CFA.Loc>) : CfaLoop(exitLoc, locSet) {
    val invariant = DeclManager.getVar("chc_loop_invar", BoolExprs.Bool())
}

fun findSCCs(usedLocs: Set<CFA.Loc>): List<Set<CFA.Loc>> {
    data class TarjanData(val index: Int, var lowlink: Int, var onStack: Boolean)
    data class DFSStackEntry(val loc: CFA.Loc, var nextSuccIndex: Int = 0)

    val sccs = mutableListOf<Set<CFA.Loc>>()

    val tarjanStack: MutableList<CFA.Loc> = mutableListOf()
    val dfsStack: Deque<DFSStackEntry> = ArrayDeque()
    val map: MutableMap<CFA.Loc, TarjanData> = HashMap()

    var index = 0
    for (loc in usedLocs) {
        if (map[loc] == null) {
            map[loc] = TarjanData(index, index, true)
            ++index
            tarjanStack += loc
            dfsStack.push(DFSStackEntry(loc))
            while (dfsStack.isNotEmpty()) {
                val curr = dfsStack.peek()
                val currData = map[curr.loc] ?: throw AssertionError("Location on stack, but not in map.")
                if (curr.nextSuccIndex < curr.loc.outEdges.size) {
                    val successor = curr.loc.outEdges.elementAt(curr.nextSuccIndex++).target
                    if (successor in usedLocs) {
                        val succData = map[successor]
                        when {
                            succData == null -> {
                                map[successor] = TarjanData(index, index, true)
                                ++index
                                dfsStack.push(DFSStackEntry(successor))
                                tarjanStack += successor
                            }
                            succData.onStack -> {
                                currData.lowlink = min(currData.lowlink, succData.index)
                            }
                            else -> {
                                //Other node is in another component
                                //Current node definitely not reachable from the successor
                            }
                        }
                    }
                } else {
                    if (currData.lowlink == currData.index) {
                        //This is the root node
                        val sccLocs = mutableSetOf<CFA.Loc>()
                        do {
                            val entry = tarjanStack.removeAt(tarjanStack.lastIndex)
                            sccLocs += entry
                            (map[entry] ?: throw AssertionError("Location on stack, but not in map.")).onStack = false
                        } while (entry != curr.loc)
                        sccs += sccLocs
                    }
                    dfsStack.pop()
                    if (dfsStack.isNotEmpty()) {
                        val prevData = map[dfsStack.peek().loc]
                                ?: throw AssertionError("Location on stack, but not in map.")
                        prevData.lowlink = min(prevData.lowlink, currData.lowlink)
                    }
                }

            }
        }
    }
    return sccs
}


private fun findLoopsinSCCs(sccs: List<Set<CFA.Loc>>): List<CfaLoop> {
    val setsToProcess = sccs.toMutableList()
    val loops = mutableListOf<CfaLoop>()
    while (setsToProcess.isNotEmpty()) {
        val locSet = setsToProcess.removeAt(setsToProcess.lastIndex)
        val exitNodes = locSet.filter { it.outEdges.any { it.target !in locSet } }
        when (exitNodes.size) {
            0 -> {
            }
            1 -> {
                val exitNode = exitNodes[0]
                if (exitNode.outEdges.any { it.target in locSet }) {
                    loops += NormalCfaLoop(exitNode, locSet)
                    val locSubset = locSet.subtract(listOf(exitNode))
                    if (locSubset.isNotEmpty()) {
                        setsToProcess += findSCCs(locSubset)
                    }
                }
            }
            else -> throw IllegalArgumentException("Program structure does not allow translation to CHC.")

        }
    }
    return loops
}

private fun findPathsBetweenLoops(loops: List<CfaLoop>): List<CfaStructureEdge> {
    val locToLoop = (loops.map { it.exitLoc to it }).toMap()
    val structureEdges = mutableListOf<CfaStructureEdge>()

    data class BFSQueueEntry(val loc: CFA.Loc, val path: List<CFA.Edge>, val startLoop: CfaLoop)

    val bfsQueue: Deque<BFSQueueEntry> = ArrayDeque()

    bfsQueue += loops.map { BFSQueueEntry(it.exitLoc, emptyList(), it) }
    while (bfsQueue.isNotEmpty()) {
        val (loc, path, startLoop) = bfsQueue.pop()
        for (outEdge in loc.outEdges) {
            val targetLoop = locToLoop[outEdge.target]
            if (targetLoop == null) {
                bfsQueue.push(BFSQueueEntry(outEdge.target, path + outEdge, startLoop))
            } else {
                structureEdges += CfaStructureEdge(startLoop, targetLoop, path + outEdge)
            }
        }
    }
    return structureEdges
}

fun cfaToChc(cfa: CFA): List<CHC> {
    val sccs = findSCCs(cfa.locs.toSet())
    val loops = findLoopsinSCCs(sccs) + InitOrGoalLoop(cfa.initLoc, setOf(cfa.initLoc)) + InitOrGoalLoop(cfa.errorLoc, setOf(cfa.errorLoc))
    val paths = findPathsBetweenLoops(loops)
    return paths.map { it.toCHC() }

}
