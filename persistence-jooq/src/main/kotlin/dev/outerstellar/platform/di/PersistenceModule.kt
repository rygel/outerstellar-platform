package dev.outerstellar.platform.di

import dev.outerstellar.platform.infra.createDataSource
import dev.outerstellar.platform.infra.migrate
import dev.outerstellar.platform.persistence.AuditRepository
import dev.outerstellar.platform.persistence.ContactRepository
import dev.outerstellar.platform.persistence.JooqApiKeyRepository
import dev.outerstellar.platform.persistence.JooqAuditRepository
import dev.outerstellar.platform.persistence.JooqContactRepository
import dev.outerstellar.platform.persistence.JooqDeviceTokenRepository
import dev.outerstellar.platform.persistence.JooqMessageRepository
import dev.outerstellar.platform.persistence.JooqOAuthRepository
import dev.outerstellar.platform.persistence.JooqOutboxRepository
import dev.outerstellar.platform.persistence.JooqPasswordResetRepository
import dev.outerstellar.platform.persistence.JooqSessionRepository
import dev.outerstellar.platform.persistence.JooqTransactionManager
import dev.outerstellar.platform.persistence.JooqUserRepository
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
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.koin.dsl.module

val persistenceModule
    get() = module {
        single<DataSource> {
            val config = get<dev.outerstellar.platform.AppConfig>()
            val ds = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword)
            try {
                migrate(ds)
            } catch (e: Exception) {
                ds.close()
                throw e
            }
            if (config.devMode) {
                val dialect =
                    if (config.jdbcUrl.startsWith("jdbc:postgresql:")) SQLDialect.POSTGRES
                    else SQLDialect.H2
                JooqUserRepository(DSL.using(ds, dialect)).seedAdminUser(DEV_ADMIN_PLACEHOLDER_HASH)
            }
            ds
        }

        single<DSLContext> {
            val config = get<dev.outerstellar.platform.AppConfig>()
            val dialect =
                if (config.jdbcUrl.startsWith("jdbc:postgresql:")) SQLDialect.POSTGRES
                else SQLDialect.H2
            DSL.using(get<DataSource>(), dialect).also {
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

        single<SessionRepository> { JooqSessionRepository(get()) }
    }

/**
 * Syntactically valid BCrypt hash used when seeding the dev admin user in devMode. This hash cannot
 * match any real password — devAutoLogin bypasses password checks entirely in dev mode, so only the
 * existence of the admin user matters.
 */
private const val DEV_ADMIN_PLACEHOLDER_HASH =
    "\$2a\$04\$DevPlaceholderAdminXXuZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZe"
