package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.migrate
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import javax.sql.DataSource

class JooqMessageRepositoryTest {

    private lateinit var dataSource: DataSource
    private lateinit var repository: JooqMessageRepository

    @BeforeEach
    fun setup() {
        val jdbcUrl = "jdbc:h2:mem:messagerepotest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        dataSource = createDataSource(jdbcUrl, "sa", "")
        migrate(dataSource)
        val dsl = DSL.using(dataSource, SQLDialect.H2)
        repository = JooqMessageRepository(dsl)
    }

    @AfterEach
    fun teardown() {
        // H2 in-memory DB will be closed when the connection is closed
    }

    @Test
    fun `listMessages should be null-safe for all parameters`() {
        assertDoesNotThrow {
            repository.listMessages(
                query = null,
                year = null,
                limit = 10,
                offset = 0,
                includeDeleted = false
            )
        }
    }
}
