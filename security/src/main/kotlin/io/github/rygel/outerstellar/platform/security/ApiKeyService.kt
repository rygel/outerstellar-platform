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
        val rawKey = "osk_" + generateRandomHex(API_KEY_HEX_LENGTH)
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
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val API_KEY_HEX_LENGTH = 32
        private const val API_KEY_PREFIX_LENGTH = 8
    }
}
