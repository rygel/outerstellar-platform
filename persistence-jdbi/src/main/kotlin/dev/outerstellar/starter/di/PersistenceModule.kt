package dev.outerstellar.starter.di

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.persistence.ContactRepository
import dev.outerstellar.starter.persistence.JdbiApiKeyRepository
import dev.outerstellar.starter.persistence.JdbiAuditRepository
import dev.outerstellar.starter.persistence.JdbiContactRepository
import dev.outerstellar.starter.persistence.JdbiMessageRepository
import dev.outerstellar.starter.persistence.JdbiOutboxRepository
import dev.outerstellar.starter.persistence.JdbiPasswordResetRepository
import dev.outerstellar.starter.persistence.JdbiTransactionManager
import dev.outerstellar.starter.persistence.JdbiUserRepository
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.security.ApiKeyRepository
import dev.outerstellar.starter.security.AuditRepository
import dev.outerstellar.starter.security.PasswordResetRepository
import dev.outerstellar.starter.security.UserRepository
import io.micrometer.core.instrument.Metrics
import javax.sql.DataSource
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.koin.dsl.module

val persistenceModule
    get() = module {
        single<DataSource> {
            val config = get<dev.outerstellar.starter.AppConfig>()
            createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword)
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
    }
