package dev.outerstellar.starter.di

import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
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
                        mockk<dev.outerstellar.starter.persistence.ContactRepository>(
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
