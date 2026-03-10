package dev.outerstellar.starter.swing

import com.formdev.flatlaf.extras.FlatSVGIcon
import java.awt.Color
import javax.swing.UIManager

object RemixIcon {
    private const val BASE_PATH = "META-INF/resources/webjars/remixicon/4.6.0/icons"

    fun get(name: String, size: Int = 18): Icon {
        val path = if (name.contains("/")) "$BASE_PATH/$name.svg" else "$BASE_PATH/system/$name.svg"
        
        return try {
            FlatSVGIcon(path, size, size).apply {
                val color = UIManager.getColor("Label.foreground") ?: Color.WHITE
                setColorFilter(FlatSVGIcon.ColorFilter { color })
            }
        } catch (e: Exception) {
            // Fallback to a simple circle or nothing if icon is missing from classpath
            object : Icon {
                override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics?, x: Int, y: Int) {}
                override fun getIconWidth() = size
                override fun getIconHeight() = size
            }
        }
    }
}
