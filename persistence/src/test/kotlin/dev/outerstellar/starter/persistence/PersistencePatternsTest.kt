package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.migrate
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class PersistencePatternsTest {

    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
        withDatabaseName("patternstestdb")
        withUsername("test")
        withPassword("test")
    }

    @Test
    fun `should verify soft delete fields are present in schema`() {
        val dataSource = createDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        migrate(dataSource)
        
        val dsl: DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)
        
        // Use jOOQ meta to verify fields exist
        val messagesMeta = dsl.meta().getTables("MESSAGES").firstOrNull()
        assertNotNull(messagesMeta?.field("DELETED_AT"), "messages table should have deleted_at field")
        
        val outboxMeta = dsl.meta().getTables("OUTBOX").firstOrNull()
        assertNotNull(outboxMeta?.field("PROCESSED_AT"), "outbox table should have processed_at field")
    }
}
