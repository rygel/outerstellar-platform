package io.github.rygel.outerstellar.platform.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Smoke tests that run against a live Docker container.
 *
 * Expects the application to be running at [baseUrl] (default: http://localhost:8080) with
 * DEVMODE=true so the devAutoLogin filter auto-authenticates localhost requests.
 *
 * Run locally: E2E_BASE_URL=http://localhost:8080 mvn test -pl platform-web -Dgroups=docker-e2e -DexcludedGroups=
 */
@Tag("docker-e2e")
class DockerSmokeTest {

    private lateinit var context: BrowserContext
    private lateinit var page: Page

    companion object {
        val baseUrl: String = System.getProperty("E2E_BASE_URL", "http://localhost:8080")

        private lateinit var playwright: Playwright
        private lateinit var browser: Browser

        @JvmStatic
        @BeforeAll
        fun setupPlaywright() {
            playwright = Playwright.create()
            browser = playwright.chromium().launch()
        }

        @JvmStatic
        @AfterAll
        fun teardownPlaywright() {
            browser.close()
            playwright.close()
        }
    }

    @BeforeEach
    fun setup() {
        context = browser.newContext()
        page = context.newPage()
    }

    @AfterEach
    fun teardown() {
        page.close()
        context.close()
    }

    @Test
    fun `health endpoint reports UP`() {
        page.navigate("$baseUrl/health")
        val body = page.locator("body").textContent()
        assertTrue(body.contains("UP"), "Expected health status UP but got: $body")
    }

    @Test
    fun `home page loads with devMode auto-login`() {
        page.navigate("$baseUrl/")
        page.waitForLoadState(LoadState.NETWORKIDLE)
        val title = page.title()
        assertTrue(title.isNotBlank(), "Page title should not be blank")
        // Should not be redirected to a 5xx error
        val content = page.content()
        assertTrue(
            !content.contains("500") && !content.contains("Internal Server Error", ignoreCase = true),
            "Home page should not show a server error",
        )
    }

    @Test
    fun `auth page renders login form`() {
        // Use a fresh context with no session cookie to force the auth page
        val incognito = browser.newContext()
        val authPage = incognito.newPage()
        try {
            authPage.navigate("$baseUrl/auth")
            authPage.waitForLoadState(LoadState.NETWORKIDLE)
            val content = authPage.content()
            assertTrue(
                content.contains("email", ignoreCase = true) || content.contains("sign", ignoreCase = true),
                "Auth page should render a login form",
            )
            assertTrue(
                authPage.locator("input[name='email']").count() > 0,
                "Auth page should have an email input field",
            )
        } finally {
            authPage.close()
            incognito.close()
        }
    }

    @Test
    fun `contacts page loads`() {
        page.navigate("$baseUrl/contacts")
        page.waitForLoadState(LoadState.NETWORKIDLE)
        val content = page.content()
        assertTrue(
            !content.contains("500") && !content.contains("Internal Server Error", ignoreCase = true),
            "Contacts page should not show a server error",
        )
    }

    @Test
    fun `unknown route returns 404 error page`() {
        page.navigate("$baseUrl/nonexistent-page-xyz-12345")
        val content = page.content()
        assertTrue(
            content.contains("404") || content.contains("not found", ignoreCase = true) ||
                content.contains("error", ignoreCase = true),
            "Unknown route should return a 404 error page",
        )
    }

    @Test
    fun `static assets are served`() {
        val response = page.navigate("$baseUrl/static/site.css")
        assertTrue(
            response != null && response.ok(),
            "Static CSS asset should be served with a 200 response",
        )
    }
}
