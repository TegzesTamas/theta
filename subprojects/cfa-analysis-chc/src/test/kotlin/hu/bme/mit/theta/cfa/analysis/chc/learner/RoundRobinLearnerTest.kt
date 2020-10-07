package hu.bme.mit.theta.cfa.analysis.chc.learner

import hu.bme.mit.theta.cfa.analysis.chc.CNFCandidates
import hu.bme.mit.theta.cfa.analysis.chc.GenericCandidates
import hu.bme.mit.theta.cfa.analysis.chc.constraint.ConstraintSystem
import hu.bme.mit.theta.core.type.booltype.BoolExprs.False
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito.*


internal class RoundRobinLearnerTest {

    @Test
    fun singleCandidateTest() {
        val mockLearner = mock(Learner::class.java)
        val cs = ConstraintSystem.Builder().build()
        `when`(mockLearner.suggestCandidates(cs)).thenReturn(CNFCandidates(listOf(), emptyMap()))
        val uut = RoundRobinLearner(listOf(mockLearner))
        uut.suggestCandidates(cs)
        uut.suggestCandidates(cs)
        verify(mockLearner,times(2)).suggestCandidates(cs)
    }

    @Test
    fun twoCandidatesTest() {
        val mockLearner1 = mock(Learner::class.java)
        val mockLearner2 = mock(Learner::class.java)
        val cs = ConstraintSystem.Builder().build()
        `when`(mockLearner1.suggestCandidates(cs)).thenReturn(CNFCandidates(listOf(), emptyMap()))
        `when`(mockLearner2.suggestCandidates(cs)).thenReturn(GenericCandidates(False(), emptyMap()))
        val uut = RoundRobinLearner(listOf(mockLearner1, mockLearner2))
        repeat(7) {
            uut.suggestCandidates(cs)
        }
        verify(mockLearner1,times(4)).suggestCandidates(cs)
        verify(mockLearner2,times(3)).suggestCandidates(cs)
    }

    @Test
    fun failingCandidateTest(){
        val mockLearner1 = mock(Learner::class.java)
        val mockLearner2 = mock(Learner::class.java)
        val mockLearner3 = mock(Learner::class.java)
        val cs = ConstraintSystem.Builder().build()
        `when`(mockLearner1.suggestCandidates(cs)).thenReturn(CNFCandidates(listOf(), emptyMap()))
        `when`(mockLearner2.suggestCandidates(cs)).thenThrow(Learner.CandidatesNotExpressibleException())
        `when`(mockLearner3.suggestCandidates(cs)).thenReturn(CNFCandidates(listOf(), emptyMap()))
        val uut = RoundRobinLearner(listOf(mockLearner1, mockLearner2, mockLearner3))
        repeat(7){
            uut.suggestCandidates(cs)
        }
        verify(mockLearner1, times(4)).suggestCandidates(cs)
        verify(mockLearner2, times(1)).suggestCandidates(cs)
        verify(mockLearner3, times(3)).suggestCandidates(cs)
    }

    @Test
    fun allCandidatesFailTest(){
        val mockLearner1 = mock(Learner::class.java)
        val mockLearner2 = mock(Learner::class.java)
        val mockLearner3 = mock(Learner::class.java)
        val cs = ConstraintSystem.Builder().build()
        `when`(mockLearner1.suggestCandidates(cs))
                .thenReturn(CNFCandidates(listOf(), emptyMap()))
                .thenThrow(Learner.CandidatesNotExpressibleException())
        `when`(mockLearner2.suggestCandidates(cs))
                .thenReturn(CNFCandidates(listOf(), emptyMap()))
                .thenThrow(Learner.CandidatesNotExpressibleException())
        `when`(mockLearner3.suggestCandidates(cs))
                .thenReturn(CNFCandidates(listOf(), emptyMap()))
                .thenReturn(CNFCandidates(listOf(), emptyMap()))
                .thenThrow(Learner.CandidatesNotExpressibleException())
        val uut = RoundRobinLearner(listOf(mockLearner1, mockLearner2, mockLearner3))
        repeat(4){
            uut.suggestCandidates(cs)
        }
        verify(mockLearner1, times(2)).suggestCandidates(cs)
        verify(mockLearner2, times(2)).suggestCandidates(cs)
        verify(mockLearner3, times(2)).suggestCandidates(cs)
        var thrown = false
        try {
            uut.suggestCandidates(cs)
        } catch (e : Learner.CandidatesNotExpressibleException){
            thrown = true
        }
        Assert.assertTrue("No exception thrown", thrown)
    }

}