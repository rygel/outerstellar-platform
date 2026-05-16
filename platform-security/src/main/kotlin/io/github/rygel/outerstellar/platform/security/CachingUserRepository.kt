package io.github.rygel.outerstellar.platform.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.rygel.outerstellar.platform.model.UserRole
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A [UserRepository] decorator that caches [findById] results in a Caffeine in-process cache.
 *
 * [findById] is called on every authenticated request (to resolve the session cookie to a User). User records change
 * infrequently (role changes, password changes, profile edits), so a short-TTL cache eliminates this SELECT from the
 * hot path entirely on cache hits.
 *
 * All write operations invalidate the affected entry so stale data is never served beyond the TTL. [updateLastActivity]
 * is explicitly excluded from invalidation — the cached `lastActivityAt` value can be up to [ttlSeconds] old, which is
 * negligible relative to the session timeout window (default 30 minutes).
 */
class CachingUserRepository(private val delegate: UserRepository, maximumSize: Long = 1_000, ttlSeconds: Long = 60) :
    UserRepository by delegate {

    private val cache: Cache<UUID, User> =
        Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(ttlSeconds, TimeUnit.SECONDS).build()

    override fun findById(id: UUID): User? = cache.getIfPresent(id) ?: delegate.findById(id)?.also { cache.put(id, it) }

    override fun save(user: User) {
        cache.invalidate(user.id)
        delegate.save(user)
    }

    override fun updateRole(userId: UUID, role: UserRole) {
        cache.invalidate(userId)
        delegate.updateRole(userId, role)
    }

    override fun updateEnabled(userId: UUID, enabled: Boolean) {
        cache.invalidate(userId)
        delegate.updateEnabled(userId, enabled)
    }

    override fun deleteById(userId: UUID) {
        cache.invalidate(userId)
        delegate.deleteById(userId)
    }

    override fun updateUsername(userId: UUID, newUsername: String) {
        cache.invalidate(userId)
        delegate.updateUsername(userId, newUsername)
    }

    override fun updateAvatarUrl(userId: UUID, avatarUrl: String?) {
        cache.invalidate(userId)
        delegate.updateAvatarUrl(userId, avatarUrl)
    }

    override fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean) {
        cache.invalidate(userId)
        delegate.updateNotificationPreferences(userId, emailEnabled, pushEnabled)
    }

    override fun updatePreferences(userId: UUID, language: String?, theme: String?, layout: String?) {
        cache.invalidate(userId)
        delegate.updatePreferences(userId, language, theme, layout)
    }

    override fun updateLastActivity(userId: UUID) {
        cache.invalidate(userId)
        delegate.updateLastActivity(userId)
    }

    override fun findTotpSecretByUserId(userId: UUID): Triple<String?, Boolean, String?>? =
        delegate.findTotpSecretByUserId(userId)

    override fun updateTotpSecret(userId: UUID, secret: String?, backupCodes: String?) {
        delegate.updateTotpSecret(userId, secret, backupCodes)
        cache.invalidate(userId)
    }

    override fun enableTotp(userId: UUID) {
        delegate.enableTotp(userId)
        cache.invalidate(userId)
    }
}
