package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.contract.div
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Header
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel

class ComponentRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer
) : ServerRoutes {
    private val queryLens = Query.string().optional("q")
    private val yearLens = Query.int().optional("year")
    private val limitLens = Query.int().defaulted("limit", 10)
    private val offsetLens = Query.int().defaulted("offset", 0)
    private val pagePathLens = Query.optional("pagePath")
    private val hxRequestLens = Header.optional("HX-Request")
    private val syncIdPath = Path.string().of("syncId")

    override val routes = listOf(
        "/components/modals/confirm-delete" / syncIdPath meta {
            summary = "Confirm delete modal"
        } bindContract GET to { syncId ->
            { request: org.http4k.core.Request ->
                val ctx = request.webContext
                val i18n = ctx.i18n
                renderer.render(object : ViewModel {
                    override fun template() = "dev/outerstellar/starter/web/components/Modal"
                    val id = "delete-modal-$syncId"
                    val title = i18n.translate("web.modal.delete.title")
                    val message = i18n.translate("web.modal.delete.message")
                    val confirmLabel = i18n.translate("web.modal.delete.confirm")
                    val cancelLabel = i18n.translate("web.modal.delete.cancel")
                    val actionUrl = "/messages/$syncId"
                    val targetId = "#message-list-panel"
                })
            }
        },
        "/components/message-list" meta {
            summary = "Message list fragment"
            queries += queryLens
            queries += yearLens
            queries += limitLens
            queries += offsetLens
        } bindContract GET to { request: org.http4k.core.Request ->
            val query = queryLens(request)
            val year = yearLens(request)
            val limit = limitLens(request)
            val offset = offsetLens(request)
            renderer.render(pageFactory.buildMessageList(request.webContext, query, limit, offset, year))
        },
        "/components/footer-status" meta {
            summary = "Footer status component"
        } bindContract GET to { request: org.http4k.core.Request ->
            renderer.render(pageFactory.buildFooterStatus(request.webContext))
        },
        "/components/navigation/page" meta {
            summary = "Navigation page component"
        } bindContract GET to { request: org.http4k.core.Request ->
            val pagePath = pagePathLens(request).orEmpty()
            val location = request.webContext.url(pagePath)

            if (hxRequestLens(request) == "true") {
                Response(Status.OK).header("HX-Redirect", location)
            } else {
                Response(Status.FOUND).header("location", location)
            }
        },
        "/components/sidebar/theme-selector" meta {
            summary = "Theme selector component"
        } bindContract GET to { request: org.http4k.core.Request ->
            renderer.render(pageFactory.buildThemeSelector(request.webContext))
        },
        "/components/sidebar/language-selector" meta {
            summary = "Language selector component"
        } bindContract GET to { request: org.http4k.core.Request ->
            renderer.render(pageFactory.buildLanguageSelector(request.webContext))
        },
        "/components/sidebar/layout-selector" meta {
            summary = "Layout selector component"
        } bindContract GET to { request: org.http4k.core.Request ->
            renderer.render(pageFactory.buildLayoutSelector(request.webContext))
        }
    )
}
