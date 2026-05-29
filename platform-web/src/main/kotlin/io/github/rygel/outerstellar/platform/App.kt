package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.composition.RouteRegistry
import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.plugin.HostedApp
import io.github.rygel.outerstellar.platform.plugin.HostedAppContext
import io.github.rygel.outerstellar.platform.plugin.HostedAppContribution
import io.github.rygel.outerstellar.platform.security.ApiKeyRealm
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.SessionRealm
import io.github.rygel.outerstellar.platform.web.assembly.HttpHandlerFactory
import io.github.rygel.outerstellar.platform.web.assembly.RouteRegistrar
import io.github.rygel.outerstellar.platform.web.assembly.SecurityConfigurator
import io.github.rygel.outerstellar.platform.web.hostedAppContextFromHostServices
import org.http4k.core.HttpHandler
import org.http4k.core.PolyHandler
import org.http4k.routing.websocket.bind as wsBind
import org.http4k.routing.websockets
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.App")

fun app(
    config: AppConfig,
    persistence: PlatformPersistence,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    plugin: HostedApp? = null,
): PolyHandler {
    logger.info("Initializing Outerstellar application")
    val httpHandler = assembleHttpHandler(config, persistence, security, core, web, plugin)
    val wsHandler = web.syncWebSocket.let { websockets("/ws/sync" wsBind it.handler) }
    return PolyHandler(httpHandler, wsHandler)
}

private fun assembleHttpHandler(
    config: AppConfig,
    persistence: PlatformPersistence,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    plugin: HostedApp?,
): HttpHandler {
    val registry = RouteRegistry()
    val pluginContext = plugin?.let { buildPluginContext(web.templateRenderer, config, persistence, security, web) }
    val pluginContribution = HostedAppContribution.from(plugin, config.platformMode, pluginContext)
    val realms = listOf(SessionRealm(security.sessionService), ApiKeyRealm(security.apiKeyService))
    val (bearerSecurity, bearerAdminSecurity) = SecurityConfigurator(realms).bearerSecurityPair()
    RouteRegistrar(config, persistence, security, core, web, pluginContribution)
        .registerAll(registry, bearerSecurity, bearerAdminSecurity)
    registry.requireNoConflicts()
    logger.info(registry.formatTable())
    return HttpHandlerFactory(config, persistence, security, web, pluginContribution).build(registry)
}

private fun buildPluginContext(
    jteRenderer: TemplateRenderer,
    config: AppConfig,
    persistence: PlatformPersistence,
    security: SecurityComponents,
    web: WebComponents,
): HostedAppContext =
    hostedAppContextFromHostServices(
        renderer = jteRenderer,
        config = config,
        apiKeyService = security.apiKeyService,
        oauthService = security.oauthService,
        userRepository = persistence.userRepository,
        analytics = web.analyticsService,
        notificationService = web.notificationService,
    )
