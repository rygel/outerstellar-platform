package dev.outerstellar.starter.swing

import dev.outerstellar.starter.infra.migrate
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import javax.sql.DataSource

class SwingStartupE2ETest : KoinTest {

    @BeforeEach
    fun setup() {
        stopKoin()
        startKoin {
            allowOverride(true)
            modules(
                swingRuntimeModules() + module {
                    single {
                        SwingAppConfig(
                            serverBaseUrl = "http://localhost:8080",
                            jdbcUrl = "jdbc:h2:mem:swing_startup_e2e;MODE=PostgreSQL",
                            jdbcUser = "sa",
                            jdbcPassword = ""
                        )
                    }
                }
            )
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `startup module graph resolves critical services`() {
        val dataSource = get<DataSource>()
        migrate(dataSource)

        assertNotNull(get<MessageService>())
        assertNotNull(get<SyncService>())
        assertNotNull(get<SyncViewModel>())
    }
}
