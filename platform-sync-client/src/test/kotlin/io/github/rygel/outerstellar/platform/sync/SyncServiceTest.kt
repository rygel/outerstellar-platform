package io.github.rygel.outerstellar.platform.sync

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.mockk.mockk
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncServiceTest {

    private val repository = mockk<MessageRepository>(relaxed = true)
    private val transactionManager = mockk<TransactionManager>(relaxed = true)

    private fun makeSvc(client: (Request) -> Response): SyncService =
        SyncService("http://localhost:8080", repository, transactionManager, client)

    private fun makeLoginSvc(token: String = "tok", role: String = "USER"): Pair<SyncService, Response> {
        val loginResp =
            Response(Status.OK).with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse(token, "alice", role))
        val svc = makeSvc { req -> if (req.uri.toString().contains("auth/login")) loginResp else Response(Status.OK) }
        return svc to loginResp
    }

    @Test
    fun `login stores token and role on success`() {
        val resp =
            Response(Status.OK)
                .with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok123", "alice", "USER"))
        val svc = makeSvc { resp }
        val result = svc.login("alice", "password123")
        assertEquals("tok123", result.token)
        assertEquals("USER", result.role)
    }

    @Test
    fun `login throws SyncException on failure`() {
        val svc = makeSvc { Response(Status.FORBIDDEN) }
        assertThrows<SyncException> { svc.login("alice", "bad") }
    }

    @Test
    fun `register stores token and role`() {
        val resp =
            Response(Status.OK)
                .with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok456", "bob", "USER"))
        val svc = makeSvc { resp }
        val result = svc.register("bob", "password123")
        assertEquals("tok456", result.token)
    }

    @Test
    fun `logout clears token and role`() {
        val authResp =
            Response(Status.OK)
                .with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok123", "alice", "USER"))
        val svc = makeSvc { authResp }
        svc.login("alice", "pass")
        svc.logout()
        assertNull(svc.userRole)
    }

    @Test
    fun `changePassword sends authenticated PUT request`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        var authHeader: String? = null
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                authHeader = req.header("Authorization")
                Response(Status.OK)
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        svc.changePassword("old", "new")
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `listUsers requires auth token`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        var authHeader: String? = null
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                authHeader = req.header("Authorization")
                Response(Status.OK)
                    .with(
                        Body.auto<List<io.github.rygel.outerstellar.platform.model.UserSummary>>().toLens() of
                            emptyList()
                    )
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        svc.listUsers()
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `setUserEnabled sends PUT with correct JSON body`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        var body: String? = null
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                body = req.bodyString()
                Response(Status.OK)
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        svc.setUserEnabled("user-uuid", true)
        assertEquals("{\"enabled\":true}", body)
    }

    @Test
    fun `setUserRole sends PUT with role JSON`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        var body: String? = null
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                body = req.bodyString()
                Response(Status.OK)
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        svc.setUserRole("user-uuid", "ADMIN")
        assertEquals("{\"role\":\"ADMIN\"}", body)
    }

    @Test
    fun `requestPasswordReset does not require auth`() {
        var called = false
        val svc = makeSvc {
            called = true
            Response(Status.OK)
        }
        svc.requestPasswordReset("alice@example.com")
        assert(called)
    }

    @Test
    fun `resetPassword does not require auth`() {
        var called = false
        val svc = makeSvc {
            called = true
            Response(Status.OK)
        }
        svc.resetPassword("token123", "newPassword123")
        assert(called)
    }

    @Test
    fun `listNotifications requires auth`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        var authHeader: String? = null
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                authHeader = req.header("Authorization")
                Response(Status.OK)
                    .with(
                        Body.auto<List<io.github.rygel.outerstellar.platform.model.NotificationSummary>>().toLens() of
                            emptyList()
                    )
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        svc.listNotifications()
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `markNotificationRead requires auth`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        var called = false
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                called = true
                Response(Status.OK)
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        svc.markNotificationRead("notif-1")
        assert(called)
    }

    @Test
    fun `markAllNotificationsRead requires auth`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        var called = false
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                called = true
                Response(Status.OK)
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        svc.markAllNotificationsRead()
        assert(called)
    }

    @Test
    fun `fetchProfile returns UserProfileResponse`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        val profile =
            io.github.rygel.outerstellar.platform.model.UserProfileResponse(
                "alice",
                "alice@example.com",
                null,
                true,
                true,
            )
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                Response(Status.OK)
                    .with(
                        Body.auto<io.github.rygel.outerstellar.platform.model.UserProfileResponse>().toLens() of profile
                    )
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        val result = svc.fetchProfile()
        assertEquals("alice", result.username)
    }

    @Test
    fun `updateProfile sends PUT with email and username`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        var body: String? = null
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                body = req.bodyString()
                Response(Status.OK)
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        svc.updateProfile("alice@example.com", "aliceNew", null)
        assert(body?.contains("\"email\":\"alice@example.com\"") == true)
    }

    @Test
    fun `updateNotificationPreferences sends correct JSON`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        var body: String? = null
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                body = req.bodyString()
                Response(Status.OK)
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        svc.updateNotificationPreferences(false, true)
        assertEquals("{\"emailEnabled\":false,\"pushEnabled\":true}", body)
    }

    @Test
    fun `deleteAccount sends DELETE with auth`() {
        val (loginSvc, loginResp) = makeLoginSvc()
        var method: Method? = null
        val svc = makeSvc { req ->
            if (req.uri.toString().contains("auth/login")) {
                loginResp
            } else {
                method = req.method
                Response(Status.OK)
            }
        }
        loginSvc.login("alice", "pass")
        svc.login("alice", "pass")
        svc.deleteAccount()
        assertEquals(Method.DELETE, method)
    }

    @Test
    fun `userRole is null before login`() {
        val svc = makeSvc { Response(Status.OK) }
        assertNull(svc.userRole)
    }

    @Test
    fun `userRole is set after login`() {
        val resp =
            Response(Status.OK).with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok", "u", "ADMIN"))
        val svc = makeSvc { resp }
        svc.login("u", "p")
        assertEquals("ADMIN", svc.userRole)
    }
}
