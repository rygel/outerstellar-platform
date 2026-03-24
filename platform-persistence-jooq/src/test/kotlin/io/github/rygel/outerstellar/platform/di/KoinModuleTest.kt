package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.AppConfig
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.KoinTest
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class KoinModuleTest : KoinTest {

    @Test
    fun `persistence modules should be valid`() {
        persistenceModule.verify(extraTypes = listOf(AppConfig::class, String::class))
    }
}
