#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.host

import ${package}.extension.ExtensionPlatformExtension
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.ServerComponents
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.createServerComponents
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("${package}.host.Main")
private const val DEFAULT_ADMIN_PASSWORD = "${extensionId}-admin-change-me"

fun main() {
    val config = AppConfig.fromEnvironment().copy(platformMode = PlatformMode.ExtensionHost)
    val components = createServerComponents(config = config, extension = ExtensionPlatformExtension())
    val server = components.app.asServer(Netty(components.config.port)).start()

    seedAdminUser(components)
    registerShutdownHook(components, server::stop)

    logger.info("${appLabel} is running at http://localhost:${symbol_dollar}{server.port()}")
}

private fun seedAdminUser(components: ServerComponents) {
    if (components.persistence.userRepository.findByUsername("admin") != null) {
        return
    }

    val adminPassword = System.getenv("ADMIN_PASSWORD") ?: DEFAULT_ADMIN_PASSWORD
    components.persistence.userRepository.seedAdminUser(BCryptPasswordEncoder().encode(adminPassword))

    if (adminPassword == DEFAULT_ADMIN_PASSWORD) {
        logger.warn("Seeded admin user 'admin' with the scaffold default password. Set ADMIN_PASSWORD before startup to override it.")
        return
    }

    logger.info("Seeded admin user 'admin' from ADMIN_PASSWORD.")
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
