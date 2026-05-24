package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.di.createCoreComponents
import io.github.rygel.outerstellar.platform.di.createPersistenceComponents
import io.github.rygel.outerstellar.platform.persistence.NoOpMessageCache
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.DesktopAppConfig
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

class SwingStartupE2ETest {

    companion object {
        private val container =
            PostgreSQLContainer<Nothing>("postgres:18").apply {
                withDatabaseName("outerstellar")
                withUsername("outerstellar")
                withPassword("outerstellar")
                start()
            }
    }

    private lateinit var persistence: io.github.rygel.outerstellar.platform.di.PersistenceComponents
    private lateinit var core: io.github.rygel.outerstellar.platform.di.CoreComponents
    private lateinit var authModule: AuthModule
    private lateinit var syncViewModel: SyncViewModel

    @BeforeEach
    fun setup() {
        val appConfig =
            DesktopAppConfig(
                serverBaseUrl = "http://localhost:8080",
                jdbcUrl = container.jdbcUrl,
                jdbcUser = container.username,
                jdbcPassword = container.password,
            )
        val config =
            AppConfig(jdbcUrl = appConfig.jdbcUrl, jdbcUser = appConfig.jdbcUser, jdbcPassword = appConfig.jdbcPassword)
        persistence = createPersistenceComponents(config)
        core =
            createCoreComponents(
                config = config,
                messageRepository = persistence.messageRepository,
                contactRepository = persistence.contactRepository,
                outboxRepository = persistence.outboxRepository,
                messageCache = NoOpMessageCache,
                transactionManager = persistence.transactionManager,
                auditRepository = persistence.auditRepository,
            )
    }

    @AfterEach
    fun tearDown() {
        if (::persistence.isInitialized) {
            (persistence.dataSource as? com.zaxxer.hikari.HikariDataSource)?.close()
        }
    }

    @Test
    fun `startup module graph resolves critical services`() {
        assertNotNull(core.messageService)
        assertNotNull(persistence.dataSource)
    }
}
