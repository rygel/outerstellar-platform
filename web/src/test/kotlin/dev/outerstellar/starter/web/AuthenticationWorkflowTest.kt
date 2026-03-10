package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRole
import dev.outerstellar.starter.service.MessageService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies

class AuthenticationWorkflowTest : PostgresWebTest() {

    @Test
    fun `user registration and login with redirect workflow`() {
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl, testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val messageService = MessageService(repository, outbox, null, cache)
        val pageFactory = WebPageFactory(repository)
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService = SecurityService(userRepository, encoder)
        val i18n = I18nService.fromResourceBundle("messages")

        val app = app(
            messageService, repository, outbox, cache, createRenderer(), 
            pageFactory, testConfig, securityService, userRepository, encoder
        ).http!!

        // 1. Try to access admin dashboard -> should redirect to auth with returnTo
        val initialResponse = app(Request(GET, "/admin/dev"))
        assertEquals(Status.FOUND, initialResponse.status)
        assertTrue(initialResponse.header("location")!!.contains("/auth?returnTo=/admin/dev"))

        // 2. Register a new admin user
        val regResponse = app(Request(POST, "/auth/components/result")
            .form("mode", "register")
            .form("name", "superadmin")
            .form("email", "admin@test.com")
            .form("password", "password123")
            .form("confirmPassword", "password123")
        )
        assertEquals(Status.OK, regResponse.status)
        assertTrue(regResponse.bodyString().contains("Registration successful"))

        // Update user to ADMIN role manually for test
        val user = userRepository.findByUsername("superadmin")!!
        userRepository.save(user.copy(role = UserRole.ADMIN))

        // 3. Login with returnTo
        val loginResponse = app(Request(POST, "/auth/components/result")
            .query("returnTo", "/admin/dev")
            .form("mode", "sign-in")
            .form("email", "superadmin") // username
            .form("password", "password123")
        )

        assertEquals(Status.OK, loginResponse.status)
        // Check HTMX redirect header
        assertEquals("/admin/dev", loginResponse.header("HX-Redirect"))
        
        val sessionCookie = loginResponse.cookies().find { it.name == "app_session" }
        assertNotNull(sessionCookie)

        // 4. Access admin dashboard with session
        val adminResponse = app(Request(GET, "/admin/dev").cookie(sessionCookie!!))
        assertEquals(Status.OK, adminResponse.status)
        assertTrue(adminResponse.bodyString().contains("Developer Dashboard"))
    }

    @Test
    fun `standard user cannot access admin dashboard`() {
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl, testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val messageService = MessageService(repository, outbox, null, cache)
        val pageFactory = WebPageFactory(repository)
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService = SecurityService(userRepository, encoder)
        val i18n = I18nService.fromResourceBundle("messages")

        val app = app(
            messageService, repository, outbox, cache, createRenderer(), 
            pageFactory, testConfig, securityService, userRepository, encoder
        ).http!!

        // Register standard user
        app(Request(POST, "/auth/components/result")
            .form("mode", "register")
            .form("name", "regular")
            .form("email", "user@test.com")
            .form("password", "password123")
            .form("confirmPassword", "password123")
        )
        
        val loginResponse = app(Request(POST, "/auth/components/result")
            .form("mode", "sign-in")
            .form("email", "regular")
            .form("password", "password123")
        )
        val sessionCookie = loginResponse.cookies().find { it.name == "app_session" }!!

        // Try to access admin dashboard -> should be Forbidden (403)
        val adminResponse = app(Request(GET, "/admin/dev").cookie(sessionCookie))
        assertEquals(Status.FORBIDDEN, adminResponse.status)
    }
}
