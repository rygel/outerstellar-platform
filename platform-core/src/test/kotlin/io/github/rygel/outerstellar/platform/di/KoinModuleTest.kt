package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.KoinTest
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class KoinModuleTest : KoinTest {

    @Test
    fun `core modules should be valid`() {
        coreModule.verify(
            extraTypes =
                listOf(
                    MessageRepository::class,
                    io.github.rygel.outerstellar.platform.persistence.ContactRepository::class,
                    OutboxRepository::class,
                    MessageCache::class,
                    TransactionManager::class,
                )
        )
    }
}
