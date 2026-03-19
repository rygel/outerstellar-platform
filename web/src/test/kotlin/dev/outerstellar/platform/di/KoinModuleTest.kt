package dev.outerstellar.platform.di

import dev.outerstellar.platform.security.securityModule
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.KoinTest
import org.koin.test.check.checkModules

@OptIn(KoinExperimentalAPI::class)
class KoinModuleTest : KoinTest {
    @Test
    fun `web application modules should be valid`() {
        checkModules { modules(persistenceModule, coreModule, securityModule, webModule) }
    }
}
