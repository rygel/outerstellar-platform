package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.template.TemplateRenderer

class ContactsRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
) : ServerRoutes {

    override val routes =
        listOf(
            "/contacts" meta
                {
                    summary = "Contacts page"
                } bindContract
                GET to
                { request ->
                    val ctx = request.webContext
                    val query = request.query("q")
                    val limit = request.query("limit")?.toIntOrNull()?.coerceIn(1, 50) ?: 12
                    val offset = request.query("offset")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    renderer.render(pageFactory.buildContactsPage(ctx, query, limit, offset))
                }
        )
}
