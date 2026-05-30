package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.ContactRepository
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.persistence.createConfiguredMessageCache
import io.github.rygel.outerstellar.platform.service.ApnsPushNotificationService
import io.github.rygel.outerstellar.platform.service.ConsolePushNotificationService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.EmailService
import io.github.rygel.outerstellar.platform.service.EventPublisher
import io.github.rygel.outerstellar.platform.service.FcmPushNotificationService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NoOpEmailService
import io.github.rygel.outerstellar.platform.service.NoOpEventPublisher
import io.github.rygel.outerstellar.platform.service.OutboxProcessor
import io.github.rygel.outerstellar.platform.service.PushNotificationService
import io.github.rygel.outerstellar.platform.service.ResilientEmailService
import io.github.rygel.outerstellar.platform.service.SmtpConfig
import io.github.rygel.outerstellar.platform.service.SmtpEmailService

class CoreComponents(
    val messageService: MessageService,
    val messageCache: MessageCache,
    val contactService: ContactService,
    val outboxProcessor: OutboxProcessor,
    val eventPublisher: EventPublisher,
    val emailService: EmailService,
    val pushNotificationService: PushNotificationService,
)

fun createConfiguredEmailService(config: AppConfig): EmailService =
    if (config.email.enabled && config.email.host.isNotBlank()) {
        ResilientEmailService(
            SmtpEmailService(
                SmtpConfig(
                    host = config.email.host,
                    port = config.email.port,
                    username = config.email.username,
                    password = config.email.password,
                    from = config.email.from,
                    startTls = config.email.startTls,
                )
            )
        )
    } else {
        NoOpEmailService()
    }

fun createCoreComponents(
    config: AppConfig,
    messageRepository: MessageRepository,
    contactRepository: ContactRepository,
    outboxRepository: OutboxRepository,
    messageCache: MessageCache = createConfiguredMessageCache(config.runtime),
    transactionManager: TransactionManager? = null,
    auditRepository: AuditRepository? = null,
    eventPublisher: EventPublisher = NoOpEventPublisher,
    emailService: EmailService = createConfiguredEmailService(config),
): CoreComponents {
    val messageService =
        MessageService(
            repository = messageRepository,
            outboxRepository = outboxRepository,
            transactionManager = transactionManager,
            cache = messageCache,
            eventPublisher = eventPublisher,
            auditRepository = auditRepository,
        )
    val contactService =
        ContactService(
            repository = contactRepository,
            eventPublisher = eventPublisher,
            transactionManager = transactionManager,
            auditRepository = auditRepository,
        )
    val outboxProcessor = OutboxProcessor(outboxRepository = outboxRepository, transactionManager = transactionManager)
    val pushNotificationConfig = config.pushNotifications
    val pushNotificationService: PushNotificationService =
        if (!pushNotificationConfig.enabled) {
            ConsolePushNotificationService
        } else {
            when (pushNotificationConfig.provider) {
                "fcm" -> FcmPushNotificationService(pushNotificationConfig.fcmServiceAccountJson)
                "apns" ->
                    ApnsPushNotificationService(
                        privateKeyPem = pushNotificationConfig.apnsPrivateKeyPem,
                        teamId = pushNotificationConfig.apnsTeamId,
                        keyId = pushNotificationConfig.apnsKeyId,
                        bundleId = pushNotificationConfig.apnsBundleId,
                    )
                else -> ConsolePushNotificationService
            }
        }
    return CoreComponents(
        messageService = messageService,
        messageCache = messageCache,
        contactService = contactService,
        outboxProcessor = outboxProcessor,
        eventPublisher = eventPublisher,
        emailService = emailService,
        pushNotificationService = pushNotificationService,
    )
}
