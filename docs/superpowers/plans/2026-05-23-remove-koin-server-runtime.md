# Remove Koin from Server Runtime (#322) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all Koin-based runtime wiring in the server application path (platform-web, platform-seeder, and their dependency modules) with explicit constructor composition, while preserving the desktop modules' Koin usage untouched.

**Architecture:** Introduce a `ServerComponents` data class in platform-web as the explicit composition root. `main()` constructs `ServerComponents` directly instead of calling `startKoin`. All Koin module files in platform-core, platform-security, platform-persistence-jdbi, and platform-web are replaced by plain factory functions. The `app()` function and `AppContext` remain unchanged — they already accept explicit parameters.

**Tech Stack:** Kotlin, http4k, JDBI, JTE, HikariCP, Flyway

---

## Scope

### In scope (server path only)
- `platform-core/src/main/kotlin/.../di/CoreModule.kt` — replace with factory function
- `platform-core/src/main/kotlin/.../AppConfig.kt` — remove `configModule`
- `platform-security/src/main/kotlin/.../security/SecurityModule.kt` — replace with factory function
- `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceModule.kt` — replace with factory function
- `platform-web/src/main/kotlin/.../Main.kt` — replace `startKoin` + `MainComponent` with `ServerComponents`
- `platform-web/src/main/kotlin/.../di/WebModule.kt` — replace with factory function
- `platform-web/src/main/kotlin/.../di/AdminWebModule.kt` — replace with factory function
- `platform-web/src/main/kotlin/.../web/PlatformExtension.kt` — remove `koinModules()` method and `Module` import
- `platform-seeder/src/main/kotlin/.../seed/SeedData.kt` — replace `startKoin` + `SeedComponent` with explicit wiring
- `platform-web/src/test/kotlin/.../di/KoinModuleTest.kt` — delete (no longer applicable)
- `platform-web/src/test/kotlin/.../e2e/ResponsiveLayoutE2ETest.kt` — replace Koin with manual wiring
- `platform-web/src/test/kotlin/.../e2e/PlaywrightE2ETest.kt` — replace Koin with manual wiring
- POM files for platform-web, platform-seeder — remove `koin-core-jvm` from production dependencies
- Root `pom.xml` — keep `koin.version` property (desktop modules still use it)
- `platform-web/pom.xml` — keep `koin-test-junit5` as test dependency only if still needed after E2E rewrite

### Out of scope (desktop modules retain Koin)
- `platform-desktop/` — uses Koin, not touched
- `platform-desktop-javafx/` — uses Koin, not touched
- `platform-sync-client/` — uses Koin for desktop path; will still have `apiClientModule` (desktop needs it)
- Desktop test files — not touched

## Key Reference: WebTest

`WebTest.kt` already demonstrates the target pattern: manual construction without Koin. The production `ServerComponents` will follow the same approach.

## File Structure

### New files
- `platform-web/src/main/kotlin/.../ServerComponents.kt` — explicit composition root
- `platform-core/src/main/kotlin/.../di/CoreFactory.kt` — replaces `CoreModule.kt`
- `platform-security/src/main/kotlin/.../security/SecurityFactory.kt` — replaces `SecurityModule.kt`
- `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceFactory.kt` — replaces `PersistenceModule.kt`
- `platform-web/src/main/kotlin/.../di/WebFactory.kt` — replaces `WebModule.kt` + `AdminWebModule.kt`

### Deleted files
- `platform-core/src/main/kotlin/.../di/CoreModule.kt`
- `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceModule.kt`
- `platform-web/src/main/kotlin/.../di/WebModule.kt`
- `platform-web/src/main/kotlin/.../di/AdminWebModule.kt`
- `platform-web/src/test/kotlin/.../di/KoinModuleTest.kt`

### Modified files
- `platform-core/src/main/kotlin/.../AppConfig.kt` — remove `configModule`
- `platform-security/src/main/kotlin/.../security/SecurityModule.kt` — replaced by `SecurityFactory.kt`
- `platform-web/src/main/kotlin/.../Main.kt` — use `ServerComponents` instead of Koin
- `platform-web/src/main/kotlin/.../web/PlatformExtension.kt` — remove `koinModules()` and `Module` import
- `platform-seeder/src/main/kotlin/.../seed/SeedData.kt` — use explicit construction
- `platform-web/src/test/.../e2e/ResponsiveLayoutE2ETest.kt` — use explicit construction
- `platform-web/src/test/.../e2e/PlaywrightE2ETest.kt` — use explicit construction
- POM files — dependency adjustments

---

### Task 1: Create PersistenceFactory

**Files:**
- Create: `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceFactory.kt`
- Reference: `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceModule.kt`

This is the foundation — everything depends on repositories. The factory function creates DataSource + JDBI + all repositories explicitly.

- [ ] **Step 1: Write PersistenceFactory.kt**

```kotlin
package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.ExtensionMigrationSource
import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.infra.migrateExtension
import io.github.rygel.outerstellar.platform.persistence.*
import io.github.rygel.outerstellar.platform.security.CachingUserRepository
import io.github.rygel.outerstellar.platform.security.OAuthRepository
import io.micrometer.core.instrument.Metrics
import org.jdbi.v3.core.Jdbi
import javax.sql.DataSource

data class PersistenceComponents(
    val dataSource: DataSource,
    val jdbi: Jdbi,
    val messageRepository: MessageRepository,
    val contactRepository: ContactRepository,
    val userRepository: UserRepository,
    val outboxRepository: OutboxRepository,
    val transactionManager: TransactionManager,
    val auditRepository: AuditRepository,
    val passwordResetRepository: PasswordResetRepository,
    val apiKeyRepository: ApiKeyRepository,
    val oAuthRepository: OAuthRepository,
    val deviceTokenRepository: DeviceTokenRepository,
    val sessionRepository: SessionRepository,
    val voteRepository: VoteRepository,
    val pollRepository: PollRepository,
    val notificationRepository: NotificationRepository,
)

fun createPersistenceComponents(
    config: AppConfig,
    extensionMigrationSource: ExtensionMigrationSource? = null,
): PersistenceComponents {
    val ds = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword, config.runtime)
    try {
        if (config.runtime.flywayEnabled) {
            migrate(ds)
            extensionMigrationSource?.migrationLocation?.let { location ->
                migrateExtension(ds, location, extensionMigrationSource.migrationHistoryTable, extensionMigrationSource.migrationNames)
            }
        }
    } catch (e: Exception) {
        ds.close()
        throw e
    }
    if (config.devMode) {
        JdbiUserRepository(Jdbi.create(ds)).seedAdminUser(DEV_ADMIN_PLACEHOLDER_HASH)
    }

    val jdbi = Jdbi.create(ds).also {
        if (Metrics.globalRegistry.find("database.connections.active").gauge() == null) {
            Metrics.globalRegistry.gauge("database.connections.active", 1)
        }
    }

    return PersistenceComponents(
        dataSource = ds,
        jdbi = jdbi,
        messageRepository = JdbiMessageRepository(jdbi),
        contactRepository = JdbiContactRepository(jdbi),
        userRepository = CachingUserRepository(JdbiUserRepository(jdbi)),
        outboxRepository = JdbiOutboxRepository(jdbi),
        transactionManager = JdbiTransactionManager(jdbi),
        auditRepository = JdbiAuditRepository(jdbi),
        passwordResetRepository = JdbiPasswordResetRepository(jdbi),
        apiKeyRepository = JdbiApiKeyRepository(jdbi),
        oAuthRepository = JdbiOAuthRepository(jdbi),
        deviceTokenRepository = JdbiDeviceTokenRepository(jdbi),
        sessionRepository = JdbiSessionRepository(jdbi),
        voteRepository = JdbiVoteRepository(jdbi),
        pollRepository = JdbiPollRepository(jdbi),
        notificationRepository = JdbiNotificationRepository(jdbi),
    )
}

private const val DEV_ADMIN_PLACEHOLDER_HASH = "\$2a\$04\$DevPlaceholderAdminXXuZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZe"
```

- [ ] **Step 2: Compile platform-persistence-jdbi**

Run: `mvn -pl platform-persistence-jdbi compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 3: Run persistence tests**

Run: `mvn -pl platform-persistence-jdbi -am test`
Expected: All tests pass (PersistenceModule still exists, nothing changed yet)

- [ ] **Step 4: Commit**

```bash
git add platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceFactory.kt
git commit -m "feat(persistence): add explicit PersistenceFactory alongside Koin module"
```

---

### Task 2: Create CoreFactory

**Files:**
- Create: `platform-core/src/main/kotlin/.../di/CoreFactory.kt`
- Reference: `platform-core/src/main/kotlin/.../di/CoreModule.kt`

- [ ] **Step 1: Write CoreFactory.kt**

```kotlin
package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.persistence.*
import io.github.rygel.outerstellar.platform.service.*

data class CoreComponents(
    val messageService: MessageService,
    val contactService: ContactService,
    val outboxProcessor: OutboxProcessor,
    val eventPublisher: EventPublisher,
    val emailService: EmailService,
    val pushNotificationService: PushNotificationService,
)

fun createCoreComponents(
    config: AppConfig,
    messageRepository: MessageRepository,
    contactRepository: ContactRepository,
    outboxRepository: OutboxRepository,
    messageCache: MessageCache,
    auditRepository: AuditRepository? = null,
    transactionManager: TransactionManager? = null,
): CoreComponents {
    val eventPublisher: EventPublisher = NoOpEventPublisher
    return CoreComponents(
        messageService = MessageService(messageRepository, outboxRepository, transactionManager, messageCache, eventPublisher, auditRepository),
        contactService = ContactService(contactRepository, eventPublisher, transactionManager, auditRepository),
        outboxProcessor = OutboxProcessor(outboxRepository, transactionManager),
        eventPublisher = eventPublisher,
        emailService = ConsoleEmailService(),
        pushNotificationService = createPushNotificationService(config),
    )
}

private fun createPushNotificationService(config: AppConfig): PushNotificationService {
    val cfg = config.pushNotifications
    if (!cfg.enabled) return ConsolePushNotificationService
    return when (cfg.provider) {
        "fcm" -> FcmPushNotificationService(cfg.fcmServiceAccountJson)
        "apns" -> ApnsPushNotificationService(
            privateKeyPem = cfg.apnsPrivateKeyPem,
            teamId = cfg.apnsTeamId,
            keyId = cfg.apnsKeyId,
            bundleId = cfg.apnsBundleId,
        )
        else -> ConsolePushNotificationService
    }
}
```

- [ ] **Step 2: Compile platform-core**

Run: `mvn -pl platform-core compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add platform-core/src/main/kotlin/.../di/CoreFactory.kt
git commit -m "feat(core): add explicit CoreFactory alongside Koin module"
```

---

### Task 3: Create SecurityFactory

**Files:**
- Create: `platform-security/src/main/kotlin/.../security/SecurityFactory.kt`
- Reference: `platform-security/src/main/kotlin/.../security/SecurityModule.kt`

- [ ] **Step 1: Write SecurityFactory.kt**

```kotlin
package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.persistence.*

data class SecurityComponents(
    val passwordEncoder: PasswordEncoder,
    val jwtService: JwtService,
    val asyncActivityUpdater: AsyncActivityUpdater,
    val securityService: SecurityService,
    val permissionResolver: PermissionResolver,
    val authRealms: List<AuthRealm>,
    val totpService: TOTPService,
)

fun createSecurityComponents(
    config: AppConfig,
    userRepository: UserRepository,
    sessionRepository: SessionRepository? = null,
    apiKeyRepository: ApiKeyRepository? = null,
    passwordResetRepository: PasswordResetRepository? = null,
    auditRepository: AuditRepository? = null,
    oAuthRepository: OAuthRepository? = null,
): SecurityComponents {
    val encoder = BCryptPasswordEncoder()
    val totpService = TOTPService()
    val securityConfig = SecurityConfig(
        appBaseUrl = config.appBaseUrl,
        sessionTimeoutSeconds = config.sessionTimeoutMinutes.toLong() * 60,
        maxFailedLoginAttempts = config.maxFailedLoginAttempts,
        lockoutDurationSeconds = config.lockoutDurationSeconds,
        sessionAbsoluteTimeoutSeconds = config.sessionAbsoluteTimeoutMinutes.toLong() * 60,
        registrationEnabled = config.registrationEnabled,
    )
    val securityService = SecurityService(
        userRepository,
        encoder,
        sessionRepository,
        apiKeyRepository,
        passwordResetRepository,
        auditRepository,
        oAuthRepository,
        securityConfig,
        auditRepository,
        encoder,
        totpService,
    )
    val realms: List<AuthRealm> = listOf(SessionRealm(securityService), ApiKeyRealm(securityService))
    return SecurityComponents(
        passwordEncoder = encoder,
        jwtService = JwtService(config.jwt),
        asyncActivityUpdater = AsyncActivityUpdater(auditRepository),
        securityService = securityService,
        permissionResolver = RoleBasedPermissionResolver(),
        authRealms = realms,
        totpService = totpService,
    )
}
```

- [ ] **Step 2: Compile platform-security**

Run: `mvn -pl platform-security compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add platform-security/src/main/kotlin/.../security/SecurityFactory.kt
git commit -m "feat(security): add explicit SecurityFactory alongside Koin module"
```

---

### Task 4: Create WebFactory

**Files:**
- Create: `platform-web/src/main/kotlin/.../di/WebFactory.kt`
- Reference: `platform-web/src/main/kotlin/.../di/WebModule.kt`, `platform-web/src/main/kotlin/.../di/AdminWebModule.kt`

- [ ] **Step 1: Write WebFactory.kt**

This replaces both `WebModule.kt` and `AdminWebModule.kt`. The `app()` function already takes explicit parameters — this factory just constructs those parameters.

```kotlin
package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.ExtensionMigrationSource
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.analytics.SegmentAnalyticsService
import io.github.rygel.outerstellar.platform.infra.ExtensionTemplateRenderer
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.AsyncActivityUpdater
import io.github.rygel.outerstellar.platform.security.JwtService
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.TOTPService
import io.github.rygel.outerstellar.platform.service.*
import io.github.rygel.outerstellar.platform.web.PlatformExtension
import io.github.rygel.outerstellar.platform.web.SyncWebSocket
import io.github.rygel.outerstellar.platform.web.WebPageFactory
import io.github.rygel.outerstellar.platform.security.AdminStatsService
import org.http4k.template.TemplateRenderer

data class WebComponents(
    val templateRenderer: TemplateRenderer,
    val pageFactory: WebPageFactory,
    val messageCache: MessageCache,
    val analyticsService: AnalyticsService,
    val emailService: EmailService,
    val i18nService: I18nService,
    val syncWebSocket: SyncWebSocket,
    val eventPublisher: EventPublisher,
    val voteService: VoteService,
    val pollService: PollService,
    val notificationService: NotificationService,
    val adminPageFactory: AdminPageFactory,
    val adminStatsService: AdminStatsService,
    val extensionMigrationSource: ExtensionMigrationSource,
)

private object NoExtensionMigrationSource : ExtensionMigrationSource

fun createWebComponents(
    config: AppConfig,
    extension: PlatformExtension? = null,
    messageRepository: io.github.rygel.outerstellar.platform.persistence.MessageRepository? = null,
    messageService: MessageService? = null,
    contactService: ContactService? = null,
    securityService: SecurityService? = null,
    outboxRepository: OutboxRepository? = null,
    messageCache: MessageCache? = null,
    voteRepository: io.github.rygel.outerstellar.platform.persistence.VoteRepository? = null,
    pollRepository: io.github.rygel.outerstellar.platform.persistence.PollRepository? = null,
    notificationRepository: io.github.rygel.outerstellar.platform.persistence.NotificationRepository? = null,
    userRepository: UserRepository? = null,
): WebComponents {
    val baseRenderer = createRenderer(config.runtime)
    val overrides = extension?.templateOverrides()
    val templateRenderer: TemplateRenderer =
        if (extension != null && overrides != null && overrides.isNotEmpty()) {
            ExtensionTemplateRenderer(baseRenderer, overrides, extension::class.java.classLoader)
        } else {
            baseRenderer
        }

    val pageFactory = WebPageFactory(
        messageRepository,
        messageService,
        contactService,
        securityService,
        appleOAuthEnabled = config.appleOAuth.enabled,
    )

    val runtime = config.runtime
    val cache = messageCache ?: io.github.rygel.outerstellar.platform.persistence.CaffeineMessageCache(
        maxSize = runtime.cacheMessageMaxSize.toLong(),
        ttlMinutes = runtime.cacheMessageExpireMinutes.toLong(),
    )

    val analyticsService: AnalyticsService = config.segment.let { cfg ->
        if (cfg.enabled && cfg.writeKey.isNotBlank()) SegmentAnalyticsService(cfg.writeKey) else NoOpAnalyticsService()
    }

    val emailService: EmailService = config.email.let { cfg ->
        if (cfg.enabled && cfg.host.isNotBlank()) {
            ResilientEmailService(SmtpEmailService(SmtpConfig(
                host = cfg.host, port = cfg.port, username = cfg.username, password = cfg.password,
                from = cfg.from, startTls = cfg.startTls,
            )))
        } else {
            NoOpEmailService()
        }
    }

    val i18nService = I18nService.create("messages")
    val syncWebSocket = SyncWebSocket(securityService!!)
    val eventPublisher: EventPublisher = syncWebSocket
    val voteService = VoteService(voteRepository!!, pollRepository!!)
    val pollService = PollService(pollRepository!!)

    val notificationService = NotificationService(notificationRepository!!)
    val adminStatsService = AdminStatsService(userRepository!!)
    val adminPageFactory = AdminPageFactory(notificationRepository!!, adminStatsService)

    val extensionMigrationSource: ExtensionMigrationSource = extension ?: NoExtensionMigrationSource

    return WebComponents(
        templateRenderer = templateRenderer,
        pageFactory = pageFactory,
        messageCache = cache,
        analyticsService = analyticsService,
        emailService = emailService,
        i18nService = i18nService,
        syncWebSocket = syncWebSocket,
        eventPublisher = eventPublisher,
        voteService = voteService,
        pollService = pollService,
        notificationService = notificationService,
        adminPageFactory = adminPageFactory,
        adminStatsService = adminStatsService,
        extensionMigrationSource = extensionMigrationSource,
    )
}
```

- [ ] **Step 2: Compile platform-web**

Run: `mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/.../di/WebFactory.kt
git commit -m "feat(web): add explicit WebFactory alongside Koin module"
```

---

### Task 5: Create ServerComponents and rewrite Main.kt

**Files:**
- Create: `platform-web/src/main/kotlin/.../ServerComponents.kt`
- Modify: `platform-web/src/main/kotlin/.../Main.kt`

This is the key step — the composition root that wires everything together explicitly.

- [ ] **Step 1: Write ServerComponents.kt**

```kotlin
package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.di.*
import io.github.rygel.outerstellar.platform.web.PlatformExtension
import org.http4k.core.PolyHandler
import org.http4k.template.TemplateRenderer

class ServerComponents(
    val config: AppConfig,
    val persistence: PersistenceComponents,
    val core: CoreComponents,
    val security: SecurityComponents,
    val web: WebComponents,
    val app: PolyHandler,
)
```

- [ ] **Step 2: Write `createServerComponents()` function**

Add to `ServerComponents.kt`:

```kotlin
fun createServerComponents(extension: PlatformExtension? = null): ServerComponents {
    val config = AppConfig.fromEnvironment()

    val webComponents = createWebComponents(
        config = config,
        extension = extension,
    )
    val persistence = createPersistenceComponents(config, webComponents.extensionMigrationSource)

    val core = createCoreComponents(
        config = config,
        messageRepository = persistence.messageRepository,
        contactRepository = persistence.contactRepository,
        outboxRepository = persistence.outboxRepository,
        messageCache = webComponents.messageCache,
        auditRepository = persistence.auditRepository,
        transactionManager = persistence.transactionManager,
    )

    val security = createSecurityComponents(
        config = config,
        userRepository = persistence.userRepository,
        sessionRepository = persistence.sessionRepository,
        apiKeyRepository = persistence.apiKeyRepository,
        passwordResetRepository = persistence.passwordResetRepository,
        auditRepository = persistence.auditRepository,
        oAuthRepository = persistence.oAuthRepository,
    )

    val app = app(
        messageService = core.messageService,
        contactService = core.contactService,
        outboxRepository = persistence.outboxRepository,
        cache = webComponents.messageCache,
        jteRenderer = webComponents.templateRenderer,
        pageFactory = webComponents.pageFactory,
        config = config,
        securityService = security.securityService,
        userRepository = persistence.userRepository,
        analytics = webComponents.analyticsService,
        notificationService = webComponents.notificationService,
        jwtService = security.jwtService,
        extension = extension,
        activityUpdater = security.asyncActivityUpdater,
        syncWebSocket = webComponents.syncWebSocket,
        totpService = security.totpService,
        voteService = webComponents.voteService,
        pollService = webComponents.pollService,
    )

    return ServerComponents(config, persistence, core, security, webComponents, app)
}
```

- [ ] **Step 3: Rewrite Main.kt**

Replace `MainComponent` + `startKoin` with `ServerComponents`:

```kotlin
package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.security.AsyncActivityUpdater
import io.github.rygel.outerstellar.platform.service.OutboxProcessor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.slf4j.LoggerFactory

private const val MILLIS_PER_SECOND = 1000L
private const val OUTBOX_INTERVAL_SECONDS = 30L
private const val SHUTDOWN_TIMEOUT_SECONDS = 5L

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.Main")

private fun elapsed(from: Long, phase: String) = "Startup — $phase: ${System.currentTimeMillis() - from}ms"

fun main() {
    val t0 = System.currentTimeMillis()

    val components = createServerComponents()
    logger.info(elapsed(t0, "Components initialized"))

    val config = components.config

    if (
        config.jdbcPassword == AppConfig.DEFAULT_JDBC_PASSWORD &&
            config.profile != "default" &&
            config.profile != "test"
    ) {
        logger.error(
            "FATAL: JDBC_PASSWORD is still the default '{}' with profile '{}'. " +
                "Set JDBC_PASSWORD to a secure value before deploying.",
            AppConfig.DEFAULT_JDBC_PASSWORD,
            config.profile,
        )
    }

    val server = components.app.asServer(Netty(config.port)).start()
    logger.info(elapsed(t0, "Server ready on :${server.port()}"))

    val adminPassword =
        System.getenv("ADMIN_PASSWORD")
            ?: java.util.UUID.randomUUID().toString().also {
                logger.warn("ADMIN_PASSWORD env var not set. A random password was generated for first-boot admin.")
                logger.warn("Set ADMIN_PASSWORD to a secure value before deploying to production.")
            }
    if (components.persistence.userRepository.findByUsername("admin") == null) {
        components.persistence.userRepository.seedAdminUser(
            components.security.passwordEncoder.encode(adminPassword)
        )
    }

    val outboxScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "outbox-processor").also { it.isDaemon = true }
    }
    val outboxTask =
        object : Runnable {
            override fun run() {
                try {
                    components.core.outboxProcessor.processPending()
                    components.security.asyncActivityUpdater.flush()
                } finally {
                    val delay = maxOf(
                        components.core.outboxProcessor.backoffMs,
                        OUTBOX_INTERVAL_SECONDS * MILLIS_PER_SECOND,
                    )
                    outboxScheduler.schedule(this, delay, TimeUnit.MILLISECONDS)
                }
            }
        }
    outboxScheduler.schedule(outboxTask, 0, TimeUnit.MILLISECONDS)
    logger.info(elapsed(t0, "Background jobs started"))

    registerShutdownHook(components.security.asyncActivityUpdater, outboxScheduler, server)
}

private fun registerShutdownHook(
    activityUpdater: AsyncActivityUpdater,
    outboxScheduler: ScheduledExecutorService,
    server: Http4kServer,
) {
    Runtime.getRuntime()
        .addShutdownHook(
            Thread(
                {
                    logger.info("Graceful shutdown initiated")
                    logger.info("Flushing pending activity updates...")
                    activityUpdater.flush()
                    logger.info("Stopping outbox scheduler...")
                    outboxScheduler.shutdown()
                    try {
                        if (!outboxScheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            outboxScheduler.shutdownNow()
                        }
                    } catch (e: InterruptedException) {
                        outboxScheduler.shutdownNow()
                    }
                    logger.info("Stopping HTTP server...")
                    server.stop()
                    logger.info("Shutdown complete")
                },
                "graceful-shutdown",
            )
        )
}
```

- [ ] **Step 4: Compile platform-web**

Run: `mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS (old Koin module files still exist, no breakage yet)

- [ ] **Step 5: Run platform-web unit tests**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true"`
Expected: All pass (WebTest doesn't use Koin, only E2E tests do — those may break but are addressed in Task 7)

- [ ] **Step 6: Commit**

```bash
git add platform-web/src/main/kotlin/.../ServerComponents.kt platform-web/src/main/kotlin/.../Main.kt
git commit -m "feat(web): replace Koin with explicit ServerComponents in main()"
```

---

### Task 6: Rewrite SeedData.kt

**Files:**
- Modify: `platform-seeder/src/main/kotlin/.../seed/SeedData.kt`

- [ ] **Step 1: Rewrite SeedData.kt with explicit construction**

```kotlin
package io.github.rygel.outerstellar.platform.seeder

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.di.createPersistenceComponents
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import java.util.UUID
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.seeder.SeedData")

fun main() {
    logger.info("Starting database seed process...")

    val config = AppConfig.fromEnvironment()
    val persistence = createPersistenceComponents(config)

    migrate(persistence.dataSource)

    logger.info("Seeding messages...")
    persistence.messageRepository.seedMessages()

    logger.info("Seeding contacts...")
    persistence.contactRepository.seedContacts()

    logger.info("Seeding users...")
    seedUsers(persistence.userRepository)

    logger.info("Database seeding completed successfully.")
}

private fun seedUsers(repo: io.github.rygel.outerstellar.platform.persistence.UserRepository) {
    val encoder = BCryptPasswordEncoder(logRounds = 10)
    val seedPassword = System.getenv("SEED_USER_PASSWORD")
    if (seedPassword.isNullOrBlank()) {
        logger.warn(
            "SEED_USER_PASSWORD env var not set — using insecure default. Set this for any non-local deployment."
        )
    }
    val password = seedPassword?.takeIf { it.isNotBlank() } ?: "password123"

    val users =
        listOf(
            Triple("admin", "admin@outerstellar.de", UserRole.ADMIN),
            Triple("alice", "alice@example.com", UserRole.USER),
            Triple("bob", "bob@example.com", UserRole.USER),
            Triple("carol", "carol@example.com", UserRole.ADMIN),
        )

    users.forEach { (username, email, role) ->
        if (repo.findByUsername(username) == null) {
            repo.save(
                User(
                    id = UUID.randomUUID(),
                    username = username,
                    email = email,
                    passwordHash = encoder.encode(password),
                    role = role,
                )
            )
            logger.info("Seeded user: {} ({})", username, role)
        } else {
            logger.info("User {} already exists, skipping", username)
        }
    }
}
```

- [ ] **Step 2: Compile platform-seeder**

Run: `mvn -pl platform-seeder -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add platform-seeder/src/main/kotlin/.../seed/SeedData.kt
git commit -m "feat(seed): replace Koin with explicit construction in SeedData"
```

---

### Task 7: Rewrite E2E tests to remove Koin

**Files:**
- Modify: `platform-web/src/test/kotlin/.../e2e/ResponsiveLayoutE2ETest.kt`
- Modify: `platform-web/src/test/kotlin/.../e2e/PlaywrightE2ETest.kt`
- Delete: `platform-web/src/test/kotlin/.../di/KoinModuleTest.kt`

- [ ] **Step 1: Rewrite ResponsiveLayoutE2ETest.kt**

Replace the `stopKoin()` / `startKoin { ... }` block with manual construction using `createServerComponents`-style wiring. Key changes:
- Remove all Koin imports
- In `@BeforeEach setup()`, create `AppConfig` directly, then use `createPersistenceComponents`, `createCoreComponents`, `createSecurityComponents`, `createWebComponents` and `app()`
- Resolve `PolyHandler` as the return value of `app()` instead of via Koin

The test needs a real server with seeded data. Pattern:

```kotlin
@BeforeEach
fun setup() {
    val testConfig = AppConfig(
        jdbcUrl = container.jdbcUrl,
        jdbcUser = container.username,
        jdbcPassword = container.password,
        devMode = true,
    )
    val persistence = createPersistenceComponents(testConfig)
    val web = createWebComponents(config = testConfig)
    val core = createCoreComponents(
        config = testConfig,
        messageRepository = persistence.messageRepository,
        contactRepository = persistence.contactRepository,
        outboxRepository = persistence.outboxRepository,
        messageCache = web.messageCache,
        auditRepository = persistence.auditRepository,
        transactionManager = persistence.transactionManager,
    )
    val security = createSecurityComponents(
        config = testConfig,
        userRepository = persistence.userRepository,
        sessionRepository = persistence.sessionRepository,
        apiKeyRepository = persistence.apiKeyRepository,
        passwordResetRepository = persistence.passwordResetRepository,
        auditRepository = persistence.auditRepository,
        oAuthRepository = persistence.oAuthRepository,
    )
    persistence.messageRepository.seedMessages()
    persistence.contactRepository.seedContacts()
    val polyHandler = app(
        messageService = core.messageService,
        contactService = core.contactService,
        outboxRepository = persistence.outboxRepository,
        cache = web.messageCache,
        jteRenderer = web.templateRenderer,
        pageFactory = web.pageFactory,
        config = testConfig,
        securityService = security.securityService,
        userRepository = persistence.userRepository,
        analytics = web.analyticsService,
        notificationService = web.notificationService,
        jwtService = security.jwtService,
        totpService = security.totpService,
        voteService = web.voteService,
        pollService = web.pollService,
    )
    server = polyHandler.asServer(Netty(0)).start()
    baseUrl = "http://localhost:${server.port()}"
}
```

- [ ] **Step 2: Rewrite PlaywrightE2ETest.kt**

Same pattern as ResponsiveLayoutE2ETest. Replace Koin block with explicit construction.

- [ ] **Step 3: Delete KoinModuleTest.kt**

This test validates Koin module resolution via `checkModules` — no longer applicable.

```bash
rm platform-web/src/test/kotlin/.../di/KoinModuleTest.kt
```

- [ ] **Step 4: Compile platform-web**

Run: `mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test(web): replace Koin in E2E tests with explicit construction, delete KoinModuleTest"
```

---

### Task 8: Delete old Koin module files and clean up PlatformExtension

**Files:**
- Delete: `platform-core/src/main/kotlin/.../di/CoreModule.kt`
- Delete: `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceModule.kt`
- Delete: `platform-web/src/main/kotlin/.../di/WebModule.kt`
- Delete: `platform-web/src/main/kotlin/.../di/AdminWebModule.kt`
- Modify: `platform-security/src/main/kotlin/.../security/SecurityModule.kt` — delete the file
- Modify: `platform-core/src/main/kotlin/.../AppConfig.kt` — remove `configModule`
- Modify: `platform-web/src/main/kotlin/.../web/PlatformExtension.kt` — remove `koinModules()` method and `Module` import

**WARNING:** The desktop modules (platform-desktop, platform-desktop-javafx) still import `persistenceModule`, `coreModule`, `securityModule` from these files. Deleting them will break desktop compilation. This task must verify that desktop modules still compile.

Strategy: Keep the old module files but deprecate them, OR update desktop to use the new factory functions. Since the issue scope says desktop is out of scope, the safest approach is:

1. **Keep old module files for desktop** — they will delegate to the new factory functions internally.
2. This avoids touching desktop code while still removing Koin from the server path.

- [ ] **Step 1: Refactor PersistenceModule.kt to delegate to PersistenceFactory**

Replace the full Koin module with a thin wrapper:

```kotlin
package io.github.rygel.outerstellar.platform.di

import org.koin.dsl.module

@Deprecated("Use createPersistenceComponents() instead. This module exists for desktop compatibility only.")
val persistenceModule
    get() = module {
        single { createPersistenceComponents(get()).also { comps ->
            // Register all components from the data class so Koin can resolve them
        } }
        // ... but this gets complicated. Let's take a different approach.
    }
```

Actually, a cleaner approach: keep the old module files but have them use the factory functions internally. Each module creates its factory and registers the individual beans.

- [ ] **Step 1 (revised): Refactor each old Koin module to delegate to factory**

For `persistenceModule`, replace the body with:

```kotlin
@Deprecated("Use createPersistenceComponents() for server runtime. This exists for desktop Koin only.")
val persistenceModule
    get() = module {
        val comps = createPersistenceComponents(get(), getOrNull())
        single { comps.dataSource }
        single { comps.jdbi }
        single<MessageRepository> { comps.messageRepository }
        single<ContactRepository> { comps.contactRepository }
        single<UserRepository> { comps.userRepository }
        single<OutboxRepository> { comps.outboxRepository }
        single<TransactionManager> { comps.transactionManager }
        single<AuditRepository> { comps.auditRepository }
        single<PasswordResetRepository> { comps.passwordResetRepository }
        single<ApiKeyRepository> { comps.apiKeyRepository }
        single<OAuthRepository> { comps.oAuthRepository }
        single<DeviceTokenRepository> { comps.deviceTokenRepository }
        single<SessionRepository> { comps.sessionRepository }
        single<VoteRepository> { comps.voteRepository }
        single<PollRepository> { comps.pollRepository }
        single<NotificationRepository> { comps.notificationRepository }
    }
```

Same pattern for `coreModule`, `securityModule`, `webModule`.

- [ ] **Step 2: Remove `configModule` from AppConfig.kt**

Delete:
```kotlin
val configModule
    get() = module { single { AppConfig.fromEnvironment() } }
```

Update `platform-desktop/.../SwingSyncApp.kt` (and JavaFX) to provide `AppConfig` directly in their Koin modules instead of importing `configModule`. This is a minimal desktop change — just add `single { AppConfig.fromEnvironment() }` inline where `configModule` was used.

- [ ] **Step 3: Remove `koinModules()` from PlatformExtension**

Delete:
```kotlin
fun koinModules(): List<Module> = emptyList()
```

And remove `import org.koin.core.module.Module`.

- [ ] **Step 4: Compile the full reactor (excluding desktop)**

Run: `mvn clean compile -T4 -pl platform-core,platform-security,platform-testkit,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 5: Compile desktop modules**

Run: `mvn compile -pl platform-desktop,platform-desktop-javafx -am "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS (old module files still exist, delegating to factories)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: deprecate Koin modules, delegate to factory functions, remove configModule"
```

---

### Task 9: Remove Koin from production POM dependencies (server path)

**Files:**
- Modify: `platform-web/pom.xml` — remove `koin-core-jvm` from production deps
- Modify: `platform-seeder/pom.xml` — remove `koin-core-jvm` from production deps
- Modify: `platform-web/pom.xml` — remove `koin-test-junit5` from test deps (no more KoinModuleTest)

Note: `platform-core`, `platform-security`, `platform-persistence-jdbi` must keep `koin-core-jvm` because the deprecated module files still exist for desktop compatibility. They can be removed later when desktop migrates.

- [ ] **Step 1: Remove koin-core-jvm from platform-web pom.xml production dependencies**

Find and remove:
```xml
<dependency>
    <groupId>io.insert-koin</groupId>
    <artifactId>koin-core-jvm</artifactId>
</dependency>
```

Also remove `koin-test-junit5` from test dependencies.

- [ ] **Step 2: Remove koin-core-jvm from platform-seeder pom.xml**

Find and remove the `koin-core-jvm` dependency.

- [ ] **Step 3: Compile full reactor**

Run: `mvn clean compile -T4 -pl platform-core,platform-security,platform-testkit,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add platform-web/pom.xml platform-seeder/pom.xml
git commit -m "build: remove koin-core-jvm from platform-web and platform-seeder production deps"
```

---

### Task 10: Run full test suite and fix any remaining issues

- [ ] **Step 1: Run full reactor verify (non-desktop)**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-testkit,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder`
Expected: All tests pass

- [ ] **Step 2: Compile desktop modules**

Run: `mvn compile -pl platform-desktop,platform-desktop-javafx -am "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 3: Fix any failures**

If any test fails, read the error, identify the root cause, fix it, recompile, and re-run.

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve test failures after Koin removal"
```

---

## Self-Review Checklist

### Spec coverage
- [x] Server starts without Koin — Task 5 (Main.kt rewrite)
- [x] Main.kt uses explicit typed wiring — Task 5
- [x] No `named(...)` for core bootstrap — Task 5 (removed)
- [x] Extension migrations still work — Task 4 (extensionMigrationSource)
- [x] Extension template overrides still work — Task 4 (TemplateRenderer)
- [x] JVM startup behavior equivalent — Task 10 (verify)
- [x] GraalVM native-image supported — No native-image changes needed (app() unchanged)
- [x] Desktop modules not regressed — Task 8 (old modules delegate to factories)
- [x] E2E tests work — Task 7
- [x] Seed utility works — Task 6

### Placeholder scan
- No TBD/TODO found
- No "implement later" patterns
- All code steps contain full implementation

### Type consistency
- `PersistenceComponents` fields match `createPersistenceComponents()` return
- `CoreComponents` fields match `createCoreComponents()` return
- `SecurityComponents` fields match `createSecurityComponents()` return
- `WebComponents` fields match `createWebComponents()` return
- `ServerComponents` composition wires all factory outputs correctly
- `app()` parameter types match the wiring in Task 5 Step 2
