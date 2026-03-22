package io.github.rygel.outerstellar.platform.swing.util

import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * [Icon] implementation that renders a Unicode character (emoji/symbol).
 * Ensures proper rendering of Unicode icons in FlatLaf menus via font fallback.
 *
 * @param unicode The Unicode string to render (e.g. "\u2B1C" or an emoji literal).
 * @param size    The icon size in pixels (default 16).
 */
class UnicodeIcon(
    private val unicode: String,
    private val size: Int = DEFAULT_SIZE,
) : Icon {

    private val font: Font = resolveFont()

    private fun resolveFont(): Font {
        val fontSize = size - 2
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        return when {
            "Segoe UI Emoji" in available -> Font("Segoe UI Emoji", Font.PLAIN, fontSize)
            "Noto Color Emoji" in available -> Font("Noto Color Emoji", Font.PLAIN, fontSize)
            else -> Font("Dialog", Font.PLAIN, fontSize)
        }
    }

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        val g2d = g.create() as Graphics2D
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
            g2d.font = font
            g2d.color = c.foreground

            val fm = g2d.fontMetrics
            val textY = y + (iconHeight - fm.height) / 2 + fm.ascent
            g2d.drawString(unicode, x + 1, textY)
        } finally {
            g2d.dispose()
        }
    }

    override fun getIconWidth(): Int = size

    override fun getIconHeight(): Int = size

    companion object {
        private const val DEFAULT_SIZE = 16

        /**
         * Creates an icon by parsing a Unicode escape string such as `"\\u2B1C"`.
         */
        @JvmStatic
        fun fromEscape(unicodeEscape: String): UnicodeIcon {
            val sb = StringBuilder()
            for (part in unicodeEscape.split("\\u")) {
                if (part.isEmpty()) continue
                try {
                    val codePoint = part.substring(0, 4).toInt(16)
                    sb.append(codePoint.toChar())
                    if (part.length > 4) sb.append(part.substring(4))
                } catch (_: NumberFormatException) {
                    sb.append(part)
                }
            }
            return UnicodeIcon(sb.toString())
        }
    }
}
