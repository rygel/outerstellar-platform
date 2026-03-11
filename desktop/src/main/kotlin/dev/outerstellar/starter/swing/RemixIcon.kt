package dev.outerstellar.starter.swing

import com.formdev.flatlaf.extras.FlatSVGIcon
import org.slf4j.LoggerFactory
import java.awt.Color
import javax.swing.Icon
import javax.swing.UIManager

object RemixIcon {
    private val logger = LoggerFactory.getLogger(RemixIcon::class.java)
    private const val BASE_PATH = "icons"

    fun get(name: String, size: Int = 18): Icon {
        // Flattened names for local resources
        val fileName = if (name.contains("/")) name.substringAfterLast("/") else name
        val path = "$BASE_PATH/$fileName.svg"

        return try {
            FlatSVGIcon(path, size, size).apply {
                val color = UIManager.getColor("Label.foreground") ?: Color.WHITE
                setColorFilter(FlatSVGIcon.ColorFilter { color })
            }
        } catch (e: IllegalArgumentException) {
            logger.warn("Failed to load icon {}: {}", path, e.message)
            createEmptyIcon(size)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Unexpected error loading icon {}: {}", path, e.message)
            createEmptyIcon(size)
        }
    }

    private fun createEmptyIcon(size: Int): Icon = object : Icon {
        override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics?, x: Int, y: Int) {
            // No-op placeholder
        }
        override fun getIconWidth() = size
        override fun getIconHeight() = size
    }
}
