package io.github.rygel.outerstellar.platform.security

import org.mindrot.jbcrypt.BCrypt

interface PasswordEncoder {
    fun encode(password: String): String

    fun matches(password: String, hash: String): Boolean
}

class BCryptPasswordEncoder(private val logRounds: Int = 12) : PasswordEncoder {
    override fun encode(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(logRounds))

    override fun matches(password: String, hash: String): Boolean =
        try {
            BCrypt.checkpw(password, hash)
        } catch (_: IllegalArgumentException) {
            false
        }
}
