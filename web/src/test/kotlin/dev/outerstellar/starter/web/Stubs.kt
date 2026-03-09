package dev.outerstellar.starter.web

import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.OutboxEntry
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import java.util.*

class StubOutboxRepository : OutboxRepository {
    override fun save(entry: OutboxEntry) {}
    override fun fetchUnprocessed(limit: Int): List<OutboxEntry> = emptyList()
    override fun markProcessed(id: UUID) {}
    override fun markFailed(id: UUID, error: String, maxRetries: Int) {}
    override fun softDelete(id: UUID) {}
    override fun countByStatus(status: String): Int = 0
}

class StubMessageCache : MessageCache {
    override fun get(key: String): Any? = null
    override fun put(key: String, value: Any) {}
    override fun invalidate(key: String) {}
    override fun invalidateAll() {}
    override fun getStats(): Map<String, Any> = emptyMap()
}

class StubTransactionManager : TransactionManager {
    override fun <T> inTransaction(block: () -> T): T = block()
}
