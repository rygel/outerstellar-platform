package dev.outerstellar.starter.web

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.persistence.MessageRepository
import org.http4k.template.ViewModel

data class MessageListViewModel(
    val messages: List<MessageSummary>,
    val messagesHeading: String,
    val searchQuery: String?,
    val nextOffset: Int?,
    val prevOffset: Int?,
    val limit: Int
) : ViewModel

class MessageListComponent(private val repository: MessageRepository) : WebComponent<MessageListViewModel> {
    override fun build(ctx: WebContext, vararg args: Any?): MessageListViewModel {
        val i18n = ctx.i18n
        val query = args.getOrNull(0) as? String
        val limit = args.getOrNull(1) as? Int ?: 10
        val offset = args.getOrNull(2) as? Int ?: 0

        val messages = repository.listMessages(query, limit, offset)
        val nextOffset = if (messages.size == limit) offset + limit else null
        val prevOffset = if (offset > 0) (offset - limit).coerceAtLeast(0) else null

        return MessageListViewModel(
            messages = messages,
            messagesHeading = i18n.translate("web.home.messages"),
            searchQuery = query,
            nextOffset = nextOffset,
            prevOffset = prevOffset,
            limit = limit
        )
    }
}
