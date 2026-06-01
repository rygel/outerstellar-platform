package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.ExtensionMigrations
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.ContactRepository
import io.github.rygel.outerstellar.platform.persistence.DeviceTokenRepository
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
import java.util.ServiceLoader

@Suppress("LongParameterList")
interface PlatformPersistence : AutoCloseable {
    val messageRepository: MessageRepository
    val contactRepository: ContactRepository
    val userRepository: UserRepository
    val outboxRepository: OutboxRepository
    val transactionManager: TransactionManager
    val auditRepository: AuditRepository
    val passwordResetRepository: PasswordResetRepository
    val apiKeyRepository: ApiKeyRepository
    val oAuthRepository: OAuthRepository
    val deviceTokenRepository: DeviceTokenRepository
    val sessionRepository: SessionRepository
    val voteRepository: VoteRepository
    val pollRepository: PollRepository
    val notificationRepository: NotificationRepository

    override fun close() = Unit
}

interface PersistenceBootstrap {
    /**
     * Creates the platform persistence adapters for the given application config.
     *
     * [extensionMigrations] should come from `PlatformExtension.migrations` when the host is starting an extension. The
     * bootstrap runs those migrations in a separate Flyway history table after the platform migrations.
     */
    fun create(config: AppConfig, extensionMigrations: ExtensionMigrations? = null): PlatformPersistence
}

fun loadPersistenceBootstrap(): PersistenceBootstrap {
    val providers = ServiceLoader.load(PersistenceBootstrap::class.java).toList()
    return when (providers.size) {
        1 -> providers.single()
        0 ->
            error(
                "No PersistenceBootstrap implementation found on the classpath. " +
                    "Add a runtime dependency such as outerstellar-platform-persistence-jdbi."
            )

        else ->
            error(
                "Expected exactly one PersistenceBootstrap implementation, found ${providers.size}: " +
                    providers.joinToString { it::class.java.name }
            )
    }
}
