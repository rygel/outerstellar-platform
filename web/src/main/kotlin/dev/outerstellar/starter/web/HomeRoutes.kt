package dev.outerstellar.starter.web

import dev.outerstellar.starter.service.MessageService
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.getFirst
import org.http4k.core.toParametersMap
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

class HomeRoutes(
    private val messageService: MessageService,
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer
) : ServerRoutes {
    private val queryLens = Query.string().optional("q")
    private val yearLens = Query.int().optional("year")
    private val limitLens = Query.int().defaulted("limit", 10)
    private val offsetLens = Query.int().defaulted("offset", 0)

    private val defaultAuthor = "Server"
    private val contentRequiredMessage = "Content is required."
    private val htmlContentType = ContentType.TEXT_HTML.toHeaderValue()

    override val routes = listOf(
        "/" meta {
            summary = "Home page"
            queries += queryLens
            queries += yearLens
            queries += limitLens
            queries += offsetLens
        } bindContract GET to { request ->
            val ctx = WebContext(request)
            val query = queryLens(request)
            val year = yearLens(request)
            val limit = limitLens(request)
            val offset = offsetLens(request)
            htmlResponse(Status.OK, renderer(pageFactory.buildHomePage(ctx, query, limit, offset, year)))
        },
        "/messages" meta {
            summary = "Create message"
        } bindContract POST to { request ->
            val ctx = WebContext(request)
            val parameters = request.form().toParametersMap()
            val author = parameters.getFirst("author").takeUnless { it.isNullOrBlank() } ?: defaultAuthor
            val content = parameters.getFirst("content")?.trim().orEmpty()

            if (content.isBlank()) {
                Response(Status.BAD_REQUEST).body(contentRequiredMessage)
            } else {
                messageService.createServerMessage(author, content)
                Response(Status.FOUND).header("location", ctx.url("/"))
            }
        }
    )

    private fun htmlResponse(status: Status, body: String): Response =
        Response(status).header("content-type", htmlContentType).body(body)
}
