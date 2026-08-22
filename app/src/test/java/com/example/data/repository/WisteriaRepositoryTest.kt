package com.example.data.repository

import com.example.data.local.entity.CareActionEntity
import com.example.domain.agent.model.CycleTexture
import com.example.domain.agent.model.DailyPulseData
import com.example.testutil.FakeCheckInDao
import com.example.testutil.FakeCloudRunWorkflowService
import com.example.testutil.FakeFirestoreSyncService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WisteriaRepositoryTest {

    @Test
    fun `saveDailyCheckIn starts as pending sync, not already synced`() = runTest {
        val dao = FakeCheckInDao()
        val repository = WisteriaRepository(dao, FakeFirestoreSyncService(), FakeCloudRunWorkflowService())

        val entity = repository.saveDailyCheckIn(DailyPulseData(ratingValue = 4, texture = CycleTexture.FEELING_GOOD))

        // Regression: this used to be hardcoded to "SYNCED_TO_FIRESTORE" with a fake path,
        // even when nothing had actually been synced yet.
        assertEquals("PENDING_SYNC", entity.syncStatus)
        assertNull(entity.firestoreDocPath)
        assertEquals(repository.getTodayDateString(), entity.date)
    }

    @Test
    fun `manual firestore sync marks the existing check-in as synced`() = runTest {
        val dao = FakeCheckInDao()
        val repository = WisteriaRepository(dao, FakeFirestoreSyncService(), FakeCloudRunWorkflowService())
        repository.saveDailyCheckIn(DailyPulseData(ratingValue = 4))

        val record = repository.triggerManualFirestoreSync(DailyPulseData(ratingValue = 4))

        val stored = repository.getAllCheckInsFlow().first().first()
        assertEquals("SYNCED_TO_FIRESTORE", stored.syncStatus)
        assertEquals(record.documentPath, stored.firestoreDocPath)
    }

    @Test
    fun `manual firestore sync without a prior local check-in does not crash`() = runTest {
        val dao = FakeCheckInDao()
        val repository = WisteriaRepository(dao, FakeFirestoreSyncService(), FakeCloudRunWorkflowService())

        val record = repository.triggerManualFirestoreSync(DailyPulseData())

        assertNotNull(record)
        assertTrue(repository.getAllCheckInsFlow().first().isEmpty())
    }

    @Test
    fun `the agent's automatic firestore sync updates the row created moments earlier`() = runTest {
        val dao = FakeCheckInDao()
        val firestore = FakeFirestoreSyncService()
        val repository = WisteriaRepository(dao, firestore, FakeCloudRunWorkflowService())

        repository.checkInAgent.processUserTurn(
            userPrompt = "2",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        val stored = repository.getAllCheckInsFlow().first().first()
        assertEquals("SYNCED_TO_FIRESTORE", stored.syncStatus)
        assertEquals(1, firestore.syncCallCount)
        assertEquals(2, firestore.syncedPulses.first().ratingValue)
    }

    @Test
    fun `toggleCareAction delegates to the dao`() = runTest {
        val dao = FakeCheckInDao()
        val repository = WisteriaRepository(dao, FakeFirestoreSyncService(), FakeCloudRunWorkflowService())
        dao.insertCareAction(
            CareActionEntity(id = "care1", checkInId = "checkin_1", title = "t", type = "NERVE_TONIC", description = "d")
        )

        repository.toggleCareAction("care1", true)

        assertTrue(dao.getAllCareActionsFlow().first().first { it.id == "care1" }.isCompleted)
    }

    @Test
    fun `getCycleMemorySnapshot delegates to the firestore service`() = runTest {
        val dao = FakeCheckInDao()
        val phases = listOf(mapOf("phase" to "Test Phase"))
        val repository = WisteriaRepository(dao, FakeFirestoreSyncService(phaseSnapshot = phases), FakeCloudRunWorkflowService())

        assertEquals(phases, repository.getCycleMemorySnapshot())
    }

    @Test
    fun `manual cloud run sync delegates to the cloud run service`() = runTest {
        val dao = FakeCheckInDao()
        val cloudRun = FakeCloudRunWorkflowService()
        val repository = WisteriaRepository(dao, FakeFirestoreSyncService(), cloudRun)

        repository.triggerManualCloudRunSync()

        assertEquals(listOf("pmdd_pattern_analysis"), cloudRun.dispatchedWorkflows)
    }
}
