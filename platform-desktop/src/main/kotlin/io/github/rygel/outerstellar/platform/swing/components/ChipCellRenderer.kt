package io.github.rygel.outerstellar.platform.swing.components

import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.border.EmptyBorder
import javax.swing.table.TableCellRenderer

/**
 * Generic table-cell renderer that displays a list of items as rounded "chip" labels.
 *
 * This is a reusable replacement for domain-specific renderers such as `GroupsCellRenderer` and `TagsCellRenderer` from
 * MAIA. The caller supplies functions to extract the label text and background color for each item.
 *
 * @param T The element type contained in each cell's list.
 * @param labelFn Extracts the display text from an element.
 * @param colorFn Determines the chip background color for an element.
 */
class ChipCellRenderer<T>(private val labelFn: (T) -> String, private val colorFn: (T) -> Color) : TableCellRenderer {

    private val panel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply { isOpaque = false }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        panel.removeAll()

        when (value) {
            null -> panel.add(JLabel(""))
            is List<*> -> {
                @Suppress("UNCHECKED_CAST") val items = value as List<T>
                items.forEach { item -> panel.add(Chip(labelFn(item), colorFn(item))) }
            }
            else -> panel.add(JLabel(value.toString()))
        }

        if (isSelected) {
            panel.background = table.selectionBackground
            panel.isOpaque = true
        } else {
            panel.background = table.background
            panel.isOpaque = false
        }

        return panel
    }

    /** A single rounded-rectangle chip label. */
    private class Chip(text: String, chipColor: Color) : JLabel(text) {
        init {
            isOpaque = true
            background = chipColor
            foreground = contrastForeground(chipColor)
            font = CHIP_FONT
            border = EmptyBorder(2, CHIP_PADDING_H, 2, CHIP_PADDING_H)
        }

        override fun paintComponent(g: Graphics) {
            val g2d = g.create() as Graphics2D
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val rr =
                    RoundRectangle2D.Double(
                        0.0,
                        0.0,
                        (width - 1).toDouble(),
                        (height - 1).toDouble(),
                        ARC_SIZE,
                        ARC_SIZE,
                    )
                g2d.color = background
                g2d.fill(rr)
            } finally {
                g2d.dispose()
            }

            val g2t = g.create() as Graphics2D
            try {
                g2t.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2t.color = foreground
                g2t.font = font
                val fm = g2t.fontMetrics
                val x = (width - fm.stringWidth(text)) / 2
                val y = (height + fm.ascent - fm.descent) / 2
                g2t.drawString(text, x, y)
            } finally {
                g2t.dispose()
            }
        }

        companion object {
            private const val LUMA_RED = 0.299
            private const val LUMA_GREEN = 0.587
            private const val LUMA_BLUE = 0.114
            private const val LUMA_DIVISOR = 255.0
            private const val LUMA_THRESHOLD = 0.5

            private fun contrastForeground(bg: Color): Color {
                val luminance = (LUMA_RED * bg.red + LUMA_GREEN * bg.green + LUMA_BLUE * bg.blue) / LUMA_DIVISOR
                return if (luminance > LUMA_THRESHOLD) Color.BLACK else Color.WHITE
            }
        }
    }

    companion object {
        private val CHIP_FONT = Font("SansSerif", Font.PLAIN, 10)
        private const val CHIP_PADDING_H = 4
        private const val ARC_SIZE = 8.0

        /**
         * Generates a deterministic color from a string hash. Useful as a default [colorFn] when there is no
         * domain-specific mapping.
         */
        private const val HUE_RANGE = 360
        private const val HASH_MASK = 0x7FFFFFFF
        private const val HSB_SATURATION = 0.55f
        private const val HSB_BRIGHTNESS = 0.70f

        @JvmStatic
        fun hashColor(label: String): Color {
            val hash = label.hashCode()
            val hue = (hash and HASH_MASK) % HUE_RANGE
            return Color.getHSBColor(hue / HUE_RANGE.toFloat(), HSB_SATURATION, HSB_BRIGHTNESS)
        }

        /** Convenience factory that uses [Any.toString] as label and hash-based coloring. */
        @JvmStatic
        fun <T> withDefaults(): ChipCellRenderer<T> =
            ChipCellRenderer(labelFn = { it.toString() }, colorFn = { hashColor(it.toString()) })
    }
}
