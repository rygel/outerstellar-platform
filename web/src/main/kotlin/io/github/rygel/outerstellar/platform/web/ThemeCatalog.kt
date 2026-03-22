package io.github.rygel.outerstellar.platform.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.rygel.outerstellar.platform.model.ThemeDefinition

object ThemeCatalog {
    private val objectMapper = jacksonObjectMapper()
    private val cssVariablesCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val extendedCssCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val themes: List<ThemeDefinition> by lazy {
        val resourceStream =
            requireNotNull(ThemeCatalog::class.java.classLoader.getResourceAsStream("themes.json")) {
                "Unable to load themes.json from the classpath."
            }

        resourceStream.use { objectMapper.readValue<List<ThemeDefinition>>(it) }
    }

    fun allThemes(): List<ThemeDefinition> = themes

    fun findTheme(themeId: String): ThemeDefinition =
        themes.firstOrNull { it.id == themeId } ?: themes.first { it.id == "dark" }

    /** Returns dark themes only, based on luminance detection of the background color. */
    fun darkThemes(): List<ThemeDefinition> = themes.filter { isDarkTheme(it) }

    /** Returns light themes only, based on luminance detection of the background color. */
    fun lightThemes(): List<ThemeDefinition> = themes.filter { !isDarkTheme(it) }

    /**
     * Detects whether a theme is dark by computing the relative luminance of its background color. Falls back to the
     * `type` field when the background color is absent or unparseable.
     */
    fun isDarkTheme(theme: ThemeDefinition): Boolean {
        val bg = theme.colors["background"] ?: return theme.type == "dark"
        val lum = relativeLuminance(bg) ?: return theme.type == "dark"
        return lum < DARK_LUMINANCE_THRESHOLD
    }

    /**
     * Generates the basic CSS variables (preserving the original format) that map each theme color key to a `--color-*`
     * custom property.
     */
    fun toCssVariables(themeId: String): String =
        cssVariablesCache.computeIfAbsent(themeId) { id ->
            val theme = findTheme(id)

            buildString {
                append(":root {")
                theme.colors.forEach { (key, value) ->
                    append("--color-")
                    append(key)
                    append(": ")
                    append(value)
                    append(";")
                }
                append("} body { color-scheme: ")
                append(if (isDarkTheme(theme)) "dark" else "light")
                append("; }")
            }
        }

    /**
     * Generates an extended set of CSS variables for a theme, including:
     * - Brightness-adjusted variants (hover, secondary, tertiary, muted)
     * - Semantic aliases (accent, success, danger, warning)
     * - Shadow, radius, and spacing tokens
     * - Typography scale variables
     *
     * Modelled after MAIA's ThemeService.generateCssVariables.
     */
    @Suppress("LongMethod")
    fun toExtendedCssVariables(themeId: String): String {
        extendedCssCache[themeId]?.let {
            return it
        }
        val theme = findTheme(themeId)
        val c = theme.colors
        val dark = isDarkTheme(theme)

        val bg = c["background"] ?: "#1e1e1e"
        val fg = c["foreground"] ?: "#d4d4d4"
        val componentBg = c["componentBackground"] ?: bg
        val border = c["borderColor"] ?: componentBg
        val accent = c["accent"] ?: c["selectionBackground"] ?: "#007acc"
        val success = c["success"] ?: "#4caf50"
        val danger = c["danger"] ?: c["red"] ?: "#f44336"
        val warning = c["warning"] ?: c["yellow"] ?: "#ff9800"

        return buildString {
                append(":root[data-theme=\"")
                append(themeId)
                appendLine("\"] {")

                // --- Core backgrounds ---
                cssVar("--bg-primary", bg)
                cssVar("--bg-secondary", componentBg)
                cssVar("--bg-tertiary", adjustBrightness(componentBg, if (dark) 10 else -10))
                cssVar("--bg-hover", adjustBrightness(componentBg, if (dark) 15 else -5))

                // --- Text ---
                cssVar("--text-primary", fg)
                cssVar("--text-secondary", adjustBrightness(fg, -20))
                cssVar("--text-muted", adjustBrightness(fg, if (dark) -40 else -35))

                // --- Border ---
                cssVar("--border-color", border)

                // --- Accent / semantic ---
                cssVar("--accent-primary", accent)
                cssVar("--accent-primary-hover", adjustBrightness(accent, if (dark) 15 else -15))
                cssVar("--accent-success", success)
                cssVar("--accent-success-hover", adjustBrightness(success, if (dark) 15 else -15))
                cssVar("--accent-danger", danger)
                cssVar("--accent-danger-hover", adjustBrightness(danger, if (dark) 15 else -15))
                cssVar("--accent-warning", warning)
                cssVar("--accent-info", accent)

                // --- Shadows ---
                val shadowAlpha = if (dark) "0.3" else "0.1"
                val shadowAlphaLg = if (dark) "0.4" else "0.15"
                cssVar("--shadow-sm", "0 1px 3px rgba(0,0,0,$shadowAlpha)")
                cssVar("--shadow-md", "0 4px 6px rgba(0,0,0,$shadowAlpha)")
                cssVar("--shadow-lg", "0 10px 25px rgba(0,0,0,$shadowAlphaLg)")

                // --- Radii ---
                cssVar("--radius-sm", "4px")
                cssVar("--radius-md", "8px")
                cssVar("--radius-lg", "12px")

                // --- Typography scale ---
                cssVar("--font-sans", "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif")
                cssVar("--font-size-xs", "0.75rem")
                cssVar("--font-size-sm", "0.875rem")
                cssVar("--font-size-md", "1rem")
                cssVar("--font-size-lg", "1.125rem")
                cssVar("--font-size-xl", "1.25rem")
                cssVar("--font-size-2xl", "1.5rem")
                cssVar("--font-size-3xl", "2rem")

                // --- Spacing scale ---
                cssVar("--spacing-xs", "4px")
                cssVar("--spacing-sm", "8px")
                cssVar("--spacing-md", "16px")
                cssVar("--spacing-lg", "24px")
                cssVar("--spacing-xl", "32px")

                // --- Component-level aliases ---
                cssVar("--header-bg", componentBg)
                cssVar("--sidebar-bg", adjustBrightness(bg, if (dark) 5 else -3))
                cssVar("--card-bg", bg)
                cssVar("--input-bg", componentBg)
                cssVar("--input-border", border)
                cssVar("--table-header-bg", componentBg)
                cssVar("--table-row-hover", adjustBrightness(componentBg, if (dark) 10 else -5))
                cssVar("--modal-overlay", if (dark) "rgba(0,0,0,0.7)" else "rgba(0,0,0,0.5)")

                appendLine("}")
            }
            .also { extendedCssCache[themeId] = it }
    }

    /** Generates extended CSS for every loaded theme, concatenated. */
    fun generateAllExtendedStyles(): String = themes.joinToString("\n") { toExtendedCssVariables(it.id) }

    // -----------------------------------------------------------------------
    // Color utilities
    // -----------------------------------------------------------------------

    /**
     * Adjusts the brightness of a hex color by the given [percent] (positive = lighter, negative = darker). Returns the
     * original value when parsing fails.
     */
    internal fun adjustBrightness(hexColor: String, percent: Int): String {
        val rgb = parseHexRgb(hexColor)
        if (rgb.isEmpty()) return hexColor
        val factor = percent / 100.0
        val r = (rgb[0] + (MAX_CHANNEL - rgb[0]) * factor).coerceIn(0.0, MAX_CHANNEL_D).toInt()
        val g = (rgb[1] + (MAX_CHANNEL - rgb[1]) * factor).coerceIn(0.0, MAX_CHANNEL_D).toInt()
        val b = (rgb[2] + (MAX_CHANNEL - rgb[2]) * factor).coerceIn(0.0, MAX_CHANNEL_D).toInt()
        return "#${hex2(r)}${hex2(g)}${hex2(b)}"
    }

    /** Computes the relative luminance of a hex color per WCAG 2.0. Returns `null` when the string cannot be parsed. */
    internal fun relativeLuminance(hexColor: String): Double? {
        val rgb = parseHexRgb(hexColor)
        if (rgb.isEmpty()) return null
        fun channel(v: Int): Double {
            val s = v / MAX_CHANNEL_D
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2])
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun StringBuilder.cssVar(name: String, value: String) {
        append("    ")
        append(name)
        append(": ")
        append(value)
        appendLine(";")
    }

    /** Parses "#RGB" or "#RRGGBB" into an [IntArray] of [r, g, b]. Returns an empty array when the input is invalid. */
    private fun parseHexRgb(hex: String): IntArray {
        if (!hex.startsWith("#")) return intArrayOf()
        val h = hex.substring(1)
        val expanded =
            when (h.length) {
                3 -> "${h[0]}${h[0]}${h[1]}${h[1]}${h[2]}${h[2]}"
                6 -> h
                else -> return intArrayOf()
            }
        return try {
            intArrayOf(
                expanded.substring(0, 2).toInt(16),
                expanded.substring(2, 4).toInt(16),
                expanded.substring(4, 6).toInt(16),
            )
        } catch (_: NumberFormatException) {
            intArrayOf()
        }
    }

    private fun hex2(value: Int): String = value.toString(16).padStart(2, '0')

    private const val MAX_CHANNEL = 255
    private const val MAX_CHANNEL_D = 255.0
    private const val DARK_LUMINANCE_THRESHOLD = 0.2
}
