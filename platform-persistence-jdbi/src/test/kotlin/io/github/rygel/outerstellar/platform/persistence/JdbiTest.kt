package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.testing.SharedPostgres
import io.github.rygel.outerstellar.platform.testing.sanitizeDbName
import java.util.UUID
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JdbiTest {
    private val testDb = SharedPostgres.createDatabase(sanitizeDbName(this::class.simpleName!!))

    protected val jdbi: Jdbi by lazy { testDb.jdbi }

    private val tablesToDelete =
        listOf(
            "plt_sessions",
            "plt_notifications",
            "plt_device_tokens",
            "plt_oauth_connections",
            "plt_api_keys",
            "plt_password_reset_tokens",
            "plt_audit_log",
            "plt_outbox",
            "plt_contact_emails",
            "plt_contact_phones",
            "plt_contact_socials",
            "plt_contacts",
            "plt_messages",
            "plt_poll_votes",
            "plt_poll_options",
            "plt_polls",
            "plt_sync_state",
            "plt_users",
        )

    @AfterEach
    fun cleanDatabase() {
        jdbi.useHandle<Exception> { handle -> tablesToDelete.forEach { table -> handle.execute("DELETE FROM $table") } }
    }

    protected fun createUser(
        username: String = "user_${UUID.randomUUID().toString().take(6)}",
        role: UserRole = UserRole.USER,
    ): UUID {
        val id = UUID.randomUUID()
        JdbiUserRepository(jdbi)
            .save(
                User(
                    id = id,
                    username = username,
                    email = "${id.toString().take(6)}@example.com",
                    passwordHash = "hash",
                    role = role,
                )
            )
        return id
    }

    @AfterAll
    fun tearDown() {
        testDb.drop()
    }
}
