package dev.outerstellar.platform.di

import dev.outerstellar.platform.service.MessageService
import dev.outerstellar.platform.swing.ConnectivityChecker
import dev.outerstellar.platform.swing.SystemTrayNotifier
import dev.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.i18n.I18nService
import org.junit.jupiter.api.Test
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class KoinModuleTest : KoinTest {

    @Test
    fun `desktop application modules should be valid`() {
        desktopModule.verify(
            extraTypes =
                listOf(
                    MessageService::class,
                    dev.outerstellar.platform.service.ContactService::class,
                    SyncService::class,
                    I18nService::class,
                    SystemTrayNotifier::class,
                    dev.outerstellar.platform.swing.SwingAppConfig::class,
                    ConnectivityChecker::class,
                    java.lang.String::class,
                    Boolean::class,
                    Int::class,
                )
        )
    }
}
