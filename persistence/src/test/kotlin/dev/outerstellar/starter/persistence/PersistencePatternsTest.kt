package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.jooq.tables.references.MESSAGES
import dev.outerstellar.starter.jooq.tables.references.OUTBOX
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource
import org.flywaydb.core.Flyway

class PersistencePatternsTest {
    private lateinit var dataSource: DataSource
    private lateinit var repository: JooqMessageRepository
    private lateinit var outboxRepository: JooqOutboxRepository
    private val dsl = DSL.using("jdbc:h2:mem:persistence_patterns_test;DB_CLOSE_DELAY=-1", "sa", "")

    @BeforeEach
    fun setup() {
        dataSource = createDataSource("jdbc:h2:mem:persistence_patterns_test;DB_CLOSE_DELAY=-1", "sa", "")
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        
        // Clear tables for clean test
        dsl.deleteFrom(MESSAGES).execute()
        dsl.deleteFrom(OUTBOX).execute()
        
        repository = JooqMessageRepository(dsl, dsl)
        outboxRepository = JooqOutboxRepository(dsl, dsl)
    }

    @Test
    fun `soft delete message should hide it from list results`() {
        val msg = repository.createServerMessage("Author", "Content")
        val syncId = msg.syncId
        
        assertEquals(1, repository.listMessages().size)
        
        repository.softDelete(syncId)
        
        assertEquals(0, repository.listMessages().size)
        assertNull(repository.findBySyncId(syncId))
    }

    @Test
    fun `soft delete outbox entry should hide it from fetchUnprocessed`() {
        val id = UUID.randomUUID()
        val entry = OutboxEntry(id = id, payloadType = "TEST", payload = "data")
        outboxRepository.save(entry)
        
        assertEquals(1, outboxRepository.fetchUnprocessed().size)
        
        outboxRepository.softDelete(id)
        
        assertEquals(0, outboxRepository.fetchUnprocessed().size)
    }

    @Test
    fun `database should have the new soft delete columns`() {
        val messagesMeta = dsl.meta().getTables(MESSAGES.unqualifiedName).firstOrNull()
        assertNotNull(messagesMeta?.field("deleted_at"))
        
        val outboxMeta = dsl.meta().getTables(OUTBOX.unqualifiedName).firstOrNull()
        assertNotNull(outboxMeta?.field("deleted_at"))
    }
}
