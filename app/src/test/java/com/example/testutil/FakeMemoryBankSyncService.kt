package com.example.testutil

import com.example.data.cloud.MemoryBankSyncService
import com.example.data.local.entity.AgentMemoryEntity

class FakeMemoryBankSyncService : MemoryBankSyncService {
    val remembered = mutableListOf<AgentMemoryEntity>()
    val forgotten = mutableListOf<AgentMemoryEntity>()
    var recalled: List<AgentMemoryEntity> = emptyList()
    var lastQuery: String? = null
    var forgotAll = false

    override suspend fun remember(memory: AgentMemoryEntity) {
        remembered += memory
    }

    override suspend fun recall(query: String): List<AgentMemoryEntity> {
        lastQuery = query
        return recalled
    }

    override suspend fun forget(memory: AgentMemoryEntity) {
        forgotten += memory
    }

    override suspend fun forgetAll() {
        forgotAll = true
    }
}
