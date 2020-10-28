package hu.bme.mit.theta.cfa.analysis.chc.coordinator

import com.nhaarman.mockitokotlin2.*
import hu.bme.mit.theta.cfa.analysis.chc.CHCSystem
import hu.bme.mit.theta.cfa.analysis.chc.GenericCandidates
import hu.bme.mit.theta.cfa.analysis.chc.learner.Learner
import hu.bme.mit.theta.cfa.analysis.chc.teacher.Teacher
import hu.bme.mit.theta.core.type.booltype.BoolExprs.False
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiThreadedCoordinatorTest {

    @Test
    fun simpleTest() {
        val mockLearner = mock<Learner> {
            on { suggestCandidates(any()) } doReturn GenericCandidates("", True(), emptyMap())
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

    @Test
    fun multipleTeachersMultipleLearnersTest() {
        val mockLearner1 = mock<Learner> {
            on { suggestCandidates(any()) } doReturn GenericCandidates("", True(), emptyMap())
        }
        val mockLearner2 = mock<Learner> {
            on { suggestCandidates(any()) } doReturn GenericCandidates("", False(), emptyMap())
        }
        val mockTeacher1 = mock<Teacher> {
            on { checkCandidates(any(), any()) } doReturn emptyList() doReturn null
        }
        val mockTeacher2 = mock<Teacher> {
            on { checkCandidates(any(), any()) } doReturn emptyList() doReturn null
        }

        val coordinator = MultiThreadedCoordinator(
                listOf(mockLearner1, mockLearner2),
                listOf(mockTeacher1, mockTeacher2)
        )

        coordinator.solveCHCSystem(CHCSystem())

        Thread.sleep(10)

        val learner1Count = mockingDetails(mockLearner1).invocations.size
        val learner2Count = mockingDetails(mockLearner2).invocations.size
        val teacher1Count = mockingDetails(mockTeacher1).invocations.size
        val teacher2Count = mockingDetails(mockTeacher2).invocations.size

        assertTrue("Learners not invoked enough times", learner1Count + learner2Count >= 2)
        assertTrue("Teachers not invoked enough times", teacher1Count >= 2 || teacher2Count >= 2)
    }

    @Test
    fun failingLearnerTest(){
        val mockLearner1 = mock<Learner> {
            on { suggestCandidates(any()) } doThrow Learner.CandidatesNotExpressibleException()
        }
        val mockLearner2 = mock<Learner> {
            on { suggestCandidates(any()) } doReturn GenericCandidates("", False(), emptyMap())
        }
        val mockTeacher1 = mock<Teacher> {
            on { checkCandidates(any(), any()) } doReturn emptyList() doReturn null
        }
        val mockTeacher2 = mock<Teacher> {
            on { checkCandidates(any(), any()) } doReturn emptyList() doReturn null
        }

        val coordinator = MultiThreadedCoordinator(
                listOf(mockLearner1, mockLearner2),
                listOf(mockTeacher1, mockTeacher2)
        )

        coordinator.solveCHCSystem(CHCSystem())

        Thread.sleep(10)

        val learner1Count = mockingDetails(mockLearner1).invocations.size
        val learner2Count = mockingDetails(mockLearner2).invocations.size
        val teacher1Count = mockingDetails(mockTeacher1).invocations.size
        val teacher2Count = mockingDetails(mockTeacher2).invocations.size

        assertTrue("Learner threw exception then invoked again", learner1Count <= 1)
        assertTrue("Learners not invoked enough times", learner2Count >= 2)
        assertTrue("Teachers not invoked enough times", teacher1Count >= 2 || teacher2Count >= 2)
    }

}