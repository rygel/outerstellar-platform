package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.jooq.tables.references.USERS
import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.UserRole
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.util.UUID

class JooqUserRepository(private val dsl: DSLContext) : UserRepository {
    private val logger = LoggerFactory.getLogger(JooqUserRepository::class.java)

    override fun findById(id: UUID): User? {
        return dsl.selectFrom(USERS)
            .where(USERS.ID.eq(id))
            .fetchOne()?.let { record ->
                User(
                    id = record.id!!,
                    username = record.username!!,
                    email = record.email!!,
                    passwordHash = record.passwordHash!!,
                    role = UserRole.valueOf(record.role!!),
                    enabled = record.enabled!!
                )
            }
    }

    override fun findByUsername(username: String): User? {
        return dsl.selectFrom(USERS)
            .where(USERS.USERNAME.eq(username))
            .fetchOne()?.let { record ->
                User(
                    id = record.id!!,
                    username = record.username!!,
                    email = record.email!!,
                    passwordHash = record.passwordHash!!,
                    role = UserRole.valueOf(record.role!!),
                    enabled = record.enabled!!
                )
            }
    }

    override fun findByEmail(email: String): User? {
        return dsl.selectFrom(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne()?.let { record ->
                User(
                    id = record.id!!,
                    username = record.username!!,
                    email = record.email!!,
                    passwordHash = record.passwordHash!!,
                    role = UserRole.valueOf(record.role!!),
                    enabled = record.enabled!!
                )
            }
    }

    override fun save(user: User) {
        dsl.insertInto(USERS)
            .set(USERS.ID, user.id)
            .set(USERS.USERNAME, user.username)
            .set(USERS.EMAIL, user.email)
            .set(USERS.PASSWORD_HASH, user.passwordHash)
            .set(USERS.ROLE, user.role.name)
            .set(USERS.ENABLED, user.enabled)
            .onDuplicateKeyUpdate()
            .set(USERS.PASSWORD_HASH, user.passwordHash)
            .set(USERS.ROLE, user.role.name)
            .set(USERS.ENABLED, user.enabled)
            .execute()
    }

    fun seedAdminUser(passwordHash: String) {
        if (findByUsername("admin") == null) {
            logger.info("Seeding default admin user")
            save(
                User(
                    id = UUID.randomUUID(),
                    username = "admin",
                    email = "admin@outerstellar.de",
                    passwordHash = passwordHash,
                    role = UserRole.ADMIN
                )
            )
        }
    }
}
