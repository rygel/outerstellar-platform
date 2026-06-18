package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.PasswordResetToken
import java.util.UUID

interface PasswordResetRepository {
    fun save(token: PasswordResetToken)

    fun findByToken(token: String): PasswordResetToken?

    fun markUsed(token: String)

    /**
     * Atomically claim a single-use reset token: marks it used iff it is currently unused and unexpired, returning the
     * owning user id. Returns null if the token does not exist, is already used, or has expired. The atomic UPDATE ...
     * WHERE used = false guard makes concurrent claims with the same token race-safe (only one wins) — fixing the
     * TOCTOU where findByToken + Java check + markUsed in separate transactions both succeeded.
     */
    fun claimToken(token: String): UUID?

    /** Mark all unused reset tokens for [userId] as used (invalidates prior tokens when a new one is issued). */
    fun invalidateUnusedForUser(userId: UUID)
}
