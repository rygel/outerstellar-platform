package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.model.*
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.persistence.NoOpMessageCache
import dev.outerstellar.starter.sync.SyncConflict
import dev.outerstellar.starter.sync.SyncMessage
import dev.outerstellar.starter.sync.SyncPushRequest
import dev.outerstellar.starter.sync.SyncPushResponse
import dev.outerstellar.starter.sync.SyncPullResponse
import dev.outerstellar.starter.persistence.OutboxEntry
import io.konform.validation.Invalid
import java.util.*

class MessageService(
    private val repository: MessageRepository,
    private val outboxRepository: OutboxRepository? = null,
    private val transactionManager: TransactionManager? = null,
    private val cache: MessageCache = NoOpMessageCache,
    private val eventPublisher: EventPublisher = NoOpEventPublisher
) {

    fun listMessages(
        query: String? = null,
        year: Int? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<MessageSummary> {
        val cacheKey = "list:$query:$year:$limit:$offset"
        @Suppress("UNCHECKED_CAST")
        val cached = cache.get(cacheKey) as? List<MessageSummary>
        if (cached != null) return cached
        
        val results = repository.listMessages(query, year, limit, offset)
        cache.put(cacheKey, results)
        return results
    }

    fun findBySyncId(syncId: String): StoredMessage? {
        val cacheKey = "entity:$syncId"
        val cached = cache.get(cacheKey) as? StoredMessage
        if (cached != null) return cached

        val message = repository.findBySyncId(syncId) ?: throw MessageNotFoundException(syncId)
        cache.put(cacheKey, message)
        return message
    }

    fun listDirtyMessages(): List<StoredMessage> = repository.listDirtyMessages()

    fun createServerMessage(author: String, content: String): StoredMessage {
        val errors = mutableListOf<String>()
        if (author.isBlank()) errors += "Author is required."
        if (content.isBlank()) errors += "Content is required."
        if (errors.isNotEmpty()) throw ValidationException(errors)

        val message = if (transactionManager != null && outboxRepository != null) {
            transactionManager.inTransaction {
                val msg = repository.createServerMessage(author, content)
                outboxRepository.save(
                    OutboxEntry(
                        id = UUID.randomUUID(),
                        payloadType = "MESSAGE_CREATED",
                        payload = msg.syncId
                    )
                )
                msg
            }
        } else {
            repository.createServerMessage(author, content)
        }

        cache.put("entity:${message.syncId}", message)
        cache.invalidateAll() 
        eventPublisher.publishRefresh("message-list-panel")
        return message
    }

    fun createLocalMessage(author: String, content: String): StoredMessage {
        if (author.isBlank() || content.isBlank()) throw ValidationException(listOf("Fields cannot be empty."))
        
        val message = repository.createLocalMessage(author, content)
        cache.put("entity:${message.syncId}", message)
        cache.invalidateAll()
        eventPublisher.publishRefresh("message-list-panel")
        return message
    }

    fun getChangesSince(since: Long): SyncPullResponse {
        val changes = repository.findChangesSince(since).map { it.toSyncMessage() }
        return SyncPullResponse(
            messages = changes,
            serverTimestamp = System.currentTimeMillis()
        )
    }

    fun processPushRequest(request: SyncPushRequest): SyncPushResponse {
        val validationResult = SyncPushRequest.validate(request)
        if (validationResult is Invalid) {
            throw ValidationException(validationResult.errors.map { "${it.dataPath}: ${it.message}" })
        }

        val conflicts = mutableListOf<SyncConflict>()
        var appliedCount = 0

        request.messages.forEach { incoming ->
            val current = try { findBySyncId(incoming.syncId) } catch (e: MessageNotFoundException) { null }
            when {
                current == null || incoming.updatedAtEpochMs > current.updatedAtEpochMs -> {
                    val updated = repository.upsertSyncedMessage(incoming, dirty = false)
                    cache.put("entity:${updated.syncId}", updated)
                    appliedCount++
                }
                incoming.updatedAtEpochMs < current.updatedAtEpochMs -> {
                    conflicts += SyncConflict(
                        syncId = incoming.syncId,
                        reason = "Server has a newer version.",
                        serverMessage = current.toSyncMessage()
                    )
                }
            }
        }

        if (appliedCount > 0) {
            cache.invalidateAll()
            eventPublisher.publishRefresh("message-list-panel")
        }

        return SyncPushResponse(appliedCount = appliedCount, conflicts = conflicts)
    }

    fun deleteMessage(syncId: String) {
        if (transactionManager != null && outboxRepository != null) {
            transactionManager.inTransaction {
                repository.softDelete(syncId)
                outboxRepository.save(
                    OutboxEntry(
                        id = UUID.randomUUID(),
                        payloadType = "MESSAGE_DELETED",
                        payload = syncId
                    )
                )
            }
        } else {
            repository.softDelete(syncId)
        }
        cache.invalidate("entity:$syncId")
        cache.invalidateAll()
        eventPublisher.publishRefresh("message-list-panel")
    }

    fun updateMessage(message: StoredMessage): StoredMessage {
        val updated = if (transactionManager != null && outboxRepository != null) {
            transactionManager.inTransaction {
                val up = repository.updateMessage(message)
                outboxRepository.save(
                    OutboxEntry(
                        id = UUID.randomUUID(),
                        payloadType = "MESSAGE_UPDATED",
                        payload = up.syncId
                    )
                )
                up
            }
        } else {
            repository.updateMessage(message)
        }
        cache.put("entity:${updated.syncId}", updated)
        cache.invalidateAll()
        eventPublisher.publishRefresh("message-list-panel")
        return updated
    }
}
