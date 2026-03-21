package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.LoginRequest
import io.github.rygel.outerstellar.platform.model.RegisterRequest
import io.github.rygel.outerstellar.platform.model.UpdateNotificationPrefsRequest
import io.github.rygel.outerstellar.platform.model.UpdateProfileRequest
import io.github.rygel.outerstellar.platform.model.UserProfileResponse
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.mockk.mockk
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileApiIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userRepository: JooqUserRepository

    private val registerLens = Body.auto<RegisterRequest>().toLens()
    private val loginLens = Body.auto<LoginRequest>().toLens()
    private val tokenLens = Body.auto<AuthTokenResponse>().toLens()
    private val updateProfileLens = Body.auto<UpdateProfileRequest>().toLens()
    private val updateNotifPrefsLens = Body.auto<UpdateNotificationPrefsRequest>().toLens()
    private val userProfileLens = Body.auto<UserProfileResponse>().toLens()

    @BeforeEach
    fun setupTest() {
        cleanup()
        userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService =
            io.github.rygel.outerstellar.platform.service.MessageService(
                repository,
                outbox,
                txManager,
                cache,
            )
        val pageFactory = WebPageFactory(repository, messageService, null, null)
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = JooqSessionRepository(testDsl),
            )
        val contactService =
            mockk<io.github.rygel.outerstellar.platform.service.ContactService>(relaxed = true)

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

    @AfterEach fun teardown() = cleanup()

    // ---- Helpers ----

    private fun registerAndLogin(
        username: String = "profuser${UUID.randomUUID().toString().take(6)}"
    ): String {
        app(
            Request(POST, "/api/v1/auth/register")
                .with(registerLens of RegisterRequest(username, "pass123!"))
        )
        val loginResp =
            app(
                Request(POST, "/api/v1/auth/login")
                    .with(loginLens of LoginRequest(username, "pass123!"))
            )
        return tokenLens(loginResp).token
    }

    private fun bearer(method: org.http4k.core.Method, path: String, token: String) =
        Request(method, path).header("Authorization", "Bearer $token")

    // ---- GET /api/v1/auth/profile ----

    @Test
    fun `GET profile returns current user data`() {
        val token = registerAndLogin("alice")
        val response = app(bearer(GET, "/api/v1/auth/profile", token))

        assertEquals(Status.OK, response.status)
        val profile = userProfileLens(response)
        assertEquals("alice", profile.username)
        assertTrue(profile.email.isNotBlank())
        assertTrue(profile.emailNotificationsEnabled)
        assertTrue(profile.pushNotificationsEnabled)
        assertNull(profile.avatarUrl)
    }

    @Test
    fun `GET profile requires authentication`() {
        val response = app(Request(GET, "/api/v1/auth/profile"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    // ---- PUT /api/v1/auth/profile ----

    @Test
    fun `PUT profile updates email`() {
        val token = registerAndLogin("bob")
        val user = userRepository.findByUsername("bob")!!

        val response =
            app(
                bearer(PUT, "/api/v1/auth/profile", token)
                    .with(updateProfileLens of UpdateProfileRequest(email = "bob_new@example.com"))
            )

        assertEquals(Status.OK, response.status)
        assertEquals("bob_new@example.com", userRepository.findById(user.id)?.email)
    }

    @Test
    fun `PUT profile updates username`() {
        val token = registerAndLogin("carol")
        val user = userRepository.findByUsername("carol")!!

        val response =
            app(
                bearer(PUT, "/api/v1/auth/profile", token)
                    .with(
                        updateProfileLens of
                            UpdateProfileRequest(email = user.email, username = "carol_v2")
                    )
            )

        assertEquals(Status.OK, response.status)
        assertNotNull(userRepository.findByUsername("carol_v2"))
        assertNull(userRepository.findByUsername("carol"))
    }

    @Test
    fun `PUT profile updates avatar URL`() {
        val token = registerAndLogin("dave")
        val user = userRepository.findByUsername("dave")!!

        val response =
            app(
                bearer(PUT, "/api/v1/auth/profile", token)
                    .with(
                        updateProfileLens of
                            UpdateProfileRequest(
                                email = user.email,
                                avatarUrl = "https://example.com/avatar.png",
                            )
                    )
            )

        assertEquals(Status.OK, response.status)
        assertEquals("https://example.com/avatar.png", userRepository.findById(user.id)?.avatarUrl)
    }

    @Test
    fun `PUT profile returns 409 when username is already taken`() {
        val token1 = registerAndLogin("eve")
        registerAndLogin("frank")
        val user = userRepository.findByUsername("eve")!!

        val response =
            app(
                bearer(PUT, "/api/v1/auth/profile", token1)
                    .with(
                        updateProfileLens of
                            UpdateProfileRequest(email = user.email, username = "frank")
                    )
            )

        assertEquals(Status.CONFLICT, response.status)
        // original username unchanged
        assertNotNull(userRepository.findByUsername("eve"))
    }

    @Test
    fun `PUT profile requires authentication`() {
        val response =
            app(
                Request(PUT, "/api/v1/auth/profile")
                    .with(updateProfileLens of UpdateProfileRequest(email = "x@example.com"))
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    // ---- PUT /api/v1/auth/notification-preferences ----

    @Test
    fun `PUT notification-preferences persists both flags`() {
        val token = registerAndLogin("grace")
        val user = userRepository.findByUsername("grace")!!

        val response =
            app(
                bearer(PUT, "/api/v1/auth/notification-preferences", token)
                    .with(
                        updateNotifPrefsLens of
                            UpdateNotificationPrefsRequest(
                                emailEnabled = false,
                                pushEnabled = false,
                            )
                    )
            )

        assertEquals(Status.OK, response.status)
        val saved = userRepository.findById(user.id)!!
        assertFalse(saved.emailNotificationsEnabled)
        assertFalse(saved.pushNotificationsEnabled)
    }

    @Test
    fun `PUT notification-preferences can enable selectively`() {
        val token = registerAndLogin("henry")
        val user = userRepository.findByUsername("henry")!!

        app(
            bearer(PUT, "/api/v1/auth/notification-preferences", token)
                .with(
                    updateNotifPrefsLens of
                        UpdateNotificationPrefsRequest(emailEnabled = false, pushEnabled = false)
                )
        )
        app(
            bearer(PUT, "/api/v1/auth/notification-preferences", token)
                .with(
                    updateNotifPrefsLens of
                        UpdateNotificationPrefsRequest(emailEnabled = true, pushEnabled = false)
                )
        )

        val saved = userRepository.findById(user.id)!!
        assertTrue(saved.emailNotificationsEnabled)
        assertFalse(saved.pushNotificationsEnabled)
    }

    @Test
    fun `PUT notification-preferences requires authentication`() {
        val response =
            app(
                Request(PUT, "/api/v1/auth/notification-preferences")
                    .with(
                        updateNotifPrefsLens of
                            UpdateNotificationPrefsRequest(
                                emailEnabled = false,
                                pushEnabled = false,
                            )
                    )
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    // ---- DELETE /api/v1/auth/account ----

    @Test
    fun `DELETE account removes the user`() {
        val token = registerAndLogin("ivan")
        val user = userRepository.findByUsername("ivan")!!

        val response = app(bearer(DELETE, "/api/v1/auth/account", token))

        assertEquals(Status.OK, response.status)
        assertNull(userRepository.findById(user.id))
    }

    @Test
    fun `DELETE account returns 403 when user is the only admin`() {
        val adminId = UUID.randomUUID()
        val password = "adminpass1"
        userRepository.save(
            User(
                id = adminId,
                username = "soleadmin",
                email = "soleadmin@test.com",
                passwordHash = BCryptPasswordEncoder(logRounds = 4).encode(password),
                role = UserRole.ADMIN,
            )
        )
        val loginResp =
            app(
                Request(POST, "/api/v1/auth/login")
                    .with(loginLens of LoginRequest("soleadmin", password))
            )
        val adminToken = tokenLens(loginResp).token

        val response = app(bearer(DELETE, "/api/v1/auth/account", adminToken))

        assertEquals(Status.FORBIDDEN, response.status)
        assertNotNull(userRepository.findById(adminId))
    }

    @Test
    fun `DELETE account allows admin deletion when another admin exists`() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        userRepository.save(
            User(
                id = id1,
                username = "admin1",
                email = "admin1@test.com",
                passwordHash = encoder.encode("adminpass1"),
                role = UserRole.ADMIN,
            )
        )
        userRepository.save(
            User(
                id = id2,
                username = "admin2",
                email = "admin2@test.com",
                passwordHash = encoder.encode("adminpass2"),
                role = UserRole.ADMIN,
            )
        )
        val loginResp =
            app(
                Request(POST, "/api/v1/auth/login")
                    .with(loginLens of LoginRequest("admin1", "adminpass1"))
            )
        val token1 = tokenLens(loginResp).token

        val response = app(bearer(DELETE, "/api/v1/auth/account", token1))

        assertEquals(Status.OK, response.status)
        assertNull(userRepository.findById(id1))
        assertNotNull(userRepository.findById(id2))
    }

    @Test
    fun `DELETE account requires authentication`() {
        val response = app(Request(DELETE, "/api/v1/auth/account"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    // ---- GET profile reflects changes from PUT ----

    @Test
    fun `GET profile after PUT reflects updated values`() {
        val token = registerAndLogin("karen")
        val user = userRepository.findByUsername("karen")!!

        app(
            bearer(PUT, "/api/v1/auth/profile", token)
                .with(
                    updateProfileLens of
                        UpdateProfileRequest(
                            email = "karen_new@example.com",
                            username = "karen_updated",
                            avatarUrl = "https://example.com/karen.png",
                        )
                )
        )
        app(
            bearer(PUT, "/api/v1/auth/notification-preferences", token)
                .with(
                    updateNotifPrefsLens of
                        UpdateNotificationPrefsRequest(emailEnabled = false, pushEnabled = true)
                )
        )

        val profile = userProfileLens(app(bearer(GET, "/api/v1/auth/profile", token)))
        assertEquals("karen_updated", profile.username)
        assertEquals("karen_new@example.com", profile.email)
        assertEquals("https://example.com/karen.png", profile.avatarUrl)
        assertFalse(profile.emailNotificationsEnabled)
        assertTrue(profile.pushNotificationsEnabled)
    }
}
