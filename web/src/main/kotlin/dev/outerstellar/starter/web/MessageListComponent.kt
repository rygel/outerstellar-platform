package dev.outerstellar.starter.web

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.persistence.MessageRepository
import org.http4k.template.ViewModel

data class MessageListViewModel(
    val messages: List<MessageSummary>,
    val messagesHeading: String,
    val searchQuery: String?,
    val selectedYear: Int?,
    val nextOffset: Int?,
    val prevOffset: Int?,
    val limit: Int,
    val fragmentUrl: String,
    val prevPageUrl: String?,
    val nextPageUrl: String?,
    val searchActionUrl: String
) : ViewModel

class MessageListComponent(private val repository: MessageRepository) : WebComponent<MessageListViewModel> {
    override fun build(ctx: WebContext, vararg args: Any?): MessageListViewModel {
        val i18n = ctx.i18n
        val query = args.getOrNull(0) as? String
        val limit = args.getOrNull(1) as? Int ?: 10
        val offset = args.getOrNull(2) as? Int ?: 0
        val year = args.getOrNull(3) as? Int

        val messages = repository.listMessages(query, year, limit, offset)
        val nextOffset = if (messages.size == limit) offset + limit else null
        val prevOffset = if (offset > 0) (offset - limit).coerceAtLeast(0) else null

        fun buildUrl(newOffset: Int?): String? {
            if (newOffset == null) return null
            val base = ctx.url("/components/message-list")
            val params = mutableListOf("offset=$newOffset", "limit=$limit")
            if (query != null) params.add("q=$query")
            if (year != null) params.add("year=$year")
            return "$base&${params.joinToString("&")}"
        }

        return MessageListViewModel(
            messages = messages,
            messagesHeading = i18n.translate("web.home.messages"),
            searchQuery = query,
            selectedYear = year,
            nextOffset = nextOffset,
            prevOffset = prevOffset,
            limit = limit,
            fragmentUrl = ctx.url("/components/message-list"),
            prevPageUrl = buildUrl(prevOffset),
            nextPageUrl = buildUrl(nextOffset),
            searchActionUrl = ctx.url("/components/message-list")
        )
    }
}
