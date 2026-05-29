package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.di.createCoreComponents
import io.github.rygel.outerstellar.platform.di.createWebComponents
import io.github.rygel.outerstellar.platform.di.loadPersistenceBootstrap
import io.github.rygel.outerstellar.platform.plugin.HostedApp
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.createSecurityComponents
import org.http4k.core.PolyHandler

class ServerComponents(
    val config: AppConfig,
    val persistence: PlatformPersistence,
    val core: CoreComponents,
    val security: SecurityComponents,
    val web: WebComponents,
    val app: PolyHandler,
)

fun createServerComponents(plugin: HostedApp? = null): ServerComponents {
    val config = AppConfig.fromEnvironment()

    val persistence = loadPersistenceBootstrap().create(config = config, pluginMigrationSource = plugin)

    val security =
        createSecurityComponents(
            config = config,
            userRepository = persistence.userRepository,
            auditRepository = persistence.auditRepository,
            resetRepository = persistence.passwordResetRepository,
            apiKeyRepository = persistence.apiKeyRepository,
            emailService = null,
            oauthRepository = persistence.oAuthRepository,
            sessionRepository = persistence.sessionRepository,
        )

    val core =
        createCoreComponents(
            config = config,
            messageRepository = persistence.messageRepository,
            contactRepository = persistence.contactRepository,
            outboxRepository = persistence.outboxRepository,
            transactionManager = persistence.transactionManager,
            auditRepository = persistence.auditRepository,
        )

    val web =
        createWebComponents(
            config = config,
            plugin = plugin,
            apiKeyService = security.apiKeyService,
            sessionService = security.sessionService,
            userAdminService = security.userAdminService,
            messageRepository = persistence.messageRepository,
            messageService = core.messageService,
            contactService = core.contactService,
            userRepository = persistence.userRepository,
            voteRepository = persistence.voteRepository,
            pollRepository = persistence.pollRepository,
            notificationRepository = persistence.notificationRepository,
        )

    val polyHandler =
        app(config = config, persistence = persistence, security = security, core = core, web = web, plugin = plugin)

    return ServerComponents(
        config = config,
        persistence = persistence,
        core = core,
        security = security,
        web = web,
        app = polyHandler,
    )
}
