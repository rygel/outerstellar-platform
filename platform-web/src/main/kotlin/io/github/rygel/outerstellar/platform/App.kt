package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.extension.ExtensionContribution
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.web.assembly.HttpHandlerFactory
import io.github.rygel.outerstellar.platform.web.assembly.RouteRegistrar
import org.http4k.core.HttpHandler
import org.http4k.core.PolyHandler
import org.http4k.routing.websocket.bind as wsBind
import org.http4k.routing.websockets
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.App")

fun app(
    config: AppConfig,
    persistence: PlatformPersistence,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    extension: PlatformExtension? = null,
): PolyHandler {
    logger.info("Initializing Outerstellar application")
    val httpHandler = assembleHttpHandler(config, persistence, security, core, web, extension)
    val wsHandler = web.runtime.syncWebSocket.let { websockets("/ws/sync" wsBind it.handler) }
    return PolyHandler(httpHandler, wsHandler)
}

private fun assembleHttpHandler(
    config: AppConfig,
    persistence: PlatformPersistence,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    extension: PlatformExtension?,
): HttpHandler {
    val extensionContext = extension?.let { web.hostedAppContextFactory.create(config) }
    val extensionContribution = ExtensionContribution.from(extension, config.platformMode, extensionContext)
    val registry = RouteRegistrar(config, persistence, security, core, web, extensionContribution).buildRegistry()
    logger.info(registry.formatTable())
    return HttpHandlerFactory(config, persistence, security, web, extensionContribution).build(registry)
}
