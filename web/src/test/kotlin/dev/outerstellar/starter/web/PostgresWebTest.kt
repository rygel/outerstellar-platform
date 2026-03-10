package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.migrate
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@org.junit.jupiter.api.Disabled("Requires Docker environment")
object PostgresWebTest {
    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
        withDatabaseName("webtestdb")
        withUsername("test")
        withPassword("test")
    }

    val testConfig = dev.outerstellar.starter.AppConfig(
        port = 0,
        jdbcUrl = "jdbc:postgresql://localhost:5432/webtestdb",
        devDashboardEnabled = true
    )

    lateinit var testDsl: DSLContext
    lateinit var ctx: WebContext

    fun setup() {
        if (!this::testDsl.isInitialized) {
            val dataSource = createDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            migrate(dataSource)
            testDsl = DSL.using(dataSource, SQLDialect.POSTGRES)
            ctx = WebContext(org.http4k.core.Request(org.http4k.core.Method.GET, "/"), true)
        }
    }
}
