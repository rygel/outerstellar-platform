#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.host

import ${package}.extension.ExtensionPlatformExtension
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.ServerComponents
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.createServerComponents
import io.github.rygel.outerstellar.platform.ensureInitialAdmin
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("${package}.host.Main")

fun main() {
    val config = AppConfig.fromEnvironment().copy(platformMode = PlatformMode.ExtensionHost)
    val components = createServerComponents(config = config, extension = ExtensionPlatformExtension())
    components.ensureInitialAdmin(System.getenv("ADMIN_PASSWORD"))
    val server = components.app.asServer(Netty(components.config.port)).start()

    registerShutdownHook(components, server::stop)

    logger.info("${appLabel} is running at http://localhost:${symbol_dollar}{server.port()}")
}

private fun registerShutdownHook(components: ServerComponents, stopServer: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(
        Thread(
            {
                logger.info("Shutting down ${appLabel}")
                stopServer()
                components.persistence.close()
            },
            "${extensionId}-host-shutdown",
        ),
    )
}
