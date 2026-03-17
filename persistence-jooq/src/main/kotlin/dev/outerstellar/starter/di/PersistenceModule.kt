package dev.outerstellar.starter.di

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.persistence.ContactRepository
import dev.outerstellar.starter.persistence.JooqApiKeyRepository
import dev.outerstellar.starter.persistence.JooqAuditRepository
import dev.outerstellar.starter.persistence.JooqContactRepository
import dev.outerstellar.starter.persistence.JooqDeviceTokenRepository
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.JooqOAuthRepository
import dev.outerstellar.starter.persistence.JooqOutboxRepository
import dev.outerstellar.starter.persistence.JooqPasswordResetRepository
import dev.outerstellar.starter.persistence.JooqTransactionManager
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.security.ApiKeyRepository
import dev.outerstellar.starter.security.AuditRepository
import dev.outerstellar.starter.security.DeviceTokenRepository
import dev.outerstellar.starter.security.OAuthRepository
import dev.outerstellar.starter.security.PasswordResetRepository
import dev.outerstellar.starter.security.UserRepository
import io.micrometer.core.instrument.Metrics
import javax.sql.DataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.koin.dsl.module

val persistenceModule
    get() = module {
        single<DataSource> {
            val config = get<dev.outerstellar.starter.AppConfig>()
            createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword)
        }

        single<DSLContext> {
            DSL.using(get<DataSource>(), SQLDialect.H2).also {
                if (Metrics.globalRegistry.find("database.connections.active").gauge() == null) {
                    Metrics.globalRegistry.gauge("database.connections.active", 1)
                }
            }
        }

        single<MessageRepository> { JooqMessageRepository(get()) }

        single<ContactRepository> { JooqContactRepository(get()) }

        single<UserRepository> { JooqUserRepository(get()) }

        single<OutboxRepository> { JooqOutboxRepository(get()) }

        single<TransactionManager> { JooqTransactionManager(get()) }

        single<AuditRepository> { JooqAuditRepository(get()) }

        single<PasswordResetRepository> { JooqPasswordResetRepository(get()) }

        single<ApiKeyRepository> { JooqApiKeyRepository(get()) }

        single<OAuthRepository> { JooqOAuthRepository(get()) }

        single<DeviceTokenRepository> { JooqDeviceTokenRepository(get()) }
    }
