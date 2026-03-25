package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import javax.sql.DataSource
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class H2JdbiTest {

    protected lateinit var jdbi: Jdbi

    @BeforeEach
    fun setupDatabase() {
        jdbi = sharedJdbi
    }

    @AfterEach
    fun cleanDatabase() {
        jdbi.useHandle<Exception> { handle ->
            handle.execute("SET REFERENTIAL_INTEGRITY FALSE")
            handle.execute("TRUNCATE TABLE PLT_SESSIONS")
            handle.execute("TRUNCATE TABLE PLT_NOTIFICATIONS")
            handle.execute("TRUNCATE TABLE PLT_DEVICE_TOKENS")
            handle.execute("TRUNCATE TABLE PLT_OAUTH_CONNECTIONS")
            handle.execute("TRUNCATE TABLE PLT_API_KEYS")
            handle.execute("TRUNCATE TABLE PLT_PASSWORD_RESET_TOKENS")
            handle.execute("TRUNCATE TABLE PLT_AUDIT_LOG")
            handle.execute("TRUNCATE TABLE PLT_OUTBOX")
            handle.execute("TRUNCATE TABLE PLT_CONTACT_EMAILS")
            handle.execute("TRUNCATE TABLE PLT_CONTACT_PHONES")
            handle.execute("TRUNCATE TABLE PLT_CONTACT_SOCIALS")
            handle.execute("TRUNCATE TABLE PLT_CONTACTS")
            handle.execute("TRUNCATE TABLE PLT_MESSAGES")
            handle.execute("TRUNCATE TABLE PLT_SYNC_STATE")
            handle.execute("TRUNCATE TABLE PLT_USERS")
            handle.execute("SET REFERENTIAL_INTEGRITY TRUE")
        }
    }

    companion object {
        private val sharedDataSource: DataSource by lazy {
            createDataSource("jdbc:h2:mem:jdbitestdb_shared;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "").also {
                migrate(it)
            }
        }

        private val sharedJdbi: Jdbi by lazy { Jdbi.create(sharedDataSource) }
    }
}
