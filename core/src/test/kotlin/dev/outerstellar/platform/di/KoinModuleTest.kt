package dev.outerstellar.platform.di

import dev.outerstellar.platform.persistence.MessageCache
import dev.outerstellar.platform.persistence.MessageRepository
import dev.outerstellar.platform.persistence.OutboxRepository
import dev.outerstellar.platform.persistence.TransactionManager
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.check.checkModules

class KoinModuleTest : KoinTest {

    @Test
    fun `core modules should be valid`() {
        checkModules {
            modules(
                coreModule,
                module {
                    single { mockk<MessageRepository>(relaxed = true) }
                    single {
                        mockk<dev.outerstellar.platform.persistence.ContactRepository>(
                            relaxed = true
                        )
                    }
                    single { mockk<OutboxRepository>(relaxed = true) }
                    single { mockk<MessageCache>(relaxed = true) }
                    single { mockk<TransactionManager>(relaxed = true) }
                },
            )
        }
    }
}
