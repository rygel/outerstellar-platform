package io.github.rygel.outerstellar.platform.search

import io.github.rygel.outerstellar.platform.service.MessageService

class MessageSearchProvider(private val messageService: MessageService?) : SearchProvider {
    override val type: String = "message"

    companion object {
        private const val MAX_SEARCH_LIMIT = 50
        private const val MAX_PREVIEW_LENGTH = 120
    }

    override fun search(query: String, limit: Int): List<SearchResult> {
        if (query.isBlank() || messageService == null) return emptyList()
        return messageService
            .listMessages(query = query, limit = limit.coerceIn(1, MAX_SEARCH_LIMIT), offset = 0)
            .items
            .map { msg ->
                SearchResult(
                    id = msg.syncId,
                    title = msg.author,
                    subtitle = msg.content.take(MAX_PREVIEW_LENGTH),
                    url = "/",
                    type = "message",
                    score = if (msg.content.contains(query, ignoreCase = true)) 1.0 else 0.8,
                )
            }
    }
}
