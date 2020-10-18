package hu.bme.mit.theta.cfa.analysis.chc.learner

import com.nhaarman.mockitokotlin2.*
import hu.bme.mit.theta.cfa.analysis.chc.GenericCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import junit.framework.TestCase.fail
import org.junit.Test

class FallbackLearnerTest {


    @Test
    fun simpleTest() {
        val mockLearner = mock<Learner> {
            on { suggestCandidates(any()) } doReturn GenericCandidates(True(), emptyMap())
        }

        val dut = FallbackLearner(listOf(mockLearner))

        dut.suggestCandidates(ConstraintSystem.Builder().build())

        verify(mockLearner).suggestCandidates(any())
        verifyNoMoreInteractions(mockLearner)
    }

    @Test
    fun fallbackTest() {
        val failingLearner = mock<Learner> {
            on { suggestCandidates(any()) }
                    .doReturn(GenericCandidates(True(), emptyMap()))
                    .doThrow(Learner.CandidatesNotExpressibleException())
        }
        val goodLearner = mock<Learner> {
            on { suggestCandidates(any()) } doReturn GenericCandidates(True(), emptyMap())
        }

        val dut = FallbackLearner(listOf(failingLearner, goodLearner))

        repeat(3) {
            dut.suggestCandidates(ConstraintSystem.Builder().build())
        }

        verify(failingLearner, times(2)).suggestCandidates(any())
        verify(goodLearner, times(2)).suggestCandidates(any())
        verifyNoMoreInteractions(failingLearner, goodLearner)
    }

    @Test
    fun allLearnersFailTest() {
        val learners = List(3) {
            mock<Learner> {
                on { suggestCandidates(any()) } doThrow Learner.CandidatesNotExpressibleException()
            }
        }

        val dut = FallbackLearner(learners)

        try {
            dut.suggestCandidates(ConstraintSystem.Builder().build())
        } catch (e: Learner.CandidatesNotExpressibleException) {
            for (learner in learners) {
                verify(learner, times(1)).suggestCandidates(any())
                verifyNoMoreInteractions(learner)
            }
            return
        }
        fail("No exception was thrown.")
    }
}