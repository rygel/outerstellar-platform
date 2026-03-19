package dev.outerstellar.platform.swing

import dev.outerstellar.platform.AppConfig
import dev.outerstellar.platform.di.apiClientModule
import dev.outerstellar.platform.di.coreModule
import dev.outerstellar.platform.di.desktopModule
import dev.outerstellar.platform.di.persistenceModule
import dev.outerstellar.platform.persistence.MessageCache
import dev.outerstellar.platform.persistence.NoOpMessageCache
import dev.outerstellar.platform.service.SyncProvider
import dev.outerstellar.platform.swing.viewmodel.SyncViewModel
import dev.outerstellar.platform.sync.SyncService
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
                    single { AppConfig() }
                    single(named("jdbcUrl")) { "jdbc:h2:mem:test;MODE=PostgreSQL" }
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
