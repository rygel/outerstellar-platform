package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.template.TemplateRenderer

class SettingsRoutes(private val settingsPageFactory: SettingsPageFactory, private val renderer: TemplateRenderer) :
    ServerRoutes {

    override val routes: List<ContractRoute> =
        listOf(
            "/settings" meta
                {
                    summary = "Unified settings page"
                } bindContract
                GET to
                { request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    val tab = request.query("tab") ?: "profile"
                    val isHtmx = request.header("HX-Request") == "true"
                    if (isHtmx) {
                        renderer.render(settingsPageFactory.buildSettingsFragment(ctx, shellRenderer, tab))
                    } else {
                        renderer.render(settingsPageFactory.buildSettingsPage(shellRenderer, tab))
                    }
                }
        )
}
