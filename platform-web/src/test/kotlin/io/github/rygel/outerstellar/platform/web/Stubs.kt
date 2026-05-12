package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.OutboxEntry
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxStatus
import io.github.rygel.outerstellar.platform.security.DeviceToken
import io.github.rygel.outerstellar.platform.security.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.security.OAuthConnection
import io.github.rygel.outerstellar.platform.security.OAuthRepository
import io.github.rygel.outerstellar.platform.security.Session
import io.github.rygel.outerstellar.platform.security.SessionRepository
import java.time.Instant
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

    override fun getStats(): Map<OutboxStatus, Int> = emptyMap()

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

    override fun invalidateByPrefix(prefix: String) {
        // test stub — no-op
    }

    override fun getStats(): Map<String, Any> = emptyMap()
}

class StubTransactionManager : io.github.rygel.outerstellar.platform.persistence.TransactionManager {
    override fun <T> inTransaction(block: () -> T): T = block()
}

/** In-memory OAuthRepository for tests. Thread-safe enough for sequential tests. */
class InMemoryOAuthRepository : OAuthRepository {
    private val connections = mutableListOf<OAuthConnection>()

    override fun findByProviderSubject(provider: String, subject: String): OAuthConnection? = connections.find {
        it.provider == provider && it.subject == subject
    }

    override fun save(connection: OAuthConnection) {
        connections.removeAll { it.provider == connection.provider && it.subject == connection.subject }
        connections.add(connection.copy(id = connections.size.toLong() + 1))
    }

    override fun findByUserId(userId: UUID): List<OAuthConnection> = connections.filter { it.userId == userId }

    override fun delete(id: Long, userId: UUID) {
        connections.removeAll { it.id == id && it.userId == userId }
    }

    fun clear() = connections.clear()
}

/** In-memory SessionRepository for tests. */
class InMemorySessionRepository : SessionRepository {
    private val sessions = mutableMapOf<String, Session>()
    private var nextId = 1L

    override fun save(session: Session) {
        sessions[session.tokenHash] = session.copy(id = nextId++)
    }

    override fun findByTokenHash(tokenHash: String): Session? =
        sessions[tokenHash]?.takeIf { it.expiresAt.isAfter(Instant.now()) }

    override fun findByTokenHashIncludingExpired(tokenHash: String): Session? = sessions[tokenHash]

    override fun updateExpiresAt(tokenHash: String, expiresAt: Instant) {
        sessions[tokenHash]?.let { sessions[tokenHash] = it.copy(expiresAt = expiresAt) }
    }

    override fun deleteByTokenHash(tokenHash: String) {
        sessions.remove(tokenHash)
    }

    override fun deleteByUserId(userId: UUID) {
        sessions.entries.removeAll { it.value.userId == userId }
    }

    override fun deleteExpired() {
        val now = Instant.now()
        sessions.entries.removeAll { it.value.expiresAt.isBefore(now) }
    }

    fun clear() = sessions.clear()
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

    override fun findByUserId(userId: UUID): List<DeviceToken> = tokens.values.filter { it.userId == userId }

    override fun deleteAllForUser(userId: UUID) {
        tokens.values.removeAll { it.userId == userId }
    }

    fun clear() = tokens.clear()

    fun all(): Collection<DeviceToken> = tokens.values
}

/** Generates a dynamic test password to avoid hardcoded credentials in source. */
fun testPassword(): String = "test-" + java.util.UUID.randomUUID().toString().take(12)
