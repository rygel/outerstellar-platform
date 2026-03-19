package dev.outerstellar.platform.di

import dev.outerstellar.platform.persistence.MessageCache
import dev.outerstellar.platform.persistence.MessageRepository
import dev.outerstellar.platform.persistence.OutboxRepository
import dev.outerstellar.platform.persistence.TransactionManager
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.check.checkModules

class KoinModuleTest : KoinTest {

    @Test
    fun `api-client modules should be valid`() {
        checkModules {
            modules(
                apiClientModule,
                module {
                    single(named("serverBaseUrl")) { "http://localhost:8080" }
                    single { mockk<MessageRepository>(relaxed = true) }
                    single { mockk<OutboxRepository>(relaxed = true) }
                    single { mockk<MessageCache>(relaxed = true) }
                    single { mockk<TransactionManager>(relaxed = true) }
                },
            )
        }
    }
}
