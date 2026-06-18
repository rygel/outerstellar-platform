package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.PasswordResetToken
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbiPasswordResetRepositoryTest : JdbiTest() {

    private val repo by lazy { JdbiPasswordResetRepository(jdbi) }

    private fun token(userId: UUID, rawToken: String = UUID.randomUUID().toString()) =
        PasswordResetToken(userId = userId, token = rawToken, expiresAt = Instant.now().plusSeconds(3600), used = false)

    @Test
    fun `save and findByToken round-trips`() {
        val userId = createUser()
        val t = token(userId, "reset-abc")
        repo.save(t)
        val found = repo.findByToken("reset-abc")!!
        assertEquals(userId, found.userId)
        assertEquals("reset-abc", found.token)
        assertFalse(found.used)
        assertNotNull(found.expiresAt)
    }

    @Test
    fun `findByToken returns null for unknown token`() {
        assertNull(repo.findByToken("nonexistent"))
    }

    @Test
    fun `markUsed sets used flag`() {
        val userId = createUser()
        val t = token(userId, "mark-used-token")
        repo.save(t)
        assertFalse(repo.findByToken("mark-used-token")!!.used)
        repo.markUsed("mark-used-token")
        assertTrue(repo.findByToken("mark-used-token")!!.used)
    }

    @Test
    fun `multiple tokens can exist for same user`() {
        val userId = createUser()
        repo.save(token(userId, "token-1"))
        repo.save(token(userId, "token-2"))
        assertNotNull(repo.findByToken("token-1"))
        assertNotNull(repo.findByToken("token-2"))
    }

    @Test
    fun `claimToken atomically marks a valid token used and returns the owner`() {
        val userId = createUser()
        repo.save(token(userId, "claimable-token"))
        val claimedUserId = repo.claimToken("claimable-token")
        assertEquals(userId, claimedUserId)
        assertTrue(repo.findByToken("claimable-token")!!.used)
    }

    @Test
    fun claimTokenReturnsNullForAlreadyClaimedToken() {
        val userId = createUser()
        repo.save(token(userId, "once-only"))
        assertNotNull(repo.claimToken("once-only"))
        // Second claim must fail — the atomic WHERE used = false guard prevents double-use.
        assertNull(repo.claimToken("once-only"))
    }

    @Test
    fun `claimToken returns null for an expired token`() {
        val userId = createUser()
        val expired = token(userId, "expired-claim").copy(expiresAt = Instant.now().minusSeconds(60))
        repo.save(expired)
        assertNull(repo.claimToken("expired-claim"))
    }

    @Test
    fun `invalidateUnusedForUser marks all prior unused tokens for the user as used`() {
        val userId = createUser()
        repo.save(token(userId, "old-1"))
        repo.save(token(userId, "old-2"))
        repo.save(token(userId, "old-3"))
        repo.invalidateUnusedForUser(userId)
        assertTrue(repo.findByToken("old-1")!!.used)
        assertTrue(repo.findByToken("old-2")!!.used)
        assertTrue(repo.findByToken("old-3")!!.used)
    }

    @Test
    fun `token column has only the unique-constraint index, not a redundant duplicate`() {
        // Regression guard for issue #530: the non-unique idx_plt_password_reset_tokens_token
        // duplicated the plt_password_reset_tokens_token_key UNIQUE constraint and was dropped.
        // If it ever returns (e.g. a future migration re-adding it), this fails.
        val indexes =
            jdbi.withHandle<List<String>, Exception> { handle ->
                handle
                    .createQuery(
                        "SELECT indexname FROM pg_indexes WHERE tablename = 'plt_password_reset_tokens' ORDER BY indexname"
                    )
                    .mapTo(String::class.java)
                    .list()
            }
        assertFalse(
            indexes.contains("idx_plt_password_reset_tokens_token"),
            "Redundant token index must not exist: $indexes",
        )
        assertTrue(
            indexes.contains("plt_password_reset_tokens_token_key"),
            "UNIQUE constraint index must remain: $indexes",
        )
        assertTrue(indexes.contains("idx_plt_password_reset_tokens_user_id"), "user_id index must remain: $indexes")
    }
}
