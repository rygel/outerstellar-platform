package io.github.rygel.outerstellar.platform.persistence

import java.util.UUID

data class OAuthUserInfo(val subject: String, val email: String?, val displayName: String?)

data class OAuthConnection(
    val id: Long = 0,
    val userId: UUID,
    val provider: String,
    val subject: String,
    val email: String?,
)

interface OAuthRepository {
    fun findByProviderSubject(provider: String, subject: String): OAuthConnection?

    fun save(connection: OAuthConnection)

    fun findByUserId(userId: UUID): List<OAuthConnection>

    fun delete(id: Long, userId: UUID)
}
