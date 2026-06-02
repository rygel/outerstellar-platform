#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.extension

import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.extension.ExtensionContributionContext
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class ExtensionPlatformExtension : PlatformExtension {
    override val id = "${extensionId}"
    override val appLabel = "${appLabel}"
    override val mode = PlatformMode.ExtensionHost

    override fun contribute(context: ExtensionContributionContext) {
        context.platformPages.include(PlatformPageSets.SETTINGS, PlatformPageSets.SEARCH)

        val homeRoute =
            "/" meta
                {
                    summary = "${appLabel} home"
                } bindContract
                GET to
                { _: Request ->
                    val html = context.host.rendering.renderer(
                        IndexPage(platformVersion = context.host.app.version)
                    )
                    Response(Status.OK)
                        .header("content-type", "text/html; charset=utf-8")
                        .body(html)
                }
        context.routes.publicUi(homeRoute, "${appLabel} home", "/")
        context.navigation.item(appLabel, "/", "rocket-line")

        val healthRoute =
            "/${extensionId}/health" meta
                {
                    summary = "${extensionId} health"
                } bindContract
                GET to
                { _: Request ->
                    Response(Status.OK).body("${extensionId}:${symbol_dollar}{context.host.app.version}")
                }
        context.routes.publicUi(healthRoute, "${extensionId} health", "/${extensionId}/health")

        val currentUserRoute =
            "/${extensionId}/me" meta
                {
                    summary = "Current user"
                } bindContract
                GET to
                { request: Request ->
                    val user = context.host.currentUser(request)
                    Response(Status.OK).body("Hello ${symbol_dollar}{user?.username ?: "anonymous"}")
                }
        context.routes.protectedUi(currentUserRoute, "Current user", "/${extensionId}/me")
    }
}
