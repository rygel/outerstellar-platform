package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.swing.SystemTrayNotifier
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.platform.sync.engine.DesktopAppConfig
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
                    io.github.rygel.outerstellar.platform.service.ContactService::class,
                    SyncService::class,
                    I18nService::class,
                    SystemTrayNotifier::class,
                    DesktopAppConfig::class,
                    java.lang.String::class,
                    Boolean::class,
                    Int::class,
                )
        )
    }
}
