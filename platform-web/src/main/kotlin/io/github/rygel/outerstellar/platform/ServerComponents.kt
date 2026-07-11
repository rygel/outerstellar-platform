package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.di.createConfiguredEmailService
import io.github.rygel.outerstellar.platform.di.createCoreComponents
import io.github.rygel.outerstellar.platform.di.createWebComponents
import io.github.rygel.outerstellar.platform.di.loadPersistenceBootstrap
import io.github.rygel.outerstellar.platform.extension.ExtensionContribution
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.createSecurityComponents
import io.github.rygel.outerstellar.platform.security.validatePassword
import io.github.rygel.outerstellar.platform.web.SyncWebSocket
import org.http4k.core.PolyHandler

private val LOCAL_SERVER_PROFILES = setOf("dev", "test")

class ServerComponents(
    val config: AppConfig,
    val persistence: PlatformPersistence,
    val core: CoreComponents,
    val security: SecurityComponents,
    val web: WebComponents,
    val app: PolyHandler,
)

/**
 * Preferred application entrypoint for extensions and production wiring.
 *
 * This keeps persistence bootstrap, extension migrations, email wiring, websocket/event publishing, and extension host
 * context assembly aligned with the production composition model. Prefer this over manually wiring the lower-level
 * `createXxxComponents(...)` helpers unless you are deliberately testing a smaller assembly seam.
 */
fun createServerComponents(extension: PlatformExtension? = null): ServerComponents {
    val config = AppConfig.fromEnvironment()
    return createServerComponents(config = config, extension = extension)
}

/**
 * Preferred application entrypoint when the caller already owns configuration, such as full-stack extension tests that
 * run against a test database. Production startup should usually use [createServerComponents] without a config
 * argument.
 */
fun createServerComponents(config: AppConfig, extension: PlatformExtension? = null): ServerComponents {
    require(!config.devMode || config.profile in LOCAL_SERVER_PROFILES) {
        "DEVMODE may only be enabled with the dev or test profile"
    }
    val persistence = loadPersistenceBootstrap().create(config = config, extensionMigrations = extension?.migrations)
    val emailService = createConfiguredEmailService(config)

    val security =
        createSecurityComponents(
            config = config,
            userRepository = persistence.userRepository,
            auditRepository = persistence.auditRepository,
            resetRepository = persistence.passwordResetRepository,
            apiKeyRepository = persistence.apiKeyRepository,
            emailService = emailService,
            oauthRepository = persistence.oAuthRepository,
            sessionRepository = persistence.sessionRepository,
            transactionManager = persistence.transactionManager,
        )
    val syncWebSocket = SyncWebSocket(security.sessionService)

    val core =
        createCoreComponents(
            config = config,
            messageRepository = persistence.messageRepository,
            contactRepository = persistence.contactRepository,
            outboxRepository = persistence.outboxRepository,
            transactionManager = persistence.transactionManager,
            auditRepository = persistence.auditRepository,
            eventPublisher = syncWebSocket,
            emailService = emailService,
        )

    val extensionContribution = ExtensionContribution.platformDefaults(config.platformMode)
    val web =
        createWebComponents(
            config = config,
            extensionContribution = extensionContribution,
            apiKeyService = security.apiKeyService,
            oauthService = security.oauthService,
            syncWebSocket = syncWebSocket,
            userAdminService = security.userAdminService,
            userRepository = persistence.userRepository,
            messageRepository = persistence.messageRepository,
            messageService = core.messageService,
            contactService = core.contactService,
            voteRepository = persistence.voteRepository,
            pollRepository = persistence.pollRepository,
            notificationRepository = persistence.notificationRepository,
        )

    val polyHandler =
        app(
            config = config,
            persistence = persistence,
            security = security,
            core = core,
            web = web,
            extension = extension,
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

fun ServerComponents.ensureInitialAdmin(adminPassword: String?) {
    if (persistence.userRepository.findByUsername("admin") != null) return

    require(!adminPassword.isNullOrBlank()) {
        "ADMIN_PASSWORD is required when the initial administrator account does not exist"
    }
    validatePassword(adminPassword)?.let { error -> throw IllegalArgumentException("Invalid ADMIN_PASSWORD: $error") }
    persistence.userRepository.seedAdminUser(BCryptPasswordEncoder().encode(adminPassword))
}
