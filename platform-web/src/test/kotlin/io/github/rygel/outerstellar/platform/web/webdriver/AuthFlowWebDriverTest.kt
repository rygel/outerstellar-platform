package io.github.rygel.outerstellar.platform.web.webdriver

import io.github.rygel.outerstellar.platform.web.WebTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.webdriver.Http4kWebDriver

class AuthFlowWebDriverTest : WebTest() {

    private val driver by lazy { Http4kWebDriver(buildApp()) }

    @Test
    fun `sign-in form fragment returns page content via webdriver`() {
        driver.get("http://test/auth/components/forms/sign-in")
        val source = driver.pageSource
        assertNotNull(source, "Page source should not be null")
        assertTrue(source.isNotEmpty(), "Page source should not be empty")
    }

    @Test
    fun `auth page contains password field via webdriver`() {
        driver.get("http://test/auth")
        val source = driver.pageSource
        assertNotNull(source)
        assertTrue(source.lowercase().contains("password"), "Auth page should contain password field")
    }

    @Test
    fun `webdriver renders sign-in form fragment`() {
        driver.get("http://test/auth/components/forms/sign-in")
        val source = driver.pageSource
        assertNotNull(source)
        val lowered = source.lowercase()
        assertTrue(lowered.contains("password"), "Sign-in form should contain password field")
        assertTrue(lowered.contains("sign") || lowered.contains("log"), "Sign-in form should have submit context")
    }
}
