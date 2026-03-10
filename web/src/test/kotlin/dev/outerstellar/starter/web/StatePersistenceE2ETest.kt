package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.service.MessageService
import io.mockk.every
import io.mockk.mockk
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StatePersistenceE2ETest {

    private lateinit var appHandler: org.http4k.core.HttpHandler
    private val repository = mockk<MessageRepository>()

    @BeforeEach
    fun setup() {
        val outbox = mockk<OutboxRepository>()
        val cache = mockk<MessageCache>()
        val transactionManager = object : TransactionManager {
            override fun <T> inTransaction(block: () -> T): T = block()
        }

        every { repository.listMessages() } returns emptyList()
        every { repository.listDirtyMessages() } returns emptyList()
        every { repository.listMessages(any(), any(), any(), any()) } returns emptyList()

        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository, true)
        val i18n = I18nService.fromResourceBundle("web-messages")
        val config = AppConfig(port = 0, jdbcUrl = "jdbc:h2:mem:test", devDashboardEnabled = true)

        appHandler = app(messageService, repository, outbox, cache, createRenderer(), pageFactory, config, i18n)
    }

    @Test
    fun `query parameters set preferences in cookies`() {
        // Request a page with explicit theme and language
        val request = Request(GET, "/?theme=monokai-pro&lang=fr&layout=cozy")
        val response = appHandler(request)

        assertEquals(Status.OK, response.status)

        // Verify cookies are set in the response
        val cookies = response.cookies()
        val themeCookie = cookies.find { it.name == WebContext.THEME_COOKIE }
        val langCookie = cookies.find { it.name == WebContext.LANG_COOKIE }
        val layoutCookie = cookies.find { it.name == WebContext.LAYOUT_COOKIE }

        assertEquals("monokai-pro", themeCookie?.value)
        assertEquals("fr", langCookie?.value)
        assertEquals("cozy", layoutCookie?.value)
    }

    @Test
    fun `preferences persist from cookies in subsequent requests`() {
        // First, set the cookies by mimicking a browser that already has them
        val requestWithCookies = Request(GET, "/")
            .cookie(Cookie(WebContext.THEME_COOKIE, "dracula"))
            .cookie(Cookie(WebContext.LANG_COOKIE, "fr"))

        val response = appHandler(requestWithCookies)

        val body = response.bodyString()
        
        // 1. Verify Dracula theme CSS variables are in the body
        // Dracula background is #282A36 (uppercase in JSON)
        assertTrue(body.contains("--color-background: #282A36"), "Should use Dracula theme from cookie")

        // 2. Verify French language is used
        // French Home Nav is "Accueil"
        assertTrue(body.contains("Accueil"), "Should use French language from cookie")
    }

    @Test
    fun `navigation links are clean and do not contain state query parameters`() {
        // Even if we provide state via cookies, the links generated in the sidebar should be clean
        val request = Request(GET, "/auth")
            .cookie(Cookie(WebContext.THEME_COOKIE, "nord"))
        
        val response = appHandler(request)
        val body = response.bodyString()

        // Verify the Home link in navigation is just "/" and not "/?theme=nord..."
        assertTrue(body.contains("href=\"/\""), "Navigation links should be clean URLs")
        assertTrue(body.contains("href=\"/auth\""), "Auth link should be clean")
    }

    @Test
    fun `error pages respect stored preferences`() {
        // Access a non-existent page with a stored theme cookie
        val request = Request(GET, "/some-garbage-page")
            .cookie(Cookie(WebContext.THEME_COOKIE, "monokai-pro"))
            .cookie(Cookie(WebContext.LANG_COOKIE, "fr"))

        val response = appHandler(request)

        assertEquals(Status.NOT_FOUND, response.status)
        val body = response.bodyString()

        // 1. Verify theme persistence on error page
        // Monokai Pro background is #2D2A2E (uppercase in JSON)
        assertTrue(body.contains("--color-background: #2D2A2E"), "Error page should respect theme cookie")

        // 2. Verify language persistence on error page
        // French 44 Heading uses "La page est introuvable"
        assertTrue(body.contains("La page est introuvable"), "Error page should respect language cookie (French)")
    }
}
