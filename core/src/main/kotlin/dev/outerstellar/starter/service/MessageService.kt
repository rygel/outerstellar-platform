package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.sync.SyncConflict
import dev.outerstellar.starter.sync.SyncMessage
import dev.outerstellar.starter.sync.SyncPushRequest
import dev.outerstellar.starter.sync.SyncPushResponse
import dev.outerstellar.starter.sync.SyncPullResponse
import io.konform.validation.Invalid

class MessageService(private val repository: MessageRepository) {

    fun listMessages(
        query: String? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<MessageSummary> = repository.listMessages(query, limit, offset)

    fun listDirtyMessages(): List<StoredMessage> = repository.listDirtyMessages()

    fun createServerMessage(author: String, content: String): StoredMessage {
        require(author.isNotBlank()) { "Author cannot be blank" }
        require(content.isNotBlank()) { "Content cannot be blank" }
        return repository.createServerMessage(author, content)
    }

    fun createLocalMessage(author: String, content: String): StoredMessage {
        require(author.isNotBlank()) { "Author cannot be blank" }
        require(content.isNotBlank()) { "Content cannot be blank" }
        return repository.createLocalMessage(author, content)
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

        return SyncPushResponse(appliedCount = appliedCount, conflicts = conflicts)
    }
}
