package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.service.MessageService
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

private const val DEFAULT_LIMIT = 10

class HomeRoutes(
    private val messageService: MessageService,
    private val repository: MessageRepository,
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer
) : ServerRoutes {
    private val queryLens = Query.string().optional("q")
    private val limitLens = Query.int().defaulted("limit", DEFAULT_LIMIT)
    private val offsetLens = Query.int().defaulted("offset", 0)
    private val yearLens = Query.int().optional("year")
    private val syncIdPath = Path.string().of("syncId")

    override val routes = listOf(
        "/" meta {
            summary = "Home page"
            queries += queryLens
            queries += limitLens
            queries += offsetLens
            queries += yearLens
        } bindContract GET to { request ->
            val query = queryLens(request)
            val limit = limitLens(request)
            val offset = offsetLens(request)
            val year = yearLens(request)
            renderer.render(pageFactory.buildHomePage(request.webContext, query, limit, offset, year))
        },
        "/messages/trash" meta {
            summary = "Trash page"
        } bindContract GET to { request ->
            val shell = request.webContext.shell("Trash", "/messages/trash")
            val messageList = pageFactory.buildMessageList(request.webContext, isTrash = true)
            renderer.render(Page(shell, messageList))
        },
        "/messages" meta {
            summary = "Create message"
        } bindContract POST to { request ->
            val author = request.form("author").orEmpty()
            val content = request.form("content").orEmpty()
            messageService.createServerMessage(author, content)
            Response(Status.FOUND).header("location", request.webContext.url("/"))
        },
        "/messages/restore" / syncIdPath meta {
            summary = "Restore deleted message"
        } bindContract POST to { syncId ->
            {
                    request: org.http4k.core.Request ->
                repository.restore(syncId)
                Response(Status.FOUND).header("location", request.webContext.url("/messages/trash"))
            }
        },
        "/messages/resolve" / syncIdPath meta {
            summary = "Show conflict resolution modal"
        } bindContract GET to { syncId ->
            {
                    request: org.http4k.core.Request ->
                val viewModel = pageFactory.buildConflictResolveModal(request.webContext, syncId)
                renderer.render(viewModel)
            }
        },
        "/messages/resolve" / syncIdPath meta {
            summary = "Resolve sync conflict"
        } bindContract POST to { syncId ->
            {
                    request: org.http4k.core.Request ->
                val strategy = request.form("strategy") ?: "server"
                messageService.resolveConflict(syncId, strategy)
                Response(Status.OK).header("HX-Trigger", "refresh")
            }
        },
        "/components/footer-status" meta {
            summary = "Footer status fragment"
        } bindContract GET to { request ->
            renderer.render(pageFactory.buildFooterStatus(request.webContext))
        }
    )
}
