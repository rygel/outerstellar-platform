package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import org.slf4j.LoggerFactory
import java.net.URL
import javax.swing.SwingWorker

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.swing.UpdateService")

class UpdateService(
    private val currentVersion: String,
    private val updateUrl: String,
    private val i18nService: I18nService,
    private val onUpdateAvailable: (String) -> Unit
) {
    fun checkForUpdates() {
        if (updateUrl.isBlank()) {
            logger.info("Auto-update check disabled: updateUrl is blank")
            return
        }

        object : SwingWorker<String?, Unit>() {
            override fun doInBackground(): String? {
                return try {
                    logger.info("Checking for updates at $updateUrl")
                    // In a real app, this would be an HTTP request to get the latest version
                    // For this starter project, we simulate the check
                    // We can also try to read from the URL if it's a real file/endpoint
                    val latestVersion = readLatestVersion()
                    if (isNewer(latestVersion, currentVersion)) {
                        latestVersion
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    logger.error("Failed to check for updates", e)
                    null
                }
            }

            override fun done() {
                try {
                    val latestVersion = get()
                    if (latestVersion != null) {
                        logger.info("Update available: $latestVersion (current: $currentVersion)")
                        onUpdateAvailable(latestVersion)
                    } else {
                        logger.info("No update available or check failed")
                    }
                } catch (e: Exception) {
                    logger.error("Error processing update check result", e)
                }
            }
        }.execute()
    }

    private fun readLatestVersion(): String {
        // Simulation: If updateUrl is "local", return current version.
        // Otherwise try to fetch.
        if (updateUrl == "local") return currentVersion

        return try {
            URL(updateUrl).readText().trim()
        } catch (e: Exception) {
            // Fallback for simulation/testing
            logger.warn("Could not read from $updateUrl, returning current version")
            currentVersion
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        // Simple version comparison (e.g. 1.1.0 > 1.0.0)
        val latestParts = latest.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }

        for (i in 0 until minOf(latestParts.size, currentParts.size)) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }
}
