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
                if (isNewerVersion(latestVersion)) UpdateResult.UpdateAvailable(latestVersion)
                else UpdateResult.UpToDate
            } else {
                UpdateResult.CheckFailed("Server returned ${response.statusCode()}")
            }
        } catch (e: Exception) {
            logger.warn("Update check failed", e)
            UpdateResult.CheckFailed(e.message ?: "Unknown error")
        }
    }

    private fun isNewerVersion(latest: String): Boolean {
        val current = currentVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(current.size, latestParts.size)) {
            val c = current.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    sealed class UpdateResult {
        data object UpToDate : UpdateResult()

        data class UpdateAvailable(val version: String) : UpdateResult()

        data object NoUpdateUrl : UpdateResult()

        data class CheckFailed(val message: String) : UpdateResult()
    }
}
