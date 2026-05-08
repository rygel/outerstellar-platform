package io.github.rygel.outerstellar.platform.security

import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory

interface PasswordEncoder {
    fun encode(password: String): String

    fun matches(password: String, hash: String): Boolean
}

class BCryptPasswordEncoder(private val logRounds: Int = 12) : PasswordEncoder {
    private val logger = LoggerFactory.getLogger(BCryptPasswordEncoder::class.java)

    override fun encode(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(logRounds))

    override fun matches(password: String, hash: String): Boolean =
        try {
            BCrypt.checkpw(password, hash)
        } catch (e: IllegalArgumentException) {
            logger.warn("Malformed BCrypt hash during verification: {}", e.message)
            false
        }
}
