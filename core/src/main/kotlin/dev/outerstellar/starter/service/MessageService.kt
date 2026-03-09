package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.NoOpMessageCache
import dev.outerstellar.starter.sync.SyncConflict
import dev.outerstellar.starter.sync.SyncMessage
import dev.outerstellar.starter.sync.SyncPushRequest
import dev.outerstellar.starter.sync.SyncPushResponse
import dev.outerstellar.starter.sync.SyncPullResponse
import io.konform.validation.Invalid

class MessageService(
    private val repository: MessageRepository,
    private val cache: MessageCache = NoOpMessageCache
) {

    fun listMessages(
        query: String? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<MessageSummary> {
        val cacheKey = "list:$query:$limit:$offset"
        @Suppress("UNCHECKED_CAST")
        val cached = cache.get(cacheKey) as? List<MessageSummary>
        if (cached != null) return cached

        val results = repository.listMessages(query, limit, offset)
        cache.put(cacheKey, results)
        return results
    }

    fun listDirtyMessages(): List<StoredMessage> = repository.listDirtyMessages()

    fun createServerMessage(author: String, content: String): StoredMessage {
        require(author.isNotBlank()) { "Author cannot be blank" }
        require(content.isNotBlank()) { "Content cannot be blank" }
        val message = repository.createServerMessage(author, content)
        cache.invalidateAll()
        return message
    }

    fun createLocalMessage(author: String, content: String): StoredMessage {
        require(author.isNotBlank()) { "Author cannot be blank" }
        require(content.isNotBlank()) { "Content cannot be blank" }
        val message = repository.createLocalMessage(author, content)
        cache.invalidateAll()
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
            throw IllegalArgumentException("Invalid sync request: ${validationResult.errors.joinToString { "${it.dataPath}: ${it.message}" }}")
        }

        val conflicts = mutableListOf<SyncConflict>()
        var appliedCount = 0

        request.messages.forEach { incoming ->
            val current = repository.findBySyncId(incoming.syncId)
            when {
                current == null || incoming.updatedAtEpochMs > current.updatedAtEpochMs -> {
                    repository.upsertSyncedMessage(incoming, dirty = false)
                    appliedCount++
                }
                incoming.updatedAtEpochMs < current.updatedAtEpochMs -> {
                    conflicts += SyncConflict(
                        syncId = incoming.syncId,
                        reason = "Server has a newer version of this message.",
                        serverMessage = current.toSyncMessage()
                    )
                }
            }
        }

        if (appliedCount > 0) {
            cache.invalidateAll()
        }

        return SyncPushResponse(appliedCount = appliedCount, conflicts = conflicts)
    }
}
