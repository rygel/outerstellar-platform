package io.github.rygel.outerstellar.platform.di

import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.KoinTest
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class KoinModuleTest : KoinTest {
    @Test
    fun `web application modules should be valid`() {
        webModule.verify()
    }
}
