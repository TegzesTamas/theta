package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.GenericCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.*

class MultiThreadedLearnerTest {

    @Test
    fun singleLearnerTest() {
        val mockLearner = mock(Learner::class.java)
        val cs = ConstraintSystem.Builder().build()
        `when`(mockLearner.suggestCandidates(cs))
                .thenReturn(GenericCandidates(True(), emptyMap()))
        val learner = MultiThreadedLearner(listOf(mockLearner))
        learner.suggestCandidates(cs)
        verify(mockLearner).suggestCandidates(cs)
        learner.close()
        verify(mockLearner).close()
        verifyNoMoreInteractions(mockLearner)
    }

    @Test
    fun multipleLearnerTest() {
        val cs = ConstraintSystem.Builder().build()
        val mockLearner1 = mock(Learner::class.java)
        val mockLearner2 = mock(Learner::class.java)
        `when`(mockLearner1.suggestCandidates(cs)).then {
            Thread.sleep(10)
            return@then GenericCandidates(True(), emptyMap())
        }
        `when`(mockLearner2.suggestCandidates(cs)).then {
            Thread.sleep(5)
            return@then GenericCandidates(True(), emptyMap())
        }

        val learner = MultiThreadedLearner(listOf(mockLearner1, mockLearner2))
        learner.suggestCandidates(cs)
        learner.suggestCandidates(cs)

        verify(mockLearner1, atLeastOnce()).suggestCandidates(cs)
        verify(mockLearner1, atMost(2)).suggestCandidates(cs)
        verify(mockLearner2, atLeastOnce()).suggestCandidates(cs)
        verify(mockLearner2, atMost(2)).suggestCandidates(cs)

        learner.close()
        verify(mockLearner1, atLeastOnce()).close()
        verify(mockLearner2, atLeastOnce()).close()

        verifyNoMoreInteractions(mockLearner1, mockLearner2)
    }

    @Test
    fun failingLearnerTest() {
        val cs = ConstraintSystem.Builder().build()
        val mockLearner1 = mock(Learner::class.java)
        val mockLearner2 = mock(Learner::class.java)
        `when`(mockLearner1.suggestCandidates(cs)).thenReturn(GenericCandidates(True(), emptyMap()))
        `when`(mockLearner2.suggestCandidates(cs)).thenThrow(Learner.CandidatesNotExpressibleException())

        MultiThreadedLearner(listOf(mockLearner1, mockLearner2)).use { learner ->
            repeat(7) {
                learner.suggestCandidates(cs)
                Thread.sleep(10)
            }
            verify(mockLearner1, times(7)).suggestCandidates(cs)
            verify(mockLearner2).suggestCandidates(cs)
        }
        verify(mockLearner1, atLeastOnce()).close()
        verify(mockLearner2, atLeastOnce()).close()
    }

    @Test
    fun allLearnersFailTest() {
        val cs = ConstraintSystem.Builder().build()

        val mockLearner1 = mock(Learner::class.java)
        val mockLearner2 = mock(Learner::class.java)
        val mockLearner3 = mock(Learner::class.java)
        `when`(mockLearner1.suggestCandidates(cs)).thenThrow(Learner.CandidatesNotExpressibleException())
        `when`(mockLearner2.suggestCandidates(cs)).thenThrow(Learner.CandidatesNotExpressibleException())
        `when`(mockLearner3.suggestCandidates(cs)).thenThrow(Learner.CandidatesNotExpressibleException())

        var thrown = false
        try {
            MultiThreadedLearner(listOf(mockLearner1, mockLearner2, mockLearner3)).use { learner ->
                learner.suggestCandidates(cs)
            }
        } catch (e: Learner.CandidatesNotExpressibleException) {
            thrown = true
        }
        Thread.sleep(10)
        assertTrue(thrown)
        verify(mockLearner1).suggestCandidates(cs)
        verify(mockLearner1, atLeastOnce()).close()
        verify(mockLearner2).suggestCandidates(cs)
        verify(mockLearner2, atLeastOnce()).close()
        verify(mockLearner3).suggestCandidates(cs)
        verify(mockLearner3, atLeastOnce()).close()
    }
}