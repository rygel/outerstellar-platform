package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.service.MessageService
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

private const val DEFAULT_LIMIT = 10
private const val MAX_LIMIT = 100

class HomeRoutes(
    private val messageService: MessageService,
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
) : ServerRoutes {
    private val queryLens = Query.string().optional("q")
    private val limitLens = Query.int().defaulted("limit", DEFAULT_LIMIT)
    private val offsetLens = Query.int().defaulted("offset", 0)
    private val yearLens = Query.int().optional("year")
    private val syncIdPath = Path.string().of("syncId")

    override val routes =
        listOf(
            "/" meta
                {
                    summary = "Home page"
                    queries += queryLens
                    queries += limitLens
                    queries += offsetLens
                    queries += yearLens
                } bindContract
                GET to
                { request ->
                    val ctx = request.webContext
                    if (ctx.user == null) {
                        return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
                    }
                    val query = queryLens(request)
                    val limit = limitLens(request).coerceIn(1, MAX_LIMIT)
                    val offset = offsetLens(request).coerceAtLeast(0)
                    val year = yearLens(request)
                    renderer.render(pageFactory.buildHomePage(ctx, query, limit, offset, year))
                },
            "/messages/trash" meta
                {
                    summary = "Trash page"
                } bindContract
                GET to
                { request ->
                    val ctx = request.webContext
                    if (ctx.user == null) {
                        return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
                    }
                    renderer.render(pageFactory.buildTrashPage(ctx))
                },
            "/messages" meta
                {
                    summary = "Create message"
                } bindContract
                POST to
                { request ->
                    val ctx = request.webContext
                    if (ctx.user == null) {
                        return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
                    }
                    val author = request.form("author").orEmpty()
                    val content = request.form("content").orEmpty()
                    messageService.createServerMessage(author, content)
                    renderer.render(pageFactory.buildMessageList(ctx))
                },
            "/messages/restore" / syncIdPath meta
                {
                    summary = "Restore deleted message"
                } bindContract
                POST to
                { syncId ->
                    { request: org.http4k.core.Request ->
                        val ctx = request.webContext
                        if (ctx.user == null) {
                            return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
                        }
                        messageService.restore(syncId)
                        Response(Status.FOUND).header("location", ctx.url("/messages/trash"))
                    }
                },
            "/messages/resolve" / syncIdPath meta
                {
                    summary = "Show conflict resolution modal"
                } bindContract
                GET to
                { syncId ->
                    { request: org.http4k.core.Request ->
                        val ctx = request.webContext
                        if (ctx.user == null) {
                            return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
                        }
                        val viewModel = pageFactory.buildConflictResolveModal(ctx, syncId)
                        renderer.render(viewModel)
                    }
                },
            "/messages/resolve" / syncIdPath meta
                {
                    summary = "Resolve sync conflict"
                } bindContract
                POST to
                { syncId ->
                    { request: org.http4k.core.Request ->
                        val ctx = request.webContext
                        if (ctx.user == null) {
                            return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
                        }
                        val strategy = ConflictStrategy.fromString(request.form("strategy") ?: "server")
                        messageService.resolveConflict(syncId, strategy)
                        Response(Status.OK).header("HX-Trigger", "refresh")
                    }
                },
            "/messages" / syncIdPath / "delete" meta
                {
                    summary = "Delete a message"
                } bindContract
                POST to
                { syncId: String, _ ->
                    { request: Request ->
                        val ctx = request.webContext
                        if (ctx.user == null) {
                            return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
                        }
                        messageService.deleteMessage(syncId)
                        Response(Status.OK)
                    }
                },
            "/messages" / syncIdPath / "edit" meta
                {
                    summary = "Show message edit form"
                } bindContract
                GET to
                { syncId: String, _ ->
                    { request: Request ->
                        val ctx = request.webContext
                        if (ctx.user == null) {
                            return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
                        }
                        renderer.render(pageFactory.buildMessageEditForm(ctx, syncId))
                    }
                },
            "/messages" / syncIdPath / "update" meta
                {
                    summary = "Update a message"
                } bindContract
                POST to
                { syncId: String, _ ->
                    { request: Request ->
                        val ctx = request.webContext
                        if (ctx.user == null) {
                            return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
                        }
                        val msg = messageService.findBySyncId(syncId)
                        val author = request.form("author").orEmpty()
                        val content = request.form("content").orEmpty()
                        messageService.updateMessage(msg!!.copy(author = author, content = content))
                        renderer.render(pageFactory.buildMessageList(ctx))
                    }
                },
            "/components/footer-status" meta
                {
                    summary = "Footer status fragment"
                } bindContract
                GET to
                { request ->
                    renderer.render(pageFactory.buildFooterStatus(request.webContext))
                },
        )
}
