package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.Invariant
import hu.bme.mit.theta.cfa.analysis.chc.utilities.DeclManager
import hu.bme.mit.theta.core.model.MutableValuation
import hu.bme.mit.theta.core.type.inttype.IntExprs.Int
import hu.bme.mit.theta.core.type.inttype.IntExprs.Leq
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


internal class DecisionTest {
    @Test
    fun leqExprDecisionCheck() {
        val x = DeclManager.getVar("x", Int())
        val decision = ExprDecision(Leq(x.ref, Int(1996)))

        val xIs1500 = MutableValuation()
        xIs1500.put(x, Int(1500))

        val dp1500 = Datapoint(Invariant("xIs1500"), xIs1500)
        assertTrue(decision.datapointCanBeTrue(dp1500))
        assertFalse(decision.datapointCanBeFalse(dp1500))

        val xIs2000 = MutableValuation()
        xIs2000.put(x, Int(2000))

        val dp2000 = Datapoint(Invariant("xIs2000"), xIs2000)
        assertFalse(decision.datapointCanBeTrue(dp2000))
        assertTrue(decision.datapointCanBeFalse(dp2000))

        val y = DeclManager.getVar("y", Int())


        val yIs4 = MutableValuation()
        yIs4.put(y, Int(4))

        val dp4 = Datapoint(Invariant("yIs4"), yIs4)
        assertTrue(decision.datapointCanBeTrue(dp4))
        assertTrue(decision.datapointCanBeFalse(dp4))
    }

    @Test
    fun varValueDecisionCheck() {
        val invariant = Invariant("test")
        val x = DeclManager.getVar("x", Int())
        val y = DeclManager.getVar("y", Int())
        val valuation = MutableValuation().apply { put(x, Int(46)) }
        val decision = VarValueDecision(valuation)

        run {
            val exactValuation = MutableValuation().apply { put(x, Int(46)) }
            val exactDatapoint = Datapoint(invariant, exactValuation)
            assertTrue(decision.datapointCanBeTrue(exactDatapoint))
            assertFalse(decision.datapointCanBeFalse(exactDatapoint))
        }
        run {
            val moreSpecificValuation = MutableValuation().apply {
                put(x, Int(46))
                put(y, Int(13))
            }
            val moreSpecificDatapoint = Datapoint(invariant, moreSpecificValuation)
            assertTrue(decision.datapointCanBeTrue(moreSpecificDatapoint))
            assertFalse(decision.datapointCanBeFalse(moreSpecificDatapoint))
        }
        run {
            val contradictoryValuation = MutableValuation().apply {
                put(x, Int(12))
                put(y, Int(13))
            }
            val contradictoryDatapoint = Datapoint(invariant, contradictoryValuation)
            assertFalse(decision.datapointCanBeTrue(contradictoryDatapoint))
            assertTrue(decision.datapointCanBeFalse(contradictoryDatapoint))
        }
        run {
            val unrelatedValuation = MutableValuation().apply {
                put(y, Int(13))
            }
            val unrelatedDatapoint = Datapoint(invariant, unrelatedValuation)
            assertTrue(decision.datapointCanBeTrue(unrelatedDatapoint))
            assertTrue(decision.datapointCanBeFalse(unrelatedDatapoint))
        }
    }
}