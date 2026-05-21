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
    fun `sync-client modules should be valid`() {
        apiClientModule.verify(
            extraTypes =
                listOf(
                    String::class,
                    MessageRepository::class,
                    OutboxRepository::class,
                    MessageCache::class,
                    TransactionManager::class,
                    io.github.rygel.outerstellar.platform.service.MessageService::class,
                    io.github.rygel.outerstellar.platform.analytics.AnalyticsService::class,
                    io.github.rygel.outerstellar.platform.sync.engine.ConnectivityChecker::class,
                    io.github.rygel.outerstellar.platform.sync.engine.module.ModuleNotifier::class,
                    io.github.rygel.outerstellar.platform.service.ContactService::class,
                )
        )
    }
}
