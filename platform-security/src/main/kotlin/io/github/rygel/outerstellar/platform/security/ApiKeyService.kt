package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.ApiKey
import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import java.util.UUID
import org.slf4j.LoggerFactory

class ApiKeyService(
    private val userRepository: UserRepository,
    private val apiKeyRepository: ApiKeyRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(ApiKeyService::class.java)
    private val secureRandom = java.security.SecureRandom()

    fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse {
        require(name.isNotBlank()) { "API key name is required" }
        val salt = generateRandomHex(TOKEN_SALT_HEX_LENGTH)
        val secret = generateRandomHex(API_KEY_SECRET_HEX_LENGTH)
        val rawKey = "osk_${salt}_$secret"
        val keyPrefix = rawKey.take(API_KEY_PREFIX_LENGTH)
        val keyHash = hashToken(rawKey)

        val apiKey = ApiKey(userId = userId, keyHash = keyHash, keyPrefix = keyPrefix, name = name)
        apiKeyRepository?.save(apiKey)
        logger.info("API key created for user {}", userId)
        return CreateApiKeyResponse(key = rawKey, name = name, keyPrefix = keyPrefix)
    }

    fun authenticateApiKey(rawKey: String): User? {
        val keyHash = hashToken(rawKey)
        val apiKey = apiKeyRepository?.findByKeyHash(keyHash) ?: return null
        if (!apiKey.enabled) return null

        val user = userRepository.findById(apiKey.userId)
        if (user != null && user.enabled) {
            apiKeyRepository.updateLastUsed(apiKey.id)
            return user
        }
        return null
    }

    fun listApiKeys(userId: UUID): List<ApiKeySummary> {
        return apiKeyRepository?.findByUserId(userId)?.map { key ->
            ApiKeySummary(
                id = key.id,
                keyPrefix = key.keyPrefix,
                name = key.name,
                enabled = key.enabled,
                createdAt = key.createdAt.toString(),
                lastUsedAt = key.lastUsedAt?.toString(),
            )
        } ?: emptyList()
    }

    fun deleteApiKey(userId: UUID, keyId: Long) {
        apiKeyRepository?.delete(keyId, userId)
        logger.info("API key {} deleted for user {}", keyId, userId)
    }

    private fun generateRandomHex(length: Int): String {
        val bytes = ByteArray(length / 2)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashToken(key: String): String {
        parseSaltedToken(key)?.let { (salt, secret) ->
            return sha256("$salt:$secret")
        }
        // Backward-compatible path for existing keys created before salting was introduced.
        return sha256(key)
    }

    private fun sha256(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun parseSaltedToken(token: String): Pair<String, String>? {
        if (!token.startsWith("osk_")) return null
        val payload = token.removePrefix("osk_")
        val separator = payload.indexOf('_')
        if (separator <= 0 || separator == payload.lastIndex) return null
        val salt = payload.substring(0, separator)
        val secret = payload.substring(separator + 1)
        if (!HEX_REGEX.matches(salt) || !HEX_REGEX.matches(secret)) return null
        if (salt.length != TOKEN_SALT_HEX_LENGTH || secret.isBlank()) return null
        return salt to secret
    }

    companion object {
        private const val TOKEN_SALT_HEX_LENGTH = 16
        private const val API_KEY_SECRET_HEX_LENGTH = 32
        private const val API_KEY_PREFIX_LENGTH = 8
        private val HEX_REGEX = Regex("^[0-9a-f]+$")
    }
}
