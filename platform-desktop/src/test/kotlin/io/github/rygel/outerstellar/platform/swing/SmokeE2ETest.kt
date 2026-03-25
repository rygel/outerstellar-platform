package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.di.apiClientModule
import io.github.rygel.outerstellar.platform.di.coreModule
import io.github.rygel.outerstellar.platform.di.desktopModule
import io.github.rygel.outerstellar.platform.di.persistenceModule
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.NoOpMessageCache
import io.github.rygel.outerstellar.platform.service.SyncProvider
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.SyncService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

class SmokeE2ETest : KoinTest {

    @BeforeEach
    fun setup() {
        stopKoin()
        startKoin {
            modules(
                coreModule,
                persistenceModule,
                apiClientModule,
                desktopModule,
                module {
                    single {
                        AppConfig(jdbcUrl = "jdbc:h2:mem:smoketest_${System.nanoTime()};MODE=PostgreSQL")
                    }
                    single<MessageCache> { NoOpMessageCache }
                    single { SyncService(get(named("serverBaseUrl")), get(), get()) }
                    single<SyncProvider> { get<SyncService>() }
                },
            )
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `desktop application starts up and initializes viewmodel`() {
        val viewModel: SyncViewModel by inject()

        assertNotNull(viewModel)
        assertNotNull(viewModel.messages)
    }
}
