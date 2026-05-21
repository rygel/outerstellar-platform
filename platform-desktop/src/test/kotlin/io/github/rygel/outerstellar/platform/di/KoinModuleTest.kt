package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.swing.SystemTrayNotifier
import io.github.rygel.outerstellar.platform.sync.engine.DesktopAppConfig
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import org.junit.jupiter.api.Test
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class KoinModuleTest : KoinTest {

    @Test
    fun `desktop application modules should be valid`() {
        desktopModule.verify(
            extraTypes =
                listOf(
                    AuthModule::class,
                    SyncDataModule::class,
                    ProfileModule::class,
                    AdminModule::class,
                    NotificationModule::class,
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
