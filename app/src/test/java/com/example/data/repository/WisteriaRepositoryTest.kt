package com.example.data.repository

import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.AgentMemoryEntity
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.DailyTexture
import com.example.testutil.FakeCheckInDao
import com.example.testutil.FakeFirestoreSyncService
import com.example.testutil.FakeNightShiftService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WisteriaRepositoryTest {

    @Test
    fun `save starts local and pending optional sync`() = runTest {
        val repository = WisteriaRepository(
            FakeCheckInDao(),
            FakeFirestoreSyncService(),
            FakeNightShiftService()
        )

        val entity = repository.saveDailyCheckIn(
            DailyPulseData(ratingValue = 4, texture = DailyTexture.BRIGHT)
        )

        assertEquals("PENDING_SYNC", entity.syncStatus)
        assertNull(entity.firestoreDocPath)
        assertNull(entity.nightShiftRunId)
        assertEquals(repository.getTodayDateString(), entity.date)
    }

    @Test
    fun `manual firestore sync marks the existing check-in as synced`() = runTest {
        val dao = FakeCheckInDao()
        val repository = WisteriaRepository(dao, FakeFirestoreSyncService(), FakeNightShiftService())
        repository.saveDailyCheckIn(DailyPulseData(ratingValue = 4, texture = DailyTexture.BRIGHT))

        val record = repository.triggerManualFirestoreSync()

        val stored = repository.getAllCheckInsFlow().first().first()
        assertEquals("SYNCED_TO_FIRESTORE", stored.syncStatus)
        assertEquals(record.documentPath, stored.firestoreDocPath)
    }

    @Test
    fun `manual firestore sync requires a real local check-in`() = runTest {
        val repository = WisteriaRepository(
            FakeCheckInDao(),
            FakeFirestoreSyncService(),
            FakeNightShiftService()
        )

        var message: String? = null
        try {
            repository.triggerManualFirestoreSync()
        } catch (error: IllegalStateException) {
            message = error.message
        }

        assertEquals("Complete a check-in before syncing", message)
        assertTrue(repository.getAllCheckInsFlow().first().isEmpty())
    }

    @Test
    fun `a normal check-in does not sync until the user asks`() = runTest {
        val dao = FakeCheckInDao()
        val firestore = FakeFirestoreSyncService()
        val repository = WisteriaRepository(dao, firestore, FakeNightShiftService())

        repository.checkInAgent.processUserTurn(
            userPrompt = "2 (Heavy)",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        val stored = repository.getAllCheckInsFlow().first().first()
        assertEquals("PENDING_SYNC", stored.syncStatus)
        assertEquals(0, firestore.syncCallCount)
    }

    @Test
    fun `toggle idea delegates to the dao`() = runTest {
        val dao = FakeCheckInDao()
        val repository = WisteriaRepository(dao, FakeFirestoreSyncService(), FakeNightShiftService())
        dao.insertCareAction(
            CareActionEntity(
                id = "idea1",
                checkInId = "checkin_1",
                title = "One less decision",
                type = "SIMPLIFY",
                description = "Leave one choice for later."
            )
        )

        repository.toggleCareAction("idea1", true)

        assertTrue(dao.getAllCareActionsFlow().first().first { it.id == "idea1" }.isCompleted)
    }

    @Test
    fun `texture summary delegates to firestore`() = runTest {
        val summary = listOf(mapOf("textureTitle" to "Heavy days"))
        val repository = WisteriaRepository(
            FakeCheckInDao(),
            FakeFirestoreSyncService(textureSnapshot = summary),
            FakeNightShiftService()
        )

        assertEquals(summary, repository.getTextureSummary())
    }

    @Test
    fun `night shift receives local history and saves a pattern note`() = runTest {
        val dao = FakeCheckInDao()
        val nightShift = FakeNightShiftService()
        val repository = WisteriaRepository(dao, FakeFirestoreSyncService(), nightShift)
        repository.saveDailyCheckIn(
            DailyPulseData(ratingValue = 1, texture = DailyTexture.OFF, isOffDay = true)
        )

        repository.runNightShift()

        assertEquals(1, nightShift.receivedHistory.single().size)
        assertEquals(DailyTexture.OFF, nightShift.receivedHistory.single().first().texture)
        assertEquals("DAILY_PATTERN", repository.getAllMemoriesFlow().first().single().category)
    }

    @Test
    fun `conversation notes can be reviewed and forgotten without deleting pattern notes`() = runTest {
        val repository = WisteriaRepository(
            FakeCheckInDao(),
            FakeFirestoreSyncService(),
            FakeNightShiftService()
        )
        repository.saveConversationMemory(
            AgentMemoryEntity(
                memoryKey = "conversation_work",
                memoryValue = "Work has been overwhelming this week",
                category = "CONVERSATION_CONTEXT"
            )
        )
        repository.runNightShift()

        assertEquals(1, repository.getConversationMemories().size)
        repository.deleteConversationMemories()

        val remaining = repository.getAllMemoriesFlow().first()
        assertTrue(remaining.none { it.category.startsWith("CONVERSATION_") })
        assertTrue(remaining.any { it.category == "DAILY_PATTERN" })
    }

    @Test
    fun `demo history is clearly labeled local data and produces a learned transition`() = runTest {
        val dao = FakeCheckInDao()
        val repository = WisteriaRepository(
            dao,
            FakeFirestoreSyncService(),
            FakeNightShiftService()
        )

        val count = repository.loadDemoHistory()
        val run = repository.runNightShift()
        val rows = repository.getAllCheckInsFlow().first()

        assertEquals(10, count)
        assertEquals(10, rows.size)
        assertTrue(rows.all { it.singleInputResponse.startsWith("Demo:") })
        assertTrue(rows.all { it.syncStatus == "DEMO_LOCAL_ONLY" })
        assertTrue(run.morningBrief.learnedTransitionCount > 0)
    }

    @Test
    fun `demo history stays behind todays real check-in so it can still sync`() = runTest {
        val dao = FakeCheckInDao()
        val firestore = FakeFirestoreSyncService()
        val repository = WisteriaRepository(dao, firestore, FakeNightShiftService())
        repository.saveDailyCheckIn(
            DailyPulseData(
                ratingValue = 1,
                texture = DailyTexture.OFF,
                textureLabel = "Off",
                singleInputResponse = "I feel off",
                agentAcknowledgment = "I'm here with you.",
                isOffDay = true
            )
        )

        repository.loadDemoHistory()
        repository.triggerManualFirestoreSync()

        assertEquals("I feel off", firestore.syncedPulses.single().singleInputResponse)
    }
}
