package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.StoredMessage

interface MessageCache {
    fun getMessage(syncId: String): StoredMessage?

    fun putMessage(syncId: String, message: StoredMessage)

    fun getMessageList(key: String): PagedResult<MessageSummary>?

    fun putMessageList(key: String, result: PagedResult<MessageSummary>)

    fun getMessageListOrPut(key: String, loader: () -> PagedResult<MessageSummary>): PagedResult<MessageSummary>

    fun invalidate(key: String)

    fun invalidateAll()

    fun invalidateNamespace(namespace: String) = invalidateAll()

    fun getStats(): Map<String, Any>
}

object NoOpMessageCache : MessageCache {
    override fun getMessage(syncId: String): StoredMessage? = null

    override fun putMessage(syncId: String, message: StoredMessage) = Unit

    override fun getMessageList(key: String): PagedResult<MessageSummary>? = null

    override fun putMessageList(key: String, result: PagedResult<MessageSummary>) = Unit

    override fun getMessageListOrPut(
        key: String,
        loader: () -> PagedResult<MessageSummary>,
    ): PagedResult<MessageSummary> = loader()

    override fun invalidate(key: String) = Unit

    override fun invalidateAll() = Unit

    override fun invalidateNamespace(namespace: String) = Unit

    override fun getStats(): Map<String, Any> = emptyMap()
}
