package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
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
                        mockk<io.github.rygel.outerstellar.platform.persistence.ContactRepository>(
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
