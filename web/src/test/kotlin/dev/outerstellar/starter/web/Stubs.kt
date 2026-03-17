package dev.outerstellar.starter.web

import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.OutboxEntry
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.security.DeviceToken
import dev.outerstellar.starter.security.DeviceTokenRepository
import dev.outerstellar.starter.security.OAuthConnection
import dev.outerstellar.starter.security.OAuthRepository
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

/** In-memory OAuthRepository for tests. Thread-safe enough for sequential tests. */
class InMemoryOAuthRepository : OAuthRepository {
    private val connections = mutableListOf<OAuthConnection>()

    override fun findByProviderSubject(provider: String, subject: String): OAuthConnection? =
        connections.find { it.provider == provider && it.subject == subject }

    override fun save(connection: OAuthConnection) {
        connections.removeAll {
            it.provider == connection.provider && it.subject == connection.subject
        }
        connections.add(connection.copy(id = connections.size.toLong() + 1))
    }

    override fun findByUserId(userId: UUID): List<OAuthConnection> =
        connections.filter { it.userId == userId }

    override fun delete(id: Long, userId: UUID) {
        connections.removeAll { it.id == id && it.userId == userId }
    }

    fun clear() = connections.clear()
}

/** In-memory DeviceTokenRepository for tests. */
class InMemoryDeviceTokenRepository : DeviceTokenRepository {
    private val tokens = mutableMapOf<String, DeviceToken>()

    override fun upsert(deviceToken: DeviceToken) {
        tokens[deviceToken.token] = deviceToken
    }

    override fun delete(token: String) {
        tokens.remove(token)
    }

    override fun findByUserId(userId: UUID): List<DeviceToken> =
        tokens.values.filter { it.userId == userId }

    override fun deleteAllForUser(userId: UUID) {
        tokens.values.removeAll { it.userId == userId }
    }

    fun clear() = tokens.clear()

    fun all(): Collection<DeviceToken> = tokens.values
}
