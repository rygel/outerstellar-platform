package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.template.TemplateRenderer

class ContactsRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer
) : ServerRoutes {

    override val routes = listOf(
        "/contacts" meta {
            summary = "Contacts page"
        } bindContract GET to { request ->
            renderer.render(pageFactory.buildContactsPage(request.webContext))
        }
    )
}
