package dev.outerstellar.starter.web

import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.OutboxEntry
import dev.outerstellar.starter.persistence.OutboxRepository
import java.util.UUID

class StubOutboxRepository : OutboxRepository {
    override fun save(entry: OutboxEntry) {
        // Stub implementation
    }
    override fun listPending(limit: Int): List<OutboxEntry> = emptyList()
    override fun markProcessed(id: UUID) {
        // Stub implementation
    }
    override fun markFailed(id: UUID, error: String) {
        // Stub implementation
    }
    override fun getStats(): Map<String, Int> = emptyMap()
    override fun listFailed(): List<OutboxEntry> = emptyList()
}

class StubMessageCache : MessageCache {
    override fun get(key: String): Any? = null
    override fun put(key: String, value: Any) {
        // Stub implementation
    }
    override fun invalidate(key: String) {
        // Stub implementation
    }
    override fun invalidateAll() {
        // Stub implementation
    }
    override fun getStats(): Map<String, Any> = emptyMap()
}

class StubTransactionManager : dev.outerstellar.starter.persistence.TransactionManager {
    override fun <T> inTransaction(block: () -> T): T = block()
}
