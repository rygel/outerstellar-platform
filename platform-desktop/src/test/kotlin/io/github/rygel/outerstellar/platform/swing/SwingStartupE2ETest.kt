package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.SyncService
import javax.sql.DataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get

class SwingStartupE2ETest : KoinTest {

    @BeforeEach
    fun setup() {
        stopKoin()
        startKoin {
            allowOverride(true)
            modules(
                swingRuntimeModules() +
                    module {
                        single {
                            SwingAppConfig(
                                serverBaseUrl = "http://localhost:8080",
                                jdbcUrl = "jdbc:h2:mem:swing_startup_e2e;MODE=PostgreSQL",
                                jdbcUser = "sa",
                                jdbcPassword = "",
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
