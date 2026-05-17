package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

class MigrationTest {

    @Test
    fun `migrations are applied correctly`() {
        val jdbcUrl = System.getenv("TEST_JDBC_URL")
        val jdbcUser = System.getenv("TEST_JDBC_USER") ?: "outerstellar"
        val jdbcPassword = System.getenv("TEST_JDBC_PASSWORD") ?: "outerstellar"

        val container =
            if (jdbcUrl == null) {
                PostgreSQLContainer<Nothing>("postgres:18").apply {
                    withDatabaseName("outerstellar")
                    withUsername("outerstellar")
                    withPassword("outerstellar")
                    start()
                }
            } else null

        val url = jdbcUrl ?: container!!.jdbcUrl
        val user = if (jdbcUrl != null) jdbcUser else container!!.username
        val password = if (jdbcUrl != null) jdbcPassword else container!!.password

        val dataSource = createDataSource(url, user, password)

        migrate(dataSource)

        dataSource.connection.use { conn ->
            val rs = conn.metaData.getTables(null, "public", "plt_messages", null)
            assertTrue(rs.next(), "plt_messages table should exist")

            val rsSync = conn.metaData.getColumns(null, "public", "plt_messages", "sync_id")
            assertTrue(rsSync.next(), "sync_id column should exist in plt_messages table")
        }

        container?.stop()
    }
}
