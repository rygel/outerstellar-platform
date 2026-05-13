package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.PluginMigrationSource
import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.infra.migratePlugin
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.ContactRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiAuditRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiContactRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiDeviceTokenRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiOAuthRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiOutboxRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiPasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiTransactionManager
import io.github.rygel.outerstellar.platform.persistence.JdbiUserRepository
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.security.ApiKeyRepository
import io.github.rygel.outerstellar.platform.security.CachingUserRepository
import io.github.rygel.outerstellar.platform.security.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.security.OAuthRepository
import io.github.rygel.outerstellar.platform.security.PasswordResetRepository
import io.github.rygel.outerstellar.platform.security.SessionRepository
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.micrometer.core.instrument.Metrics
import javax.sql.DataSource
import org.jdbi.v3.core.Jdbi
import org.koin.dsl.module

/**
 * Persistence module backed by JDBI. Suitable for environments that do not use jOOQ code generation (e.g., lightweight
 * deployments, embedded databases). Wire this into your Koin app in place of the jOOQ module — never include both
 * `platform-persistence-jdbi` and `platform-persistence-jooq` at runtime.
 */
val persistenceModule
    get() = module {
        single<DataSource> {
            val config = get<io.github.rygel.outerstellar.platform.AppConfig>()
            val ds = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword, config.runtime)
            try {
                if (config.runtime.flywayEnabled) {
                    migrate(ds)
                    getOrNull<PluginMigrationSource>()?.let { plugin ->
                        plugin.migrationLocation?.let { location ->
                            migratePlugin(ds, location, plugin.migrationHistoryTable)
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
            ds
        }

        single<Jdbi> {
            Jdbi.create(get<DataSource>()).also {
                if (Metrics.globalRegistry.find("database.connections.active").gauge() == null) {
                    Metrics.globalRegistry.gauge("database.connections.active", 1)
                }
            }
        }

        single<MessageRepository> { JdbiMessageRepository(get()) }

        single<ContactRepository> { JdbiContactRepository(get()) }

        single<UserRepository> { CachingUserRepository(JdbiUserRepository(get())) }

        single<OutboxRepository> { JdbiOutboxRepository(get()) }

        single<TransactionManager> { JdbiTransactionManager(get()) }

        single<AuditRepository> { JdbiAuditRepository(get()) }

        single<PasswordResetRepository> { JdbiPasswordResetRepository(get()) }

        single<ApiKeyRepository> { JdbiApiKeyRepository(get()) }

        single<OAuthRepository> { JdbiOAuthRepository(get()) }

        single<DeviceTokenRepository> { JdbiDeviceTokenRepository(get()) }

        single<SessionRepository> { JdbiSessionRepository(get()) }
    }

private const val DEV_ADMIN_PLACEHOLDER_HASH = "\$2a\$04\$DevPlaceholderAdminXXuZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZe"
