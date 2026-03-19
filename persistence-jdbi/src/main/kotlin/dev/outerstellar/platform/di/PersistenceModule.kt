package dev.outerstellar.platform.di

import dev.outerstellar.platform.PluginMigrationSource
import dev.outerstellar.platform.infra.createDataSource
import dev.outerstellar.platform.infra.migrate
import dev.outerstellar.platform.infra.migratePlugin
import dev.outerstellar.platform.persistence.AuditRepository
import dev.outerstellar.platform.persistence.ContactRepository
import dev.outerstellar.platform.persistence.JdbiApiKeyRepository
import dev.outerstellar.platform.persistence.JdbiAuditRepository
import dev.outerstellar.platform.persistence.JdbiContactRepository
import dev.outerstellar.platform.persistence.JdbiDeviceTokenRepository
import dev.outerstellar.platform.persistence.JdbiMessageRepository
import dev.outerstellar.platform.persistence.JdbiOAuthRepository
import dev.outerstellar.platform.persistence.JdbiOutboxRepository
import dev.outerstellar.platform.persistence.JdbiPasswordResetRepository
import dev.outerstellar.platform.persistence.JdbiSessionRepository
import dev.outerstellar.platform.persistence.JdbiTransactionManager
import dev.outerstellar.platform.persistence.JdbiUserRepository
import dev.outerstellar.platform.persistence.MessageRepository
import dev.outerstellar.platform.persistence.OutboxRepository
import dev.outerstellar.platform.persistence.TransactionManager
import dev.outerstellar.platform.security.ApiKeyRepository
import dev.outerstellar.platform.security.DeviceTokenRepository
import dev.outerstellar.platform.security.OAuthRepository
import dev.outerstellar.platform.security.PasswordResetRepository
import dev.outerstellar.platform.security.SessionRepository
import dev.outerstellar.platform.security.UserRepository
import io.micrometer.core.instrument.Metrics
import javax.sql.DataSource
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.koin.dsl.module

val persistenceModule
    get() = module {
        single<DataSource> {
            val config = get<dev.outerstellar.platform.AppConfig>()
            val ds = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword)
            try {
                migrate(ds)
                getOrNull<PluginMigrationSource>()?.let { plugin ->
                    plugin.migrationLocation?.let { location ->
                        migratePlugin(ds, location, plugin.migrationHistoryTable)
                    }
                }
            } catch (e: Exception) {
                ds.close()
                throw e
            }
            ds
        }

        single<Jdbi> {
            Jdbi.create(get<DataSource>()).installPlugin(KotlinPlugin()).also {
                if (Metrics.globalRegistry.find("database.connections.active").gauge() == null) {
                    Metrics.globalRegistry.gauge("database.connections.active", 1)
                }
            }
        }

        single<MessageRepository> { JdbiMessageRepository(get()) }

        single<ContactRepository> { JdbiContactRepository(get()) }

        single<UserRepository> { JdbiUserRepository(get()) }

        single<OutboxRepository> { JdbiOutboxRepository(get()) }

        single<TransactionManager> { JdbiTransactionManager(get()) }

        single<AuditRepository> { JdbiAuditRepository(get()) }

        single<PasswordResetRepository> { JdbiPasswordResetRepository(get()) }

        single<ApiKeyRepository> { JdbiApiKeyRepository(get()) }

        single<OAuthRepository> { JdbiOAuthRepository(get()) }

        single<DeviceTokenRepository> { JdbiDeviceTokenRepository(get()) }

        single<SessionRepository> { JdbiSessionRepository(get()) }
    }
