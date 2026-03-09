package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.migrate
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertTrue

@Testcontainers
class MigrationTest {

    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
        withDatabaseName("testdb")
        withUsername("test")
        withPassword("test")
    }

    @Test
    fun `migrations are applied correctly on PostgreSQL`() {
        val dataSource = createDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        
        // This will run Flyway migrations
        migrate(dataSource)
        
        // Verify that tables exist by trying to query them
        dataSource.connection.use { conn ->
            val rs = conn.metaData.getTables(null, "public", "messages", null)
            assertTrue(rs.next(), "messages table should exist")
            
            val rsSync = conn.metaData.getColumns(null, "public", "messages", "sync_id")
            assertTrue(rsSync.next(), "sync_id column should exist in messages table")
        }
    }
}
