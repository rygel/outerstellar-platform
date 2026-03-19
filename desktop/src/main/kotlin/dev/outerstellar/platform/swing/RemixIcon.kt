package dev.outerstellar.platform.swing

import com.formdev.flatlaf.extras.FlatSVGIcon
import java.awt.Color
import javax.swing.Icon
import javax.swing.UIManager
import org.slf4j.LoggerFactory

object RemixIcon {
    private val logger = LoggerFactory.getLogger(RemixIcon::class.java)
    private const val BASE_PATH = "icons"
    private const val DEFAULT_ICON = "settings-3-line"
    private val fallbackByName =
        mapOf(
            "lock-password-line" to "settings-3-line",
            "logout-box-r-line" to "settings-3-line",
            "user-add-line" to "add-box-line",
            "error-warning-line" to "settings-3-line",
            "question-line" to "settings-3-line",
            "information-line" to "settings-3-line",
            "chat-smile-3-line" to "settings-3-line",
        )

    fun get(name: String, size: Int = 18): Icon {
        val fileName = if (name.contains("/")) name.substringAfterLast("/") else name
        val resolved = resolveExistingFile(fileName)
        val path = "$BASE_PATH/$resolved.svg"

        return try {
            FlatSVGIcon(path, size, size).apply {
                setColorFilter(
                    FlatSVGIcon.ColorFilter {
                        UIManager.getColor("Label.foreground") ?: Color.WHITE
                    }
                )
            }
        } catch (e: IllegalArgumentException) {
            logger.warn("Failed to load icon {}: {}", path, e.message)
            createEmptyIcon(size)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Unexpected error loading icon {}: {}", path, e.message)
            createEmptyIcon(size)
        }
    }

    private fun createEmptyIcon(size: Int): Icon =
        object : Icon {
            override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics?, x: Int, y: Int) {
                // No-op placeholder
            }

            override fun getIconWidth() = size

            override fun getIconHeight() = size
        }

    private fun resolveExistingFile(fileName: String): String {
        val classLoader = RemixIcon::class.java.classLoader
        val candidatePath = "$BASE_PATH/$fileName.svg"
        if (classLoader.getResource(candidatePath) != null) return fileName

        val mapped = fallbackByName[fileName]
        if (mapped != null && classLoader.getResource("$BASE_PATH/$mapped.svg") != null) {
            logger.debug("Using mapped fallback icon {} for missing {}", mapped, fileName)
            return mapped
        }
        logger.warn("Missing icon {}. Falling back to {}", fileName, DEFAULT_ICON)
        return DEFAULT_ICON
    }
}
