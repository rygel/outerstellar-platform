package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.service.EmailService
import io.github.rygel.outerstellar.platform.service.EventPublisher
import io.github.rygel.outerstellar.platform.service.PushNotificationService
import org.koin.dsl.module

@Deprecated("Use createCoreComponents() for server runtime. This exists for desktop Koin compatibility only.")
val coreModule
    get() = module {
        single {
            val config = get<AppConfig>()
            createCoreComponents(
                config = config,
                messageRepository = get(),
                contactRepository = get(),
                outboxRepository = get(),
                messageCache = get<MessageCache>(),
                transactionManager = getOrNull(),
                auditRepository = getOrNull(),
                eventPublisher = get<EventPublisher>(),
                emailService = get<EmailService>(),
            )
        }
        single { get<CoreComponents>().messageService }
        single { get<CoreComponents>().contactService }
        single { get<CoreComponents>().outboxProcessor }
        single<EventPublisher> { get<CoreComponents>().eventPublisher }
        single<EmailService> { get<CoreComponents>().emailService }
        single<PushNotificationService> { get<CoreComponents>().pushNotificationService }
    }
