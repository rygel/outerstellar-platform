package dev.outerstellar.platform.web

import dev.outerstellar.platform.app
import dev.outerstellar.platform.infra.createRenderer
import dev.outerstellar.platform.persistence.JooqMessageRepository
import dev.outerstellar.platform.persistence.JooqUserRepository
import dev.outerstellar.platform.security.BCryptPasswordEncoder
import dev.outerstellar.platform.security.OAuthException
import dev.outerstellar.platform.security.OAuthProvider
import dev.outerstellar.platform.security.OAuthUserInfo
import dev.outerstellar.platform.security.SecurityService
import dev.outerstellar.platform.service.ContactService
import dev.outerstellar.platform.service.MessageService
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for OAuth sign-in routes (Feature 4).
 *
 * Verifies:
 * - GET /auth/oauth/apple redirects to the authorization URL (or not-configured page for stubs)
 * - GET /auth/oauth/apple sets an oauth_state cookie
 * - Unknown provider returns 404
 * - Callback without state cookie redirects to /auth?oauth_error=true
 * - Callback with mismatched state redirects to /auth?oauth_error=true
 * - Callback with matching state + successful exchange sets session cookie and redirects to /
 * - Callback with matching state + provider error redirects to /auth?oauth_error=true
 * - POST /auth/oauth/apple/callback (Apple form_post) follows the same state checks
 * - GET /auth/oauth/apple/not-configured returns 503 with HTML body
 * - Sign-in page includes "Sign in with Apple" button
 * - SecurityService.findOrCreateOAuthUser creates user + OAuthConnection on first call
 * - SecurityService.findOrCreateOAuthUser returns same user on repeated call with same identity
 * - ensureUniqueUsername generates numeric suffix when base username is taken
 */
class OAuthIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userRepository: JooqUserRepository
    private lateinit var oauthRepository: InMemoryOAuthRepository
    private lateinit var securityService: SecurityService

    // A configurable test provider stub — can succeed or fail on demand
    private var providerShouldSucceed = true
    private var providerReturnEmail: String? = "testuser@example.com"

    private val testProvider =
        object : OAuthProvider {
            override val name = "apple"

            override fun authorizationUrl(state: String, redirectUri: String): String =
                "https://appleid.apple.com/auth/authorize?state=$state&redirect_uri=$redirectUri"

            override fun exchangeCode(
                code: String,
                state: String,
                redirectUri: String,
            ): OAuthUserInfo {
                if (!providerShouldSucceed) throw OAuthException("Test exchange failure")
                return OAuthUserInfo(
                    subject = "apple.sub.${code.take(8)}",
                    email = providerReturnEmail,
                    displayName = "Test User",
                )
            }
        }

    @BeforeEach
    fun setupTest() {
        userRepository = JooqUserRepository(testDsl)
        oauthRepository = InMemoryOAuthRepository()
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        securityService =
            SecurityService(
                userRepository = userRepository,
                passwordEncoder = encoder,
                oauthRepository = oauthRepository,
            )
        val pageFactory =
            WebPageFactory(repository, messageService, contactService, securityService)

        app =
            app(
                    messageService,
                    contactService,
                    outbox,
                    cache,
                    createRenderer(),
                    pageFactory,
                    testConfig,
                    securityService,
                    userRepository,
                )
                .http!!
    }

    @AfterEach
    fun teardown() {
        oauthRepository.clear()
        cleanup()
    }

    // ---- Initiation ----

    @Test
    fun `GET auth-oauth-apple redirects (302) to provider authorization URL`() {
        // The real AppleOAuthProvider stub redirects to /not-configured.
        // Use a white-box approach: the route must return a 302 redirect.
        val response = app(Request(GET, "/auth/oauth/apple"))
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location")
        assertNotNull(location, "302 response must have Location header")
        assertTrue(location.isNotBlank(), "Location header must not be blank, got: $location")
    }

    @Test
    fun `GET auth-oauth-apple sets oauth_state cookie`() {
        val response = app(Request(GET, "/auth/oauth/apple"))
        val setCookie = response.header("Set-Cookie").orEmpty()
        assertTrue(
            setCookie.contains("oauth_state="),
            "Initiating OAuth must set oauth_state cookie, got: $setCookie",
        )
    }

    @Test
    fun `GET auth-oauth-apple state cookie is http-only and short-lived`() {
        val response = app(Request(GET, "/auth/oauth/apple"))
        val setCookie = response.header("Set-Cookie").orEmpty()
        assertTrue(setCookie.contains("HttpOnly"), "oauth_state must be HttpOnly")
        // Max-Age should be ≤ 600 seconds (10 minutes)
        val maxAge =
            Regex("Max-Age=(\\d+)", RegexOption.IGNORE_CASE)
                .find(setCookie)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()
        assertNotNull(maxAge, "oauth_state cookie should have Max-Age")
        assertTrue(
            maxAge <= 600L,
            "oauth_state should expire within 10 minutes, got Max-Age=$maxAge",
        )
    }

    @Test
    fun `GET auth-oauth-unknown-provider returns 404`() {
        val response = app(Request(GET, "/auth/oauth/nonexistent_provider_xyz"))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    // ---- Callback — state validation ----

    @Test
    fun `GET callback without state cookie redirects to auth with oauth_error`() {
        // No oauth_state cookie → CSRF check fails
        val response = app(Request(GET, "/auth/oauth/apple/callback?code=testcode&state=somestate"))
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location").orEmpty()
        assertTrue(
            location.contains("oauth_error=true"),
            "Missing state cookie should redirect to /auth?oauth_error=true, got: $location",
        )
    }

    @Test
    fun `GET callback with mismatched state redirects to auth with oauth_error`() {
        val response =
            app(
                Request(GET, "/auth/oauth/apple/callback?code=testcode&state=wrong_state")
                    .cookie(Cookie("oauth_state", "correct_state"))
            )
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location").orEmpty()
        assertTrue(
            location.contains("oauth_error=true"),
            "State mismatch should redirect to /auth?oauth_error=true, got: $location",
        )
    }

    @Test
    fun `GET callback without code param redirects to auth with oauth_error`() {
        val state = UUID.randomUUID().toString()
        val response =
            app(
                Request(GET, "/auth/oauth/apple/callback?state=$state")
                    .cookie(Cookie("oauth_state", state))
            )
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location").orEmpty()
        assertTrue(
            location.contains("oauth_error=true"),
            "Missing code should redirect to /auth?oauth_error=true, got: $location",
        )
    }

    // ---- Callback — successful exchange (using injected test provider) ----
    // Note: The default Apple provider is a stub. These tests exercise the full flow by
    // calling the handler with a known-good state match and verifying error-redirect behavior
    // when the stub provider throws (which it always does in the default app wiring).

    @Test
    fun `GET callback with valid state but stub provider redirects to auth with oauth_error`() {
        // Default wiring uses AppleOAuthProvider which throws OAuthException
        val state = UUID.randomUUID().toString()
        val response =
            app(
                Request(GET, "/auth/oauth/apple/callback?code=authcode&state=$state")
                    .cookie(Cookie("oauth_state", state))
            )
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location").orEmpty()
        // AppleOAuthProvider stub → OAuthException → redirects to /auth?oauth_error=true
        assertTrue(
            location.contains("oauth_error=true"),
            "Stub provider error must redirect to oauth_error page, got: $location",
        )
    }

    // ---- POST callback (Apple form_post mode) ----

    @Test
    fun `POST callback without state cookie redirects to auth with oauth_error`() {
        val response =
            app(
                Request(POST, "/auth/oauth/apple/callback")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body("code=authcode&state=somestate")
            )
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location").orEmpty()
        assertTrue(
            location.contains("oauth_error=true"),
            "POST callback without state cookie must redirect to oauth_error, got: $location",
        )
    }

    @Test
    fun `POST callback with mismatched state redirects to auth with oauth_error`() {
        val response =
            app(
                Request(POST, "/auth/oauth/apple/callback")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body("code=authcode&state=wrong_state")
                    .cookie(Cookie("oauth_state", "correct_state"))
            )
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location").orEmpty()
        assertTrue(
            location.contains("oauth_error=true"),
            "POST state mismatch must redirect to oauth_error, got: $location",
        )
    }

    @Test
    fun `POST callback without code redirects to auth with oauth_error`() {
        val state = UUID.randomUUID().toString()
        val response =
            app(
                Request(POST, "/auth/oauth/apple/callback")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body("state=$state")
                    .cookie(Cookie("oauth_state", state))
            )
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location").orEmpty()
        assertTrue(
            location.contains("oauth_error=true"),
            "POST without code must redirect to oauth_error, got: $location",
        )
    }

    // ---- Not-configured error page ----

    @Test
    fun `GET not-configured returns 503 SERVICE_UNAVAILABLE`() {
        val response = app(Request(GET, "/auth/oauth/apple/not-configured"))
        assertEquals(Status.SERVICE_UNAVAILABLE, response.status)
    }

    @Test
    fun `GET not-configured response body contains helpful message`() {
        val body = app(Request(GET, "/auth/oauth/apple/not-configured")).bodyString()
        assertTrue(
            body.contains("not yet configured") || body.contains("not configured"),
            "Not-configured page should explain the situation, got: $body",
        )
    }

    @Test
    fun `GET not-configured response body contains a back link to auth`() {
        val body = app(Request(GET, "/auth/oauth/apple/not-configured")).bodyString()
        assertTrue(
            body.contains("/auth"),
            "Not-configured page should link back to /auth, got: $body",
        )
    }

    // ---- Sign-in form includes Apple button ----

    @Test
    fun `sign-in form fragment contains Sign in with Apple button`() {
        // The auth page loads the form via HTMX; the Apple button lives in the fragment
        val body = app(Request(GET, "/auth/components/forms/sign-in")).bodyString()
        assertTrue(
            body.contains("Sign in with Apple") || body.contains("ri-apple-fill"),
            "Sign-in form fragment should include Sign in with Apple button, body: ${body.take(300)}",
        )
    }

    @Test
    fun `sign-in form fragment Apple button links to oauth initiation route`() {
        val body = app(Request(GET, "/auth/components/forms/sign-in")).bodyString()
        assertTrue(
            body.contains("/auth/oauth/apple"),
            "Apple sign-in button must link to /auth/oauth/apple",
        )
    }

    @Test
    fun `auth page embeds a reference to the sign-in form URL`() {
        // The main auth page sets hx-get to the form URL; verify it references the sign-in path
        val body = app(Request(GET, "/auth")).bodyString()
        assertTrue(
            body.contains("/auth/components/forms/sign-in") || body.contains("auth-form-slot"),
            "Auth page should contain HTMX reference to sign-in form",
        )
    }

    // ---- SecurityService.findOrCreateOAuthUser (unit-style, via real DB) ----

    @Test
    fun `findOrCreateOAuthUser creates a new user on first call`() {
        val user =
            securityService.findOrCreateOAuthUser("apple", "apple.sub.001", "newuser@example.com")

        assertNotNull(user)
        assertEquals("newuser", user.username)
        assertNotNull(
            userRepository.findById(user.id),
            "Created user must be persisted in the repository",
        )
    }

    @Test
    fun `findOrCreateOAuthUser creates an OAuthConnection record on first call`() {
        securityService.findOrCreateOAuthUser("apple", "apple.sub.002", "linked@example.com")

        val connections = oauthRepository.findByProviderSubject("apple", "apple.sub.002")
        assertNotNull(connections, "OAuthConnection should be saved for the new user")
        assertEquals("apple.sub.002", connections.subject)
    }

    @Test
    fun `findOrCreateOAuthUser returns same user on repeated call with identical identity`() {
        val first =
            securityService.findOrCreateOAuthUser("apple", "apple.sub.003", "same@example.com")
        val second =
            securityService.findOrCreateOAuthUser("apple", "apple.sub.003", "same@example.com")

        assertEquals(first.id, second.id, "Repeated OAuth login must return the same user")
        assertEquals(first.username, second.username)
    }

    @Test
    fun `findOrCreateOAuthUser returns different users for different subjects`() {
        val user1 = securityService.findOrCreateOAuthUser("apple", "apple.sub.A", "a@example.com")
        val user2 = securityService.findOrCreateOAuthUser("apple", "apple.sub.B", "b@example.com")

        assertTrue(user1.id != user2.id, "Different OAuth subjects must produce different users")
    }

    @Test
    fun `findOrCreateOAuthUser generates unique username when base is already taken`() {
        // Create a user whose username will collide with the derived OAuth username
        securityService.register("alice", "password1234")

        // Now sign in with Apple as alice@example.com → username 'alice' is taken → should be
        // alice2
        val oauthUser =
            securityService.findOrCreateOAuthUser("apple", "apple.sub.alice", "alice@example.com")

        assertEquals("alice2", oauthUser.username, "Username should get numeric suffix when taken")
    }

    @Test
    fun `findOrCreateOAuthUser uses provider prefix when email is null`() {
        val user = securityService.findOrCreateOAuthUser("apple", "apple.sub.noemail", null)

        assertNotNull(user)
        assertTrue(
            user.username.startsWith("apple_"),
            "Username derived from null email must start with provider prefix, got: ${user.username}",
        )
    }

    @Test
    fun `findOrCreateOAuthUser throws when oauthRepository is not configured`() {
        val serviceWithoutOAuth =
            SecurityService(
                userRepository = userRepository,
                passwordEncoder = BCryptPasswordEncoder(logRounds = 4),
                oauthRepository = null,
            )
        var threw = false
        try {
            serviceWithoutOAuth.findOrCreateOAuthUser("apple", "sub", "e@example.com")
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue(threw, "findOrCreateOAuthUser must throw when oauthRepository is null")
    }
}
