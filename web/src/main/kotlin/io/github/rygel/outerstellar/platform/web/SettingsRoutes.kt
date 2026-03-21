package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.template.TemplateRenderer

class SettingsRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
) : ServerRoutes {

    override val routes: List<ContractRoute> =
        listOf(
            "/settings" meta
                {
                    summary = "Unified settings page"
                } bindContract
                GET to
                { request ->
                    val ctx = request.webContext
                    if (ctx.user == null) {
                        Response(Status.FOUND).header("location", ctx.url("/auth"))
                    } else {
                        val tab = request.query("tab") ?: "profile"
                        renderer.render(pageFactory.buildSettingsPage(ctx, tab))
                    }
                },
        )
}
