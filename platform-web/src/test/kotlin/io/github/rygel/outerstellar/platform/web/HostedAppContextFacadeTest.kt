package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.persistence.Notification
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.JwtService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.template.TemplateRenderer
import org.junit.jupiter.api.Test

class HostedAppContextFacadeTest {

    @Test
    fun `forTesting projects safe app info and rendering facade`() {
        val renderer: TemplateRenderer = { "rendered" }
        val context =
            HostedAppContext.forTesting(
                renderer = renderer,
                apiKeyService = mockk(relaxed = true),
                oauthService = mockk(relaxed = true),
                userRepository = mockk(relaxed = true),
                config =
                    AppConfig(
                        version = "1.2.3",
                        appBaseUrl = "https://platform.example",
                        devMode = true,
                        registrationEnabled = false,
                    ),
            )

        assertEquals("1.2.3", context.app.version)
        assertEquals("https://platform.example", context.app.appBaseUrl)
        assertEquals(true, context.app.devMode)
        assertEquals(false, context.app.registrationEnabled)
        assertSame(renderer, context.rendering.renderer)
        assertSame(renderer, context.renderer)
        assertEquals(context.app, context.config)
    }

    @Test
    fun `users facade resolves current user from request context and delegates lookups`() {
        val userId = UUID.randomUUID()
        val user = User(userId, "alex", "alex@example.com", "hash", UserRole.USER)
        val userRepository = mockk<UserRepository>()
        val jwtService = mockk<JwtService>()
        val context =
            HostedAppContext.forTesting(
                renderer = mockk(relaxed = true),
                apiKeyService = mockk(relaxed = true),
                oauthService = mockk(relaxed = true),
                userRepository = userRepository,
            )

        every { jwtService.extractClaims("jwt-token") } returns (userId to false)
        every { userRepository.findById(userId) } returns user
        every { userRepository.findByUsername("alex") } returns user
        every { userRepository.findByEmail("alex@example.com") } returns user

        val baseRequest = Request(GET, "/plugin/reports").cookie(Cookie(RequestContext.JWT_COOKIE, "jwt-token"))
        val requestWithContext =
            RequestContext.KEY(
                RequestContext(request = baseRequest, userRepository = userRepository, jwtService = jwtService),
                baseRequest,
            )

        assertSame(user, context.currentUser(requestWithContext))
        assertSame(user, context.users.currentUser(requestWithContext))
        assertSame(user, context.users.findById(userId))
        assertSame(user, context.users.findByUsername("alex"))
        assertSame(user, context.users.findByEmail("alex@example.com"))
        assertSame(user, context.userRepository.findById(userId))
    }

    @Test
    fun `security and notification facades delegate to narrowed host services`() {
        val userId = UUID.randomUUID()
        val notification = Notification(userId = userId, title = "Build ready", body = "Plugin build completed")
        val apiKey = ApiKeySummary(7L, "osk_1234", "CLI", true, "2026-05-28T10:00:00Z", null)
        val createdKey = CreateApiKeyResponse("osk_secret", "CLI", "osk_1234")
        val apiKeyService = mockk<ApiKeyService>()
        val oauthService = mockk<OAuthService>()
        val notificationService = mockk<NotificationService>()
        val user = User(userId, "alex", "alex@example.com", "hash", UserRole.USER)
        val context =
            HostedAppContext.forTesting(
                renderer = mockk(relaxed = true),
                apiKeyService = apiKeyService,
                oauthService = oauthService,
                userRepository = mockk(relaxed = true),
                notificationService = notificationService,
            )

        every { apiKeyService.createApiKey(userId, "CLI") } returns createdKey
        every { apiKeyService.listApiKeys(userId) } returns listOf(apiKey)
        every { apiKeyService.deleteApiKey(userId, 7L) } returns Unit
        every { oauthService.findOrCreateOAuthUser("github", "sub-1", "alex@example.com") } returns user
        every { notificationService.listForUser(userId, 10) } returns listOf(notification)
        every { notificationService.countUnread(userId) } returns 1
        every { notificationService.markAllRead(userId) } returns Unit

        assertEquals(createdKey, context.security.apiKeys.createApiKey(userId, "CLI"))
        assertEquals(listOf(apiKey), context.apiKeyService.listApiKeys(userId))
        context.security.apiKeys.deleteApiKey(userId, 7L)
        assertEquals(user, context.security.oauth.findOrCreateOAuthUser("github", "sub-1", "alex@example.com"))
        assertEquals(listOf(notification), context.notifications!!.listForUser(userId, 10))
        assertEquals(1, context.notificationService!!.countUnread(userId))
        context.notifications!!.markAllRead(userId)

        verify { apiKeyService.createApiKey(userId, "CLI") }
        verify { apiKeyService.listApiKeys(userId) }
        verify { apiKeyService.deleteApiKey(userId, 7L) }
        verify { oauthService.findOrCreateOAuthUser("github", "sub-1", "alex@example.com") }
        verify { notificationService.listForUser(userId, 10) }
        verify { notificationService.countUnread(userId) }
        verify { notificationService.markAllRead(userId) }
    }
}
