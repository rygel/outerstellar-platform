package com.example.outerstellar.starter.extension

import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import io.github.rygel.outerstellar.platform.extension.ExtensionContributionContext
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

class StarterPlatformExtension : PlatformExtension {
    override val id = "starter"
    override val appLabel = "Starter App"
    override val mode = PlatformMode.ExtensionHost

    override fun contribute(context: ExtensionContributionContext) {
        context.platformPages.include(PlatformPageSets.SETTINGS, PlatformPageSets.SEARCH)

        val homeRoute =
            "/" meta
                {
                    summary = "Starter home"
                } bindContract
                GET to
                { _: Request ->
                    Response(Status.OK)
                        .header("content-type", "text/html; charset=utf-8")
                        .body(
                            """
                            <main>
                              <h1>Starter App is running</h1>
                              <p>This page is served by <code>StarterPlatformExtension</code>.</p>
                              <p>Platform version: ${context.host.app.version}</p>
                              <ul>
                                <li><a href="/starter/health">Public health probe</a></li>
                                <li><a href="/starter/me">Protected route using the current user</a></li>
                                <li><a href="/settings">Included platform settings page</a></li>
                                <li><a href="/search">Included platform search page</a></li>
                              </ul>
                            </main>
                            """.trimIndent(),
                        )
                }
        context.routes.publicUi(homeRoute, "Starter home", "/")
        context.navigation.item(appLabel, "/", "rocket-line")

        val healthRoute =
            "/starter/health" meta
                {
                    summary = "Starter health"
                } bindContract
                GET to
                { _: Request ->
                    Response(Status.OK).body("starter:${context.host.app.version}")
                }
        context.routes.publicUi(healthRoute, "Starter health", "/starter/health")

        val currentUserRoute =
            "/starter/me" meta
                {
                    summary = "Current starter user"
                } bindContract
                GET to
                { request: Request ->
                    val user = context.host.currentUser(request)
                    Response(Status.OK).body("Hello ${user?.username ?: "anonymous"}")
                }
        context.routes.protectedUi(currentUserRoute, "Starter current user", "/starter/me")
    }
}
