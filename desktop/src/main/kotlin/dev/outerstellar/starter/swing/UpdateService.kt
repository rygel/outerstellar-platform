package dev.outerstellar.starter.swing

import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import javax.swing.SwingWorker

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.swing.UpdateService")

class UpdateService(
    private val currentVersion: String,
    private val updateUrl: String,
    private val onUpdateAvailable: (String) -> Unit
) {
    fun checkForUpdates() {
        if (updateUrl.isBlank()) {
            logger.info("Auto-update check disabled: updateUrl is blank")
            return
        }

        object : SwingWorker<String?, Unit>() {
            @Suppress("TooGenericExceptionCaught")
            override fun doInBackground(): String? {
                return try {
                    logger.info("Checking for updates at $updateUrl")
                    val latestVersion = readLatestVersion()
                    if (isNewer(latestVersion, currentVersion)) {
                        latestVersion
                    } else {
                        null
                    }
                } catch (e: IOException) {
                    logger.error("IO error checking for updates: {}", e.message)
                    null
                } catch (e: Exception) {
                    logger.error("Unexpected error checking for updates: {}", e.message)
                    null
                }
            }

            @Suppress("TooGenericExceptionCaught")
            override fun done() {
                try {
                    val latestVersion = get()
                    if (latestVersion != null) {
                        logger.info("Update available: $latestVersion (current: $currentVersion)")
                        onUpdateAvailable(latestVersion)
                    } else {
                        logger.info("No update available or check failed")
                    }
                } catch (e: java.util.concurrent.ExecutionException) {
                    logger.error("Execution error processing update check result: {}", e.message)
                } catch (e: Exception) {
                    logger.error("Error processing update check result: {}", e.message)
                }
            }
        }.execute()
    }

    private fun readLatestVersion(): String {
        if (updateUrl == "local") return currentVersion

        return try {
            val url: URL = URI.create(updateUrl).toURL()
            url.readText().trim()
        } catch (e: MalformedURLException) {
            logger.warn("Invalid update URL {}: {}", updateUrl, e.message)
            currentVersion
        } catch (e: IOException) {
            logger.warn("Could not read from {}, returning current version. Error: {}", updateUrl, e.message)
            currentVersion
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }

        val size = minOf(latestParts.size, currentParts.size)
        for (i in 0 until size) {
            if (latestParts[i] != currentParts[i]) {
                return latestParts[i] > currentParts[i]
            }
        }
        return latestParts.size > currentParts.size
    }
}
