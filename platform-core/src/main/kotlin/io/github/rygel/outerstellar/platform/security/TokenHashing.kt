package io.github.rygel.outerstellar.platform.security

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Hashes opaque high-entropy tokens (session tokens, API keys, password-reset tokens) for at-rest storage and indexed
 * lookup using HMAC-SHA256 keyed by a deployment-specific [pepper].
 *
 * The pepper means a DB-read attacker cannot use the stored hashes directly (they'd also need the pepper, which lives
 * in config/env, not the DB), and the signing key can be rotated by changing the pepper. HMAC is the correct
 * construction for this — unlike the previous un-keyed SHA-256, the hash is not computable from the token alone.
 *
 * The hash is deterministic for a given pepper+token (so it remains a usable DB index), but not without the pepper.
 * Tokens are high-entropy, so brute-force of the token from a known pepper+hash is infeasible.
 *
 * @param pepper a deployment-specific secret key of at least 32 UTF-8 bytes.
 */
class TokenHashing(pepper: String) {
    init {
        require(pepper.toByteArray(Charsets.UTF_8).size >= MIN_PEPPER_BYTES) {
            "TOKEN_PEPPER must contain at least $MIN_PEPPER_BYTES UTF-8 bytes"
        }
    }

    private val key = SecretKeySpec(pepper.toByteArray(Charsets.UTF_8), "HmacSHA256")

    fun hash(token: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val raw = mac.doFinal(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(raw)
    }

    companion object {
        private const val MIN_PEPPER_BYTES = 32
    }
}
