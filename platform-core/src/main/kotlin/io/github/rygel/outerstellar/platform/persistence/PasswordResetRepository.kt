package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.PasswordResetToken

interface PasswordResetRepository {
    fun save(token: PasswordResetToken)

    fun findByToken(token: String): PasswordResetToken?

    fun markUsed(token: String)
}
