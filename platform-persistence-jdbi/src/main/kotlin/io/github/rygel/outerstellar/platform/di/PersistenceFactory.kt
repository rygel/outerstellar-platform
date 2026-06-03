package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.ExtensionMigrations
import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.ContactRepository
import io.github.rygel.outerstellar.platform.persistence.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiAuditRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiContactRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiDeviceTokenRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiNotificationRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiOAuthRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiOutboxRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiPasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiPollRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiTransactionManager
import io.github.rygel.outerstellar.platform.persistence.JdbiUserRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiVoteRepository
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.NotificationRepository
import io.github.rygel.outerstellar.platform.persistence.OAuthRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.PollRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.persistence.VoteRepository
import io.github.rygel.outerstellar.platform.security.CachingUserRepository
import io.micrometer.core.instrument.Metrics
import javax.sql.DataSource
import org.jdbi.v3.core.Jdbi

private const val DEV_ADMIN_PLACEHOLDER_HASH = "\$2a\$04\$DevPlaceholderAdminXXuZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZe"

@Suppress("LongParameterList")
class PersistenceComponents(
    val dataSource: DataSource,
    val jdbi: Jdbi,
    override val messageRepository: MessageRepository,
    override val contactRepository: ContactRepository,
    override val userRepository: UserRepository,
    override val outboxRepository: OutboxRepository,
    override val transactionManager: TransactionManager,
    override val auditRepository: AuditRepository,
    override val passwordResetRepository: PasswordResetRepository,
    override val apiKeyRepository: ApiKeyRepository,
    override val oAuthRepository: OAuthRepository,
    override val deviceTokenRepository: DeviceTokenRepository,
    override val sessionRepository: SessionRepository,
    override val voteRepository: VoteRepository,
    override val pollRepository: PollRepository,
    override val notificationRepository: NotificationRepository,
) : PlatformPersistence {
    override fun close() {
        (dataSource as? AutoCloseable)?.close()
    }
}

fun createPersistenceComponents(
    config: AppConfig,
    extensionMigrations: ExtensionMigrations? = null,
): PersistenceComponents {
    val ds = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword, config.runtime)
    try {
        runMigrations(ds, config, extensionMigrations)
    } catch (e: Exception) {
        ds.close()
        throw e
    }
    if (config.devMode) {
        val seedJdbi = Jdbi.create(ds)
        seedJdbi.registerArgument(io.github.rygel.outerstellar.platform.persistence.InstantArgumentFactory())
        JdbiUserRepository(seedJdbi).seedAdminUser(DEV_ADMIN_PLACEHOLDER_HASH)
    }

    val jdbi =
        Jdbi.create(ds).also {
            it.registerArgument(io.github.rygel.outerstellar.platform.persistence.InstantArgumentFactory())
            it.registerColumnMapper(io.github.rygel.outerstellar.platform.persistence.InstantColumnMapper())
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

@Deprecated(
    "Use createPersistenceComponents(config, ExtensionMigrations?) instead.",
    ReplaceWith(
        "createPersistenceComponents(config, extensionMigrationSource?.let { ExtensionMigrations(location = it) })"
    ),
)
fun createPersistenceComponents(config: AppConfig, extensionMigrationSource: String?): PersistenceComponents =
    createPersistenceComponents(config, extensionMigrationSource?.let { ExtensionMigrations(location = it) })

private fun runMigrations(ds: DataSource, config: AppConfig, extensionMigrations: ExtensionMigrations?) {
    if (!config.runtime.flywayEnabled) return
    migrate(
        ds,
        extensionLocation = extensionMigrations?.location,
        extensionMigrationNames = extensionMigrations?.migrationNames,
    )
}
