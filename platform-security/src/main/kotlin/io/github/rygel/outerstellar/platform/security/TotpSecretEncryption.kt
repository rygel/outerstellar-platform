package io.github.rygel.outerstellar.platform.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Encrypts TOTP seeds for database storage using a deployment-specific key derived from TOKEN_PEPPER. */
class TotpSecretEncryption(pepper: String) {
    private val secureRandom = SecureRandom()
    private val key: SecretKeySpec

    init {
        val pepperBytes = pepper.toByteArray(Charsets.UTF_8)
        require(pepperBytes.size >= MIN_PEPPER_BYTES) {
            "TOKEN_PEPPER must contain at least $MIN_PEPPER_BYTES UTF-8 bytes"
        }
        val derivation = Mac.getInstance("HmacSHA256")
        derivation.init(SecretKeySpec(pepperBytes, "HmacSHA256"))
        key = SecretKeySpec(derivation.doFinal(KEY_CONTEXT.toByteArray(Charsets.UTF_8)), "AES")
    }

    fun encrypt(secret: String): String {
        require(secret.isNotBlank()) { "TOTP secret must not be blank" }
        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        return STORAGE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce + encrypted)
    }

    fun decrypt(storedSecret: String): String {
        require(isEncrypted(storedSecret)) { "TOTP secret is not in the encrypted storage format" }
        val payload = Base64.getUrlDecoder().decode(storedSecret.removePrefix(STORAGE_PREFIX))
        require(payload.size > NONCE_BYTES) { "Encrypted TOTP secret payload is invalid" }
        val nonce = payload.copyOfRange(0, NONCE_BYTES)
        val encrypted = payload.copyOfRange(NONCE_BYTES, payload.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    fun isEncrypted(storedSecret: String): Boolean = storedSecret.startsWith(STORAGE_PREFIX)

    companion object {
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_CONTEXT = "outerstellar:totp-secret:v1"
        private const val MIN_PEPPER_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        const val STORAGE_PREFIX = "enc:v1:"
    }
}
