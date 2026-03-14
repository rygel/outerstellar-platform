package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.migrate
import javax.sql.DataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

@Suppress("UtilityClassWithPublicConstructor")
abstract class H2WebTest {
    companion object {
        private const val jdbcUrl = "jdbc:h2:mem:webtestdb_unique;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        private val dataSource: DataSource by lazy {
            createDataSource(jdbcUrl, "sa", "").also { migrate(it) }
        }

        val testConfig =
            dev.outerstellar.starter.AppConfig(
                port = 0,
                jdbcUrl = jdbcUrl,
                devDashboardEnabled = true,
            )

        val testDsl: DSLContext by lazy { DSL.using(dataSource, SQLDialect.H2) }

        fun setup() {
            // Initialization is handled by lazy properties
        }

        fun cleanup() {
            testDsl.execute("SET REFERENTIAL_INTEGRITY FALSE")
            testDsl.execute("TRUNCATE TABLE MESSAGES")
            testDsl.execute("TRUNCATE TABLE OUTBOX")
            testDsl.execute("TRUNCATE TABLE USERS")
            testDsl.execute("TRUNCATE TABLE SYNC_STATE")
            testDsl.execute("TRUNCATE TABLE AUDIT_LOG")
            testDsl.execute("TRUNCATE TABLE PASSWORD_RESET_TOKENS")
            testDsl.execute("SET REFERENTIAL_INTEGRITY TRUE")
        }
    }
}
