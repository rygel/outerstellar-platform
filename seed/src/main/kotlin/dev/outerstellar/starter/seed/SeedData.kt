package dev.outerstellar.starter.seed

import dev.outerstellar.starter.di.coreModule
import dev.outerstellar.starter.di.persistenceModule
import dev.outerstellar.starter.infra.migrate
import dev.outerstellar.starter.persistence.ContactRepository
import dev.outerstellar.starter.persistence.MessageRepository
import javax.sql.DataSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.seed.SeedData")

object SeedComponent : KoinComponent {
    val dataSource: DataSource by inject()
    val messageRepository: MessageRepository by inject()
    val contactRepository: ContactRepository by inject()
}

fun main() {
    logger.info("Starting database seed process...")

    startKoin { modules(persistenceModule, coreModule) }

    val seedComponent = SeedComponent
    migrate(seedComponent.dataSource)

    logger.info("Seeding messages...")
    seedComponent.messageRepository.seedStarterMessages()

    logger.info("Seeding contacts...")
    seedComponent.contactRepository.seedStarterContacts()

    logger.info("Database seeding completed successfully.")
}
