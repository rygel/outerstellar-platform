package dev.outerstellar.platform.web

import dev.outerstellar.platform.infra.render
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

private const val DEFAULT_LIMIT = 10
private const val MAX_LIMIT = 100

class ComponentRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
) : ServerRoutes {
    private val queryLens = Query.string().optional("q")
    private val limitLens = Query.int().defaulted("limit", DEFAULT_LIMIT)
    private val offsetLens = Query.int().defaulted("offset", 0)
    private val yearLens = Query.int().optional("year")

    override val routes =
        listOf(
            "/components/navigation/page" meta
                {
                    summary = "Theme/Lang/Layout refresh"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    val pagePath = request.query("pagePath")?.ifBlank { "/" } ?: "/"
                    val forwardParams =
                        request.uri.query
                            .split("&")
                            .filter { !it.startsWith("pagePath=") }
                            .joinToString("&")
                    val redirectUrl =
                        if (forwardParams.isBlank()) pagePath else "$pagePath?$forwardParams"
                    Response(Status.OK).header("HX-Redirect", redirectUrl)
                },
            "/components/sidebar/theme-selector" meta
                {
                    summary = "Sidebar theme selector fragment"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    renderer.render(pageFactory.buildThemeSelector(request.webContext))
                },
            "/components/sidebar/language-selector" meta
                {
                    summary = "Sidebar language selector fragment"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    renderer.render(pageFactory.buildLanguageSelector(request.webContext))
                },
            "/components/sidebar/layout-selector" meta
                {
                    summary = "Sidebar layout selector fragment"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    renderer.render(pageFactory.buildLayoutSelector(request.webContext))
                },
            "/components/message-list" meta
                {
                    summary = "Message list fragment"
                    queries += queryLens
                    queries += limitLens
                    queries += offsetLens
                    queries += yearLens
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    val query = queryLens(request)
                    val limit = limitLens(request).coerceIn(1, MAX_LIMIT)
                    val offset = offsetLens(request).coerceAtLeast(0)
                    val year = yearLens(request)
                    renderer.render(
                        pageFactory.buildMessageList(request.webContext, query, limit, offset, year)
                    )
                },
        )
}
