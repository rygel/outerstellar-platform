package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.PluginMigrationSource
import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.infra.migratePlugin
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
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.PollRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.persistence.VoteRepository
import io.github.rygel.outerstellar.platform.security.CachingUserRepository
import io.github.rygel.outerstellar.platform.security.OAuthRepository
import io.micrometer.core.instrument.Metrics
import javax.sql.DataSource
import org.jdbi.v3.core.Jdbi

private const val DEV_ADMIN_PLACEHOLDER_HASH = "\$2a\$04\$DevPlaceholderAdminXXuZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZe"

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
    pluginMigrationSource: PluginMigrationSource? = null,
): PersistenceComponents {
    val ds = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword, config.runtime)
    try {
        if (config.runtime.flywayEnabled) {
            migrate(ds)
            pluginMigrationSource?.let { plugin ->
                plugin.migrationLocation?.let { location ->
                    migratePlugin(ds, location, plugin.migrationHistoryTable, plugin.migrationNames)
                }
            }
        }
    } catch (e: Exception) {
        ds.close()
        throw e
    }
    if (config.devMode) {
        JdbiUserRepository(Jdbi.create(ds)).seedAdminUser(DEV_ADMIN_PLACEHOLDER_HASH)
    }

    val jdbi =
        Jdbi.create(ds).also {
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
