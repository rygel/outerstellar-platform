package dev.outerstellar.starter.web

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.PaginationMetadata
import dev.outerstellar.starter.persistence.MessageRepository
import org.http4k.template.ViewModel

data class MessageListViewModel(
    val messages: List<MessageSummary>,
    val emptyMessage: String,
    val deleteUrl: String,
    val restoreUrl: String? = null,
    val pagination: PaginationViewModel? = null,
    val isTrashView: Boolean = false
) : ViewModel

class MessageListComponent(private val repository: MessageRepository) : WebComponent<MessageListViewModel> {
    
    override fun build(ctx: WebContext, vararg args: Any?): MessageListViewModel {
        val query = args.getOrNull(0) as? String
        val limit = args.getOrNull(1) as? Int ?: 10
        val offset = args.getOrNull(2) as? Int ?: 0
        val year = args.getOrNull(3) as? Int
        val isTrash = args.getOrNull(4) as? Boolean ?: false
        
        val i18n = ctx.i18n
        
        val repo = repository as? dev.outerstellar.starter.persistence.JooqMessageRepository
        val items = if (isTrash && repo != null) repo.listDeletedMessages(query, year, limit, offset) 
                    else repository.listMessages(query, year, limit, offset)
        
        val total = if (isTrash && repo != null) repo.countDeletedMessages(query, year)
                    else repository.countMessages(query, year)
        
        val metadata = PaginationMetadata(
            currentPage = (offset / limit) + 1,
            pageSize = limit,
            totalItems = total
        )

        fun createUrl(page: Int): String {
            val newOffset = (page - 1) * limit
            val baseUrl = if (isTrash) "/messages/trash" else "/"
            return ctx.url("$baseUrl?limit=$limit&offset=$newOffset" + 
                (if (query != null) "&query=$query" else "") + 
                (if (year != null) "&year=$year" else ""))
        }

        val pagination = if (metadata.totalPages > 1) {
            PaginationViewModel(
                currentPage = metadata.currentPage,
                totalPages = metadata.totalPages,
                hasPrevious = metadata.hasPrevious,
                hasNext = metadata.hasNext,
                previousUrl = metadata.previousPage?.let { createUrl(it) },
                nextUrl = metadata.nextPage?.let { createUrl(it) },
                pages = (1..metadata.totalPages).map { p ->
                    PageNumberViewModel(p, createUrl(p), p == metadata.currentPage)
                }
            )
        } else null

        return MessageListViewModel(
            messages = items,
            emptyMessage = if (isTrash) "No deleted messages found." else i18n.translate("web.home.list.empty"),
            deleteUrl = ctx.url("/messages"),
            restoreUrl = ctx.url("/messages/restore"),
            pagination = pagination,
            isTrashView = isTrash
        )
    }
}
