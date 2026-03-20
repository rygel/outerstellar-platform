package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.ContactRepository
import io.github.rygel.outerstellar.platform.persistence.JooqApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.JooqAuditRepository
import io.github.rygel.outerstellar.platform.persistence.JooqContactRepository
import io.github.rygel.outerstellar.platform.persistence.JooqDeviceTokenRepository
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqOAuthRepository
import io.github.rygel.outerstellar.platform.persistence.JooqOutboxRepository
import io.github.rygel.outerstellar.platform.persistence.JooqPasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.JooqSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JooqTransactionManager
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.security.ApiKeyRepository
import io.github.rygel.outerstellar.platform.security.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.security.OAuthRepository
import io.github.rygel.outerstellar.platform.security.PasswordResetRepository
import io.github.rygel.outerstellar.platform.security.SessionRepository
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.micrometer.core.instrument.Metrics
import javax.sql.DataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.koin.dsl.module

val persistenceModule
    get() = module {
        single<DataSource> {
            val config = get<io.github.rygel.outerstellar.platform.AppConfig>()
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
            val config = get<io.github.rygel.outerstellar.platform.AppConfig>()
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
