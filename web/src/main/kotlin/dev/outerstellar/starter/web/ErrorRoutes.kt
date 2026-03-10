package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
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
    private val kindPath = Path.string().of("kind")

    override val routes = listOf(
        "/errors/not-found" meta {
            summary = "Not found error page"
        } bindContract GET to { request: org.http4k.core.Request ->
            renderer.render(pageFactory.buildErrorPage(request.webContext, "not-found"))
        },
        "/errors/server-error" meta {
            summary = "Server error page"
        } bindContract GET to { request: org.http4k.core.Request ->
            renderer.render(pageFactory.buildErrorPage(request.webContext, "server-error"), Status.INTERNAL_SERVER_ERROR)
        },
        "/errors/components/help" / kindPath meta {
            summary = "Error help component"
        } bindContract GET to { kind ->
            { request: org.http4k.core.Request ->
                renderer.render(pageFactory.buildErrorHelp(request.webContext, kind))
            }
        }
    )
}
