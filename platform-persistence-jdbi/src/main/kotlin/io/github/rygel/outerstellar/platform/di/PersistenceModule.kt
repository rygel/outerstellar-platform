package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.ContactRepository
import io.github.rygel.outerstellar.platform.persistence.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.NotificationRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.PollRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.persistence.VoteRepository
import io.github.rygel.outerstellar.platform.security.OAuthRepository
import org.koin.dsl.module

@Deprecated("Use createPersistenceComponents() for server runtime. This exists for desktop Koin compatibility only.")
val persistenceModule
    get() = module {
        single {
            val config = get<AppConfig>()
            createPersistenceComponents(config, getOrNull())
        }
        single { get<PersistenceComponents>().dataSource }
        single { get<PersistenceComponents>().jdbi }
        single<MessageRepository> { get<PersistenceComponents>().messageRepository }
        single<ContactRepository> { get<PersistenceComponents>().contactRepository }
        single<UserRepository> { get<PersistenceComponents>().userRepository }
        single<OutboxRepository> { get<PersistenceComponents>().outboxRepository }
        single<TransactionManager> { get<PersistenceComponents>().transactionManager }
        single<AuditRepository> { get<PersistenceComponents>().auditRepository }
        single<PasswordResetRepository> { get<PersistenceComponents>().passwordResetRepository }
        single<ApiKeyRepository> { get<PersistenceComponents>().apiKeyRepository }
        single<OAuthRepository> { get<PersistenceComponents>().oAuthRepository }
        single<DeviceTokenRepository> { get<PersistenceComponents>().deviceTokenRepository }
        single<SessionRepository> { get<PersistenceComponents>().sessionRepository }
        single<VoteRepository> { get<PersistenceComponents>().voteRepository }
        single<PollRepository> { get<PersistenceComponents>().pollRepository }
        single<NotificationRepository> { get<PersistenceComponents>().notificationRepository }
    }
