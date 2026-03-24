package io.github.rygel.outerstellar.platform.seed

import io.github.rygel.outerstellar.platform.di.coreModule
import io.github.rygel.outerstellar.platform.di.persistenceModule
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.persistence.ContactRepository
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.security.securityModule
import java.util.UUID
import javax.sql.DataSource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.seed.SeedData")

object SeedComponent : KoinComponent {
    val dataSource: DataSource by inject()
    val messageRepository: MessageRepository by inject()
    val contactRepository: ContactRepository by inject()
    val userRepository: UserRepository by inject()
}

fun main() {
    logger.info("Starting database seed process...")

    startKoin { modules(persistenceModule, coreModule, securityModule) }

    val seed = SeedComponent
    migrate(seed.dataSource)

    logger.info("Seeding messages...")
    seed.messageRepository.seedMessages()

    logger.info("Seeding contacts...")
    seed.contactRepository.seedContacts()

    logger.info("Seeding users...")
    seedUsers(seed.userRepository)

    logger.info("Database seeding completed successfully.")
}

private fun seedUsers(repo: UserRepository) {
    val encoder = BCryptPasswordEncoder(logRounds = 10)

    val users =
        listOf(
            Triple("admin", "admin@outerstellar.de", UserRole.ADMIN),
            Triple("alice", "alice@example.com", UserRole.USER),
            Triple("bob", "bob@example.com", UserRole.USER),
            Triple("carol", "carol@example.com", UserRole.ADMIN),
        )

    users.forEach { (username, email, role) ->
        if (repo.findByUsername(username) == null) {
            repo.save(
                User(
                    id = UUID.randomUUID(),
                    username = username,
                    email = email,
                    passwordHash = encoder.encode("password123"),
                    role = role,
                )
            )
            logger.info("Seeded user: {} ({})", username, role)
        } else {
            logger.info("User {} already exists, skipping", username)
        }
    }
}
