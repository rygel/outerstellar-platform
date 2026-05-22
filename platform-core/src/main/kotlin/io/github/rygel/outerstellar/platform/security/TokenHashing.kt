package io.github.rygel.outerstellar.platform.security

import java.security.MessageDigest

object TokenHashing {
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
