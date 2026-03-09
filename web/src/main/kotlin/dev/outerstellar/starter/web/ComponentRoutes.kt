package dev.outerstellar.starter.web

import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Header
import org.http4k.lens.Query
import org.http4k.template.TemplateRenderer

class ComponentRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer
) : ServerRoutes {
    private val htmlContentType = ContentType.TEXT_HTML.toHeaderValue()
    private val pagePathLens = Query.optional("pagePath")
    private val hxRequestLens = Header.optional("HX-Request")

    override val routes = listOf(
        "/components/footer-status" meta {
            summary = "Footer status component"
        } bindContract GET to { request ->
            val ctx = WebContext(request)
            htmlResponse(Status.OK, renderer(pageFactory.buildFooterStatus(ctx)))
        },
        "/components/navigation/page" meta {
            summary = "Navigation page component"
        } bindContract GET to { request ->
            val ctx = WebContext(request)
            val pagePath = pagePathLens(request).orEmpty()
            val location = ctx.url(pagePath)

            if (hxRequestLens(request) == "true") {
                Response(Status.OK).header("HX-Redirect", location)
            } else {
                Response(Status.FOUND).header("location", location)
            }
        },
        "/components/sidebar/theme-selector" meta {
            summary = "Theme selector component"
        } bindContract GET to { request ->
            val ctx = WebContext(request)
            htmlResponse(Status.OK, renderer(pageFactory.buildThemeSelector(ctx)))
        },
        "/components/sidebar/language-selector" meta {
            summary = "Language selector component"
        } bindContract GET to { request ->
            val ctx = WebContext(request)
            htmlResponse(Status.OK, renderer(pageFactory.buildLanguageSelector(ctx)))
        },
        "/components/sidebar/layout-selector" meta {
            summary = "Layout selector component"
        } bindContract GET to { request ->
            val ctx = WebContext(request)
            htmlResponse(Status.OK, renderer(pageFactory.buildLayoutSelector(ctx)))
        }
    )

    private fun htmlResponse(status: Status, body: String): Response =
        Response(status).header("content-type", htmlContentType).body(body)
}
