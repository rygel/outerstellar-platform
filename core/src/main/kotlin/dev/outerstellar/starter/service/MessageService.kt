package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.ConflictStrategy
import dev.outerstellar.starter.model.MessageNotFoundException
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.PagedResult
import dev.outerstellar.starter.model.PaginationMetadata
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.model.ValidationException
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.NoOpMessageCache
import dev.outerstellar.starter.persistence.OutboxEntry
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.sync.SyncConflict
import dev.outerstellar.starter.sync.SyncMessage
import dev.outerstellar.starter.sync.SyncPullResponse
import dev.outerstellar.starter.sync.SyncPushRequest
import dev.outerstellar.starter.sync.SyncPushResponse
import io.konform.validation.Invalid
import java.util.UUID
import org.http4k.format.Jackson
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
class MessageService(
    private val repository: MessageRepository,
    private val outboxRepository: OutboxRepository? = null,
    private val transactionManager: TransactionManager? = null,
    private val cache: MessageCache = NoOpMessageCache,
    private val eventPublisher: EventPublisher = NoOpEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(MessageService::class.java)

    fun listMessages(
        query: String? = null,
        year: Int? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): PagedResult<MessageSummary> {
        val cacheKey = "list:$query:$year:$limit:$offset"

        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(cacheKey) {
            val items = repository.listMessages(query, year, limit, offset)
            val total = repository.countMessages(query, year)
            PagedResult(
                items = items,
                metadata =
                    PaginationMetadata(
                        currentPage = (offset / limit) + 1,
                        pageSize = limit,
                        totalItems = total,
                    ),
            )
        } as PagedResult<MessageSummary>
    }

    fun listDeletedMessages(
        query: String? = null,
        year: Int? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): PagedResult<MessageSummary> {
        val items = repository.listMessages(query, year, limit, offset, includeDeleted = true)
        val total = repository.countMessages(query, year, includeDeleted = true)

        return PagedResult(
            items = items,
            metadata =
                PaginationMetadata(
                    currentPage = (offset / limit) + 1,
                    pageSize = limit,
                    totalItems = total,
                ),
        )
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

        val tm = transactionManager
        val outbox = outboxRepository

        val message =
            if (tm != null && outbox != null) {
                tm.inTransaction {
                    val msg = repository.createServerMessage(author, content)
                    outbox.save(
                        OutboxEntry(
                            id = UUID.randomUUID(),
                            payloadType = "MESSAGE_CREATED",
                            payload = msg.syncId,
                            status = "PENDING",
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
        if (author.isBlank() || content.isBlank()) {
            throw ValidationException(listOf("Fields cannot be empty."))
        }

        val message = repository.createLocalMessage(author, content)
        cache.put("entity:${message.syncId}", message)
        cache.invalidateAll()
        eventPublisher.publishRefresh("message-list-panel")
        return message
    }

    fun getChangesSince(since: Long): SyncPullResponse {
        val changes = repository.findChangesSince(since).map { it.toSyncMessage() }
        return SyncPullResponse(messages = changes, serverTimestamp = System.currentTimeMillis())
    }

    fun processPushRequest(request: SyncPushRequest): SyncPushResponse {
        val validationResult = SyncPushRequest.validate(request)
        if (validationResult is Invalid) {
            throw ValidationException(
                validationResult.errors.map { "${it.dataPath}: ${it.message}" }
            )
        }

        val conflicts = mutableListOf<SyncConflict>()
        var appliedCount = 0

        request.messages.forEach { incoming ->
            val current =
                try {
                    findBySyncId(incoming.syncId)
                } catch (e: MessageNotFoundException) {
                    logger.trace(
                        "No existing message found for syncId: {}. Exception: {}",
                        incoming.syncId,
                        e.message,
                    )
                    null
                }
            when {
                current == null || incoming.updatedAtEpochMs >= current.updatedAtEpochMs -> {
                    val updated = repository.upsertSyncedMessage(incoming, dirty = false)
                    cache.put("entity:${updated.syncId}", updated)
                    appliedCount++
                }
                else -> {
                    repository.markConflict(incoming.syncId, incoming)
                    conflicts +=
                        SyncConflict(
                            syncId = incoming.syncId,
                            reason = "Server has a newer version.",
                            serverMessage = current.toSyncMessage(),
                        )
                }
            }
        }

        if (appliedCount > 0 || conflicts.isNotEmpty()) {
            cache.invalidateAll()
            eventPublisher.publishRefresh("message-list-panel")
        }

        return SyncPushResponse(appliedCount = appliedCount, conflicts = conflicts)
    }

    fun deleteMessage(syncId: String) {
        val tm = transactionManager
        val outbox = outboxRepository

        if (tm != null && outbox != null) {
            tm.inTransaction {
                repository.softDelete(syncId)
                outbox.save(
                    OutboxEntry(
                        id = UUID.randomUUID(),
                        payloadType = "MESSAGE_DELETED",
                        payload = syncId,
                        status = "PENDING",
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
        val tm = transactionManager
        val outbox = outboxRepository

        val updated =
            if (tm != null && outbox != null) {
                tm.inTransaction {
                    val up = repository.updateMessage(message)
                    outbox.save(
                        OutboxEntry(
                            id = UUID.randomUUID(),
                            payloadType = "MESSAGE_UPDATED",
                            payload = up.syncId,
                            status = "PENDING",
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

    fun resolveConflict(syncId: String, strategy: ConflictStrategy) {
        val current = repository.findBySyncId(syncId) ?: throw MessageNotFoundException(syncId)
        val currentConflict = current.syncConflict ?: return

        val serverVersion = Jackson.asA(currentConflict, SyncMessage::class)

        val resolved =
            if (strategy == ConflictStrategy.MINE) {
                current.copy(dirty = true, syncConflict = null)
            } else {
                current.copy(
                    author = serverVersion.author,
                    content = serverVersion.content,
                    updatedAtEpochMs = serverVersion.updatedAtEpochMs,
                    dirty = false,
                    syncConflict = null,
                )
            }

        repository.resolveConflict(syncId, resolved)
        cache.invalidateAll()
        eventPublisher.publishRefresh("message-list-panel")
    }
}
