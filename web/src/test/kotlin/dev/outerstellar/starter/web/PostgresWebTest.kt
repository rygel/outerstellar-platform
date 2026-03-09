package dev.outerstellar.starter.web

import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.migrate
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
abstract class PostgresWebTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("webtestdb")
            withUsername("test")
            withPassword("test")
        }

        lateinit var testConfig: AppConfig
        lateinit var testDsl: DSLContext

        @JvmStatic
        @BeforeAll
        fun setupPostgres() {
            postgres.start()
            testConfig = AppConfig(
                port = 0,
                jdbcUrl = postgres.jdbcUrl,
                jdbcUser = postgres.username,
                jdbcPassword = postgres.password
            )
            val dataSource = createDataSource(testConfig.jdbcUrl, testConfig.jdbcUser, testConfig.jdbcPassword)
            migrate(dataSource)
            testDsl = DSL.using(dataSource, SQLDialect.POSTGRES)
        }
    }
}
