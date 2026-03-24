package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
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
        val messagesMeta = dsl.meta().getTables("PLT_MESSAGES").firstOrNull()
        assertNotNull(messagesMeta?.field("DELETED_AT"), "PLT_MESSAGES table should have DELETED_AT field")

        val outboxMeta = dsl.meta().getTables("PLT_OUTBOX").firstOrNull()
        assertNotNull(outboxMeta?.field("PROCESSED_AT"), "PLT_OUTBOX table should have PROCESSED_AT field")
    }
}
