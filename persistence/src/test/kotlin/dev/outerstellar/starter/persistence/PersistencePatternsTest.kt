package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.migrate
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PersistencePatternsTest {

    @Test
    fun `should verify soft delete fields are present in H2 schema`() {
        val jdbcUrl = "jdbc:h2:mem:patternstest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        val dataSource = createDataSource(jdbcUrl, "sa", "")
        migrate(dataSource)

        val dsl: DSLContext = DSL.using(dataSource, SQLDialect.H2)

        // Use jOOQ meta to verify fields exist
        val messagesMeta = dsl.meta().getTables("MESSAGES").firstOrNull()
        assertNotNull(
            messagesMeta?.field("DELETED_AT"),
            "MESSAGES table should have DELETED_AT field",
        )

        val outboxMeta = dsl.meta().getTables("OUTBOX").firstOrNull()
        assertNotNull(
            outboxMeta?.field("PROCESSED_AT"),
            "OUTBOX table should have PROCESSED_AT field",
        )
    }
}
