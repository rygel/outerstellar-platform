package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.sync.SyncMessage

@Suppress("TooManyFunctions")
interface MessageRepository {
    fun listMessages(
        query: String? = null,
        year: Int? = null,
        limit: Int = 100,
        offset: Int = 0,
        includeDeleted: Boolean = false
    ): List<MessageSummary>

    fun countMessages(query: String? = null, year: Int? = null, includeDeleted: Boolean = false): Long

    fun listDirtyMessages(): List<StoredMessage>

    fun findBySyncId(syncId: String): StoredMessage?

    fun createServerMessage(author: String, content: String): StoredMessage

    fun createLocalMessage(author: String, content: String): StoredMessage

    fun upsertSyncedMessage(message: SyncMessage, dirty: Boolean): StoredMessage

    fun findChangesSince(since: Long): List<StoredMessage>

    fun getLastSyncEpochMs(): Long

    fun setLastSyncEpochMs(value: Long)

    fun seedStarterMessages()

    fun softDelete(syncId: String)

    fun restore(syncId: String)

    fun updateMessage(message: StoredMessage): StoredMessage

    fun markConflict(syncId: String, serverVersion: SyncMessage)

    fun resolveConflict(syncId: String, resolvedMessage: StoredMessage)

    fun markClean(syncIds: Collection<String>)
}
