package io.github.rygel.outerstellar.platform.seeder

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.di.createPersistenceComponents
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import java.util.UUID
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.seeder.SeedData")

fun main() {
    logger.info("Starting database seed process...")

    val config = AppConfig.fromEnvironment()
    val persistence = createPersistenceComponents(config)

    logger.info("Seeding messages...")
    persistence.messageRepository.seedMessages()

    logger.info("Seeding contacts...")
    persistence.contactRepository.seedContacts()

    logger.info("Seeding users...")
    seedUsers(persistence.userRepository)

    logger.info("Database seeding completed successfully.")
}

private fun seedUsers(repo: io.github.rygel.outerstellar.platform.persistence.UserRepository) {
    val encoder = BCryptPasswordEncoder(logRounds = 10)
    val seedPassword = System.getenv("SEED_USER_PASSWORD")
    if (seedPassword.isNullOrBlank()) {
        logger.warn(
            "SEED_USER_PASSWORD env var not set — using insecure default. Set this for any non-local deployment."
        )
    }
    val password = seedPassword?.takeIf { it.isNotBlank() } ?: "password123"

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
                    passwordHash = encoder.encode(password),
                    role = role,
                )
            )
            logger.info("Seeded user: {} ({})", username, role)
        } else {
            logger.info("User {} already exists, skipping", username)
        }
    }
}
