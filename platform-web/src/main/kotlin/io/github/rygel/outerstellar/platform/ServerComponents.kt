package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PersistenceComponents
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.di.createCoreComponents
import io.github.rygel.outerstellar.platform.di.createPersistenceComponents
import io.github.rygel.outerstellar.platform.di.createWebComponents
import io.github.rygel.outerstellar.platform.persistence.CaffeineMessageCache
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.createSecurityComponents
import io.github.rygel.outerstellar.platform.web.PlatformPlugin
import org.http4k.core.PolyHandler

class ServerComponents(
    val config: AppConfig,
    val persistence: PersistenceComponents,
    val core: CoreComponents,
    val security: SecurityComponents,
    val web: WebComponents,
    val app: PolyHandler,
)

fun createServerComponents(plugin: PlatformPlugin? = null): ServerComponents {
    val config = AppConfig.fromEnvironment()

    val persistence = createPersistenceComponents(config = config, pluginMigrationSource = plugin)

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
            messageCache =
                CaffeineMessageCache(
                    maxSize = config.runtime.cacheMessageMaxSize.toLong(),
                    ttlMinutes = config.runtime.cacheMessageExpireMinutes.toLong(),
                ),
            transactionManager = persistence.transactionManager,
            auditRepository = persistence.auditRepository,
        )

    val web =
        createWebComponents(
            config = config,
            plugin = plugin,
            securityService = security.securityService,
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
        app(
            messageService = core.messageService,
            contactService = core.contactService,
            outboxRepository = persistence.outboxRepository,
            cache = web.messageCache,
            jteRenderer = web.templateRenderer,
            pageFactory = web.pageFactory,
            config = config,
            securityService = security.securityService,
            userAdminService = security.userAdminService,
            sessionService = security.sessionService,
            userRepository = persistence.userRepository,
            analytics = web.analyticsService,
            notificationService = web.notificationService,
            jwtService = security.jwtService,
            plugin = plugin,
            activityUpdater = security.asyncActivityUpdater,
            syncWebSocket = web.syncWebSocket,
            totpService = security.totpService,
            voteService = web.voteService,
            pollService = web.pollService,
        )

    return ServerComponents(
        config = config,
        persistence = persistence,
        core = core,
        security = security,
        web = web,
        app = polyHandler,
    )
}
