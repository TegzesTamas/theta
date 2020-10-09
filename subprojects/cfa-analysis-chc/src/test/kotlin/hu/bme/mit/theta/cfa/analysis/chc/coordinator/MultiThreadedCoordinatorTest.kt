package hu.bme.mit.theta.cfa.analysis.chc.coordinator

import com.nhaarman.mockitokotlin2.*
import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.GenericCandidates
import hu.bme.mit.theta.cfa.analysis.chc.learner.Learner
import hu.bme.mit.theta.cfa.analysis.chc.teacher.Teacher
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import org.junit.Test

class MultiThreadedCoordinatorTest {

    @Test
    fun simpleTest() {
        val mockLearner = mock<Learner> {
            on { suggestCandidates(any()) } doReturn GenericCandidates(True(), emptyMap())
        }
        val mockTeacher = mock<Teacher> {
            on { checkCandidates(any(), any()) } doReturn null
        }

        val coordinator = MultiThreadedCoordinator(listOf(mockLearner), listOf(mockTeacher))

        coordinator.solveCHCSystem(CHCSystem())

        verify(mockLearner).suggestCandidates(any())
        verify(mockTeacher).checkCandidates(any(), any())
        verifyNoMoreInteractions(mockLearner, mockTeacher)
    }


}