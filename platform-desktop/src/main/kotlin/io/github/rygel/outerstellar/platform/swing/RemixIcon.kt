package io.github.rygel.outerstellar.platform.swing

import com.formdev.flatlaf.extras.FlatSVGIcon
import java.awt.Color
import javax.swing.Icon
import javax.swing.UIManager
import org.slf4j.LoggerFactory

object RemixIcon {
    private val logger = LoggerFactory.getLogger(RemixIcon::class.java)
    private const val BASE_PATH = "icons"

    fun get(name: String, size: Int = 18): Icon {
        val fileName = if (name.contains("/")) name.substringAfterLast("/") else name
        val path = requireExistingPath(fileName)

        return try {
            FlatSVGIcon(path, size, size).apply {
                setColorFilter(FlatSVGIcon.ColorFilter { UIManager.getColor("Label.foreground") ?: Color.WHITE })
            }
        } catch (e: IllegalArgumentException) {
            logger.error("Failed to load icon {}: {}", path, e.message, e)
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Unexpected error loading icon {}: {}", path, e.message, e)
            throw IllegalStateException("Failed to load icon: $path", e)
        }
    }

    private fun requireExistingPath(fileName: String): String {
        val classLoader = RemixIcon::class.java.classLoader
        val candidatePath = "$BASE_PATH/$fileName.svg"
        requireNotNull(classLoader.getResource(candidatePath)) { "Missing icon resource: $candidatePath" }
        return candidatePath
    }
}
