package dev.outerstellar.starter.di

import org.junit.jupiter.api.Test
import org.koin.test.KoinTest
import org.koin.test.check.checkModules

class KoinModuleTest : KoinTest {

    @Test
    fun `desktop application modules should be valid`() {
        checkModules {
            modules(
                coreModule,
                persistenceModule,
                apiClientModule,
                desktopModule
            )
        }
    }
}
