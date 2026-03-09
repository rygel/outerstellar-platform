package dev.outerstellar.starter.web

import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.contract.div
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

class ErrorRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer
) : ServerRoutes {
    private val htmlContentType = ContentType.TEXT_HTML.toHeaderValue()
    private val kindPath = Path.string().of("kind")

    override val routes = listOf(
        "/errors/not-found" meta {
            summary = "Not found error page"
        } bindContract GET to { request ->
            val ctx = WebContext(request)
            htmlResponse(Status.OK, renderer(pageFactory.buildErrorPage(ctx, "not-found")))
        },
        "/errors/server-error" meta {
            summary = "Server error page"
        } bindContract GET to { request ->
            val ctx = WebContext(request)
            htmlResponse(Status.INTERNAL_SERVER_ERROR, renderer(pageFactory.buildErrorPage(ctx, "server-error")))
        },
        "/errors/components/help" / kindPath meta {
            summary = "Error help component"
        } bindContract GET to { kind ->
            { request ->
                val ctx = WebContext(request)
                htmlResponse(Status.OK, renderer(pageFactory.buildErrorHelp(ctx, kind)))
            }
        }
    )

    private fun htmlResponse(status: Status, body: String): Response =
        Response(status).header("content-type", htmlContentType).body(body)
}
