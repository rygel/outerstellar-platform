package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

class MigrationTest {

    @Test
    fun `migrations are applied correctly`() {
        val container =
            PostgreSQLContainer<Nothing>("postgres:18").apply {
                withDatabaseName("outerstellar")
                withUsername("outerstellar")
                withPassword("outerstellar")
                start()
            }

        val dataSource = createDataSource(container.jdbcUrl, container.username, container.password)

        migrate(dataSource)

        dataSource.connection.use { conn ->
            val rs = conn.metaData.getTables(null, "public", "plt_messages", null)
            assertTrue(rs.next(), "plt_messages table should exist")

            val rsSync = conn.metaData.getColumns(null, "public", "plt_messages", "sync_id")
            assertTrue(rsSync.next(), "sync_id column should exist in plt_messages table")
        }

        container.stop()
    }
}
