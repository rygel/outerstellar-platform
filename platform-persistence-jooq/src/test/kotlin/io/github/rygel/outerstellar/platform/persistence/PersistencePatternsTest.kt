package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import org.jooq.DSLContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

class PersistencePatternsTest {

    @Test
    fun `should verify soft delete fields are present`() {
        val container =
            PostgreSQLContainer<Nothing>("postgres:18").apply {
                withDatabaseName("outerstellar")
                withUsername("outerstellar")
                withPassword("outerstellar")
                start()
            }

        val dataSource = createDataSource(container.jdbcUrl, container.username, container.password)
        migrate(dataSource)

        val dsl: DSLContext = DSL.using(dataSource, POSTGRES)

        val messagesMeta = dsl.meta().getTables("plt_messages").firstOrNull()
        assertNotNull(messagesMeta?.field("deleted_at"), "plt_messages table should have deleted_at field")

        val outboxMeta = dsl.meta().getTables("plt_outbox").firstOrNull()
        assertNotNull(outboxMeta?.field("processed_at"), "plt_outbox table should have processed_at field")

        container.stop()
    }
}
