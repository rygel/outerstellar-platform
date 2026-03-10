package dev.outerstellar.starter.web

import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

private const val DEFAULT_LIMIT = 10

class ComponentRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer
) : ServerRoutes {
    private val queryLens = Query.string().optional("q")
    private val limitLens = Query.int().defaulted("limit", DEFAULT_LIMIT)
    private val offsetLens = Query.int().defaulted("offset", 0)
    private val yearLens = Query.int().optional("year")
    private val pagePathLens = Query.string().defaulted("pagePath", "/")

    override val routes = listOf(
        "/components/navigation/page" meta {
            summary = "Theme/Lang/Layout refresh"
        } bindContract GET to { request: org.http4k.core.Request ->
            val pagePath = pagePathLens(request)
            renderer.render(pageFactory.buildNavigationRefresh(request.webContext, pagePath))
        },
        "/components/sidebar/theme-selector" meta {
            summary = "Sidebar theme selector fragment"
        } bindContract GET to { request: org.http4k.core.Request ->
            renderer.render(pageFactory.buildThemeSelector(request.webContext))
        },
        "/components/message-list" meta {
            summary = "Message list fragment"
            queries += queryLens
            queries += limitLens
            queries += offsetLens
            queries += yearLens
        } bindContract GET to { request: org.http4k.core.Request ->
            val query = queryLens(request)
            val limit = limitLens(request)
            val offset = offsetLens(request)
            val year = yearLens(request)
            renderer.render(pageFactory.buildMessageList(request.webContext, query, limit, offset, year))
        }
    )
}
