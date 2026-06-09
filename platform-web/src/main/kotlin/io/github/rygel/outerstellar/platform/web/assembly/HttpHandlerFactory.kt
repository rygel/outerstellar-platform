package io.github.rygel.outerstellar.platform.web.assembly

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.composition.RouteRegistry
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.extension.ExtensionContribution
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.SecurityRules
import io.github.rygel.outerstellar.platform.web.Metrics
import io.github.rygel.outerstellar.platform.web.TOTPApiRoutes
import io.github.rygel.outerstellar.platform.web.TOTPRoutes
import java.nio.file.Path
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.ResourceLoader
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static

internal class HttpHandlerFactory(
    private val config: AppConfig,
    private val persistence: PlatformPersistence,
    private val security: SecurityComponents,
    private val web: WebComponents,
    private val extensionContribution: ExtensionContribution,
) {
    fun build(registry: RouteRegistry): HttpHandler {
        val sec = security
        val userRepository = persistence.userRepository
        val authenticatedFilter = Filter { next -> SecurityRules.authenticated(next) }

        val publicUiHandlers = registry.handlers(RouteGroup.PublicUi)
        val protectedUiHandlers = registry.handlers(RouteGroup.ProtectedUi)
        val apiHandlers = registry.handlers(RouteGroup.Api)
        val adminHandlers = registry.handlers(RouteGroup.Admin)
        val staticHandlers = registry.handlers(RouteGroup.Static)

        val metricsHandler =
            Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
                .then { Response(Status.OK).body(Metrics.registry.scrape()) }

        val unfiltered =
            mutableListOf(
                static(staticResourceLoader()),
                "/health" bind
                    GET to
                    {
                        StaticRoutes.localhostOnly.then { StaticRoutes.buildHealthResponse(userRepository) }(it)
                    },
                "/metrics" bind GET to metricsHandler,
                "/robots.txt" bind GET to { StaticRoutes.buildRobotsTxtResponse() },
                "/sitemap.xml" bind GET to { StaticRoutes.buildSitemapResponse(config.appBaseUrl) },
            )
        unfiltered += staticHandlers

        val appRoutes = mutableListOf<RoutingHttpHandler>()
        routesIfPresent(publicUiHandlers)?.let(appRoutes::add)
        routesIfPresent(protectedUiHandlers)?.let { appRoutes += authenticatedFilter.then(it) }
        appRoutes +=
            TOTPRoutes(
                    sec.authService,
                    web.runtime.templateRenderer,
                    config.sessionCookieSecure,
                    sec.totpService,
                    sec.sessionService,
                )
                .routes
        appRoutes += TOTPApiRoutes(sec.authService, sec.totpService, sec.sessionService).routes
        appRoutes += apiHandlers
        if (adminHandlers.isNotEmpty()) {
            val filteredAdminHandler =
                Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
                    .then(routes(adminHandlers))
            appRoutes += "/" bind filteredAdminHandler
        }

        val baseApp = routes(unfiltered + appRoutes)
        return FilterChainFactory(config, persistence, security, web, extensionContribution).build().then(baseApp)
    }

    private fun RouteRegistry.handlers(group: RouteGroup): List<RoutingHttpHandler> =
        byGroup(group).mapNotNull { it.httpRoute as? RoutingHttpHandler }.distinct()

    private fun routesIfPresent(handlers: List<RoutingHttpHandler>): RoutingHttpHandler? =
        handlers.takeIf { it.isNotEmpty() }?.let(::routes)

    private fun staticResourceLoader(): ResourceLoader {
        val fallback = ResourceLoader.Classpath("static")
        val staticDir = config.staticDir.takeIf { it.isNotBlank() } ?: return fallback
        return FilesystemFirstResourceLoader(Path.of(staticDir), fallback)
    }
}
