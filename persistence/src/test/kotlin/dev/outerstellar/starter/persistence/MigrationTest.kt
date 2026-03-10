package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.migrate
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class MigrationTest {

    @Test
    fun `migrations are applied correctly on H2`() {
        val jdbcUrl = "jdbc:h2:mem:migrationtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        val dataSource = createDataSource(jdbcUrl, "sa", "")
        
        // This will run Flyway migrations
        migrate(dataSource)
        
        // Verify that tables exist by trying to query them
        dataSource.connection.use { conn ->
            val rs = conn.metaData.getTables(null, "PUBLIC", "MESSAGES", null)
            assertTrue(rs.next(), "MESSAGES table should exist")
            
            val rsSync = conn.metaData.getColumns(null, "PUBLIC", "MESSAGES", "SYNC_ID")
            assertTrue(rsSync.next(), "SYNC_ID column should exist in MESSAGES table")
        }
    }
}
