package dev.outerstellar.starter.di

import dev.outerstellar.starter.security.securityModule
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.KoinTest
import org.koin.test.check.checkModules
import kotlin.test.Test

@OptIn(KoinExperimentalAPI::class)
class KoinModuleTest : KoinTest {
    @Test
    fun `web application modules should be valid`() {
        checkModules {
            modules(persistenceModule, coreModule, securityModule, webModule)
        }
    }
}
