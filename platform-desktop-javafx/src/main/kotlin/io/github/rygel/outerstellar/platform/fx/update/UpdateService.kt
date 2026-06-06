package io.github.rygel.outerstellar.platform.fx.update

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.slf4j.LoggerFactory

class UpdateService(private val currentVersion: String, private val updateUrl: String) {

    private val logger = LoggerFactory.getLogger(UpdateService::class.java)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun checkForUpdate(): UpdateResult {
        if (updateUrl.isBlank()) return UpdateResult.NoUpdateUrl
        return try {
            val request =
                HttpRequest.newBuilder().uri(URI.create(updateUrl)).timeout(Duration.ofSeconds(5)).GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val latestVersion = response.body().trim()
                try {
                    if (isNewerVersion(latestVersion)) UpdateResult.UpdateAvailable(latestVersion)
                    else UpdateResult.UpToDate
                } catch (e: IllegalArgumentException) {
                    UpdateResult.CheckFailed(e.message ?: "Invalid version")
                }
            } else {
                UpdateResult.CheckFailed("Server returned ${response.statusCode()}")
            }
        } catch (e: Exception) {
            logger.warn("Update check failed", e)
            UpdateResult.CheckFailed(e.message ?: "Unknown error")
        }
    }

    private fun isNewerVersion(latest: String): Boolean {
        val current = parseVersion(currentVersion, "current")
        val latestParts = parseVersion(latest, "latest")
        val width = maxOf(current.size, latestParts.size)
        val normalizedCurrent = current.padVersion(width)
        val normalizedLatest = latestParts.padVersion(width)
        for (i in 0 until width) {
            val c = normalizedCurrent[i]
            val l = normalizedLatest[i]
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun parseVersion(version: String, label: String): List<Int> {
        val normalized = version.removePrefix("v")
        require(normalized.isNotBlank()) { "Invalid $label version: $version" }
        return normalized.split(".").map { segment ->
            require(segment.isNotBlank()) { "Invalid $label version: $version" }
            requireNotNull(segment.toIntOrNull()) { "Invalid $label version segment '$segment' in $version" }
        }
    }

    private fun List<Int>.padVersion(width: Int): List<Int> =
        if (size >= width) this else this + List(width - size) { 0 }

    sealed class UpdateResult {
        data object UpToDate : UpdateResult()

        data class UpdateAvailable(val version: String) : UpdateResult()

        data object NoUpdateUrl : UpdateResult()

        data class CheckFailed(val message: String) : UpdateResult()
    }
}
