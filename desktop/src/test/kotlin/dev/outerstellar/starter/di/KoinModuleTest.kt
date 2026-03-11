package dev.outerstellar.starter.di

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.swing.SystemTrayNotifier
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.NoOpMessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncService
import org.junit.jupiter.api.Test
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class KoinModuleTest : KoinTest {

    @Test
    fun `desktop application modules should be valid`() {
        desktopModule.verify(
            extraTypes = listOf(
                MessageService::class,
                SyncService::class,
                I18nService::class,
                SystemTrayNotifier::class,
                dev.outerstellar.starter.swing.SwingAppConfig::class,
                java.lang.String::class
            )
        )
    }
}
