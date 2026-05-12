package io.github.rygel.outerstellar.platform.service

import java.net.URI

object UrlValidator {
    private const val MAX_URL_LENGTH = 2048
    private val PRIVATE_HOST_PATTERNS =
        listOf(
            Regex("^localhost$"),
            Regex("^127\\..*"),
            Regex("^10\\..*"),
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\..*"),
            Regex("^192\\.168\\..*"),
            Regex("^0\\..*"),
            Regex("^0\\.0\\.0\\.0$"),
            Regex("^\\[::1]$"),
            Regex("^\\[::]$"),
            Regex("^\\[fe80:.*]$"),
            Regex("^\\[fd00:.*]$"),
            Regex("^\\[fc00:.*]$"),
            Regex(".*\\.local$"),
            Regex(".*\\.internal$"),
        )

    fun validate(url: String) {
        val sanitized = url.trim()
        if (!sanitized.startsWith("https://") && !sanitized.startsWith("http://")) {
            throw IllegalArgumentException("URL must use http or https scheme")
        }
        if (sanitized.length > MAX_URL_LENGTH) {
            throw IllegalArgumentException("URL exceeds maximum length of $MAX_URL_LENGTH characters")
        }
        val host =
            try {
                URI(sanitized).host?.lowercase()
            } catch (e: Exception) {
                throw IllegalArgumentException("URL could not be parsed: ${e.message}", e)
            }
        if (host == null) {
            throw IllegalArgumentException("URL must have a valid host")
        }
        if (PRIVATE_HOST_PATTERNS.any { it.matches(host) }) {
            throw IllegalArgumentException("URL must not point to private or internal addresses")
        }
    }
}
