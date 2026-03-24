package io.github.rygel.outerstellar.platform.security

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthRealmTest {

    private val testUser =
        User(
            id = UUID.randomUUID(),
            username = "realmuser",
            email = "realm@example.com",
            passwordHash = "hash",
            role = UserRole.USER,
        )

    // ---- AuthResult chain resolution ----

    @Test
    fun `first authenticated result wins`() {
        val realm1 = StubRealm("first", AuthResult.Skipped)
        val realm2 = StubRealm("second", AuthResult.Authenticated(testUser))
        val realm3 = StubRealm("third", AuthResult.Authenticated(testUser.copy(username = "other")))

        val result = resolveChain(listOf(realm1, realm2, realm3), "token")
        assertIs<AuthResult.Authenticated>(result)
        assertEquals("realmuser", result.user.username)
    }

    @Test
    fun `all skipped returns skipped`() {
        val realm1 = StubRealm("first", AuthResult.Skipped)
        val realm2 = StubRealm("second", AuthResult.Skipped)

        val result = resolveChain(listOf(realm1, realm2), "token")
        assertIs<AuthResult.Skipped>(result)
    }

    @Test
    fun `expired stops the chain`() {
        val realm1 = StubRealm("first", AuthResult.Expired)
        val realm2 = StubRealm("second", AuthResult.Authenticated(testUser))

        val result = resolveChain(listOf(realm1, realm2), "token")
        assertIs<AuthResult.Expired>(result)
    }

    @Test
    fun `empty realm list returns skipped`() {
        val result = resolveChain(emptyList(), "token")
        assertIs<AuthResult.Skipped>(result)
    }

    @Test
    fun `single realm authenticated returns authenticated`() {
        val realm = StubRealm("only", AuthResult.Authenticated(testUser))

        val result = resolveChain(listOf(realm), "token")
        assertIs<AuthResult.Authenticated>(result)
        assertEquals(testUser.id, result.user.id)
    }

    // ---- helper ----

    /** Mimics the resolution logic from App.kt bearer filter. */
    private fun resolveChain(realms: List<AuthRealm>, token: String): AuthResult {
        for (realm in realms) {
            val result = realm.authenticate(token)
            if (result !is AuthResult.Skipped) return result
        }
        return AuthResult.Skipped
    }

    private class StubRealm(override val name: String, private val result: AuthResult) : AuthRealm {
        override fun authenticate(token: String): AuthResult = result
    }
}
