package io.github.rygel.outerstellar.platform.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.rygel.outerstellar.platform.model.ThemeDefinition
import java.util.concurrent.ConcurrentHashMap

object ThemeCatalog {
    private val objectMapper = jacksonObjectMapper()
    private val cssVariablesCache = ConcurrentHashMap<String, String>()
    private val extendedCssCache = ConcurrentHashMap<String, String>()

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
        val lum = ColorUtils.relativeLuminance(bg) ?: return theme.type == "dark"
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
     * Generates an extended set of CSS variables for a theme. Delegates to [ExtendedCssBuilder] for the actual
     * generation; results are cached per theme ID.
     */
    fun toExtendedCssVariables(themeId: String): String {
        extendedCssCache[themeId]?.let {
            return it
        }
        val css = ExtendedCssBuilder.build(themeId, findTheme(themeId), isDarkTheme(findTheme(themeId)))
        extendedCssCache[themeId] = css
        return css
    }

    /** Generates extended CSS for every loaded theme, concatenated. */
    fun generateAllExtendedStyles(): String = themes.joinToString("\n") { toExtendedCssVariables(it.id) }

    private const val DARK_LUMINANCE_THRESHOLD = 0.2
}

/**
 * Builds extended CSS variable blocks for a single theme. Extracted from [ThemeCatalog] to reduce object complexity.
 */
private object ExtendedCssBuilder {
    private const val BRIGHTNESS_LIGHT_STEP = 5
    private const val BRIGHTNESS_MEDIUM_STEP = 10
    private const val BRIGHTNESS_LARGE_STEP = 15
    private const val BRIGHTNESS_TEXT_SECONDARY = -20
    private const val BRIGHTNESS_TEXT_MUTED_DARK = -40
    private const val BRIGHTNESS_TEXT_MUTED_LIGHT = -35
    private const val BRIGHTNESS_SIDEBAR_LIGHT = -3
    private const val BRIGHTNESS_TERTIARY_LIGHT = -10
    private const val BRIGHTNESS_HOVER_LIGHT = -5
    private const val BRIGHTNESS_ACCENT_LIGHT = -15

    private const val DEFAULT_BG = "#1e1e1e"
    private const val DEFAULT_FG = "#d4d4d4"
    private const val DEFAULT_ACCENT = "#007acc"
    private const val DEFAULT_SUCCESS = "#4caf50"
    private const val DEFAULT_DANGER = "#f44336"
    private const val DEFAULT_WARNING = "#ff9800"

    fun build(themeId: String, theme: ThemeDefinition, dark: Boolean): String {
        val c = theme.colors
        val bg = c["background"] ?: DEFAULT_BG
        val fg = c["foreground"] ?: DEFAULT_FG
        val componentBg = c["componentBackground"] ?: bg
        val border = c["borderColor"] ?: componentBg
        val accent = c["accent"] ?: c["selectionBackground"] ?: DEFAULT_ACCENT
        val success = c["success"] ?: DEFAULT_SUCCESS
        val danger = c["danger"] ?: c["red"] ?: DEFAULT_DANGER
        val warning = c["warning"] ?: c["yellow"] ?: DEFAULT_WARNING

        return buildString {
            append(":root[data-theme=\"")
            append(themeId)
            appendLine("\"] {")

            appendBackgrounds(bg, componentBg, dark)
            appendText(fg, dark)
            cssVar("--border-color", border)
            appendAccents(accent, success, danger, warning, dark)
            appendShadows(dark)
            appendDesignTokens()
            appendComponentAliases(bg, componentBg, border, dark)

            appendLine("}")
        }
    }

    private fun StringBuilder.appendBackgrounds(bg: String, componentBg: String, dark: Boolean) {
        cssVar("--bg-primary", bg)
        cssVar("--bg-secondary", componentBg)
        cssVar(
            "--bg-tertiary",
            ColorUtils.adjustBrightness(componentBg, if (dark) BRIGHTNESS_MEDIUM_STEP else BRIGHTNESS_TERTIARY_LIGHT),
        )
        cssVar(
            "--bg-hover",
            ColorUtils.adjustBrightness(componentBg, if (dark) BRIGHTNESS_LARGE_STEP else BRIGHTNESS_HOVER_LIGHT),
        )
    }

    private fun StringBuilder.appendText(fg: String, dark: Boolean) {
        cssVar("--text-primary", fg)
        cssVar("--text-secondary", ColorUtils.adjustBrightness(fg, BRIGHTNESS_TEXT_SECONDARY))
        cssVar(
            "--text-muted",
            ColorUtils.adjustBrightness(fg, if (dark) BRIGHTNESS_TEXT_MUTED_DARK else BRIGHTNESS_TEXT_MUTED_LIGHT),
        )
    }

    private fun StringBuilder.appendAccents(
        accent: String,
        success: String,
        danger: String,
        warning: String,
        dark: Boolean,
    ) {
        val hoverStep = if (dark) BRIGHTNESS_LARGE_STEP else BRIGHTNESS_ACCENT_LIGHT
        cssVar("--accent-primary", accent)
        cssVar("--accent-primary-hover", ColorUtils.adjustBrightness(accent, hoverStep))
        cssVar("--accent-success", success)
        cssVar("--accent-success-hover", ColorUtils.adjustBrightness(success, hoverStep))
        cssVar("--accent-danger", danger)
        cssVar("--accent-danger-hover", ColorUtils.adjustBrightness(danger, hoverStep))
        cssVar("--accent-warning", warning)
        cssVar("--accent-info", accent)
    }

    private fun StringBuilder.appendShadows(dark: Boolean) {
        val shadowAlpha = if (dark) "0.3" else "0.1"
        val shadowAlphaLg = if (dark) "0.4" else "0.15"
        cssVar("--shadow-sm", "0 1px 3px rgba(0,0,0,$shadowAlpha)")
        cssVar("--shadow-md", "0 4px 6px rgba(0,0,0,$shadowAlpha)")
        cssVar("--shadow-lg", "0 10px 25px rgba(0,0,0,$shadowAlphaLg)")
    }

    private fun StringBuilder.appendDesignTokens() {
        cssVar("--radius-sm", "4px")
        cssVar("--radius-md", "8px")
        cssVar("--radius-lg", "12px")

        cssVar("--font-sans", "-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif")
        cssVar("--font-size-xs", "0.75rem")
        cssVar("--font-size-sm", "0.875rem")
        cssVar("--font-size-md", "1rem")
        cssVar("--font-size-lg", "1.125rem")
        cssVar("--font-size-xl", "1.25rem")
        cssVar("--font-size-2xl", "1.5rem")
        cssVar("--font-size-3xl", "2rem")

        cssVar("--spacing-xs", "4px")
        cssVar("--spacing-sm", "8px")
        cssVar("--spacing-md", "16px")
        cssVar("--spacing-lg", "24px")
        cssVar("--spacing-xl", "32px")
    }

    private fun StringBuilder.appendComponentAliases(bg: String, componentBg: String, border: String, dark: Boolean) {
        cssVar("--header-bg", componentBg)
        cssVar(
            "--sidebar-bg",
            ColorUtils.adjustBrightness(bg, if (dark) BRIGHTNESS_LIGHT_STEP else BRIGHTNESS_SIDEBAR_LIGHT),
        )
        cssVar("--card-bg", bg)
        cssVar("--input-bg", componentBg)
        cssVar("--input-border", border)
        cssVar("--table-header-bg", componentBg)
        cssVar(
            "--table-row-hover",
            ColorUtils.adjustBrightness(componentBg, if (dark) BRIGHTNESS_MEDIUM_STEP else BRIGHTNESS_HOVER_LIGHT),
        )
        cssVar("--modal-overlay", if (dark) "rgba(0,0,0,0.7)" else "rgba(0,0,0,0.5)")
    }

    private fun StringBuilder.cssVar(name: String, value: String) {
        append("    ")
        append(name)
        append(": ")
        append(value)
        appendLine(";")
    }
}

/** Hex-color parsing and manipulation utilities shared between [ThemeCatalog] and [ExtendedCssBuilder]. */
internal object ColorUtils {
    private const val MAX_CHANNEL = 255
    private const val MAX_CHANNEL_D = 255.0
    private const val PERCENT_DIVISOR = 100.0
    private const val SHORT_HEX_LEN = 3
    private const val FULL_HEX_LEN = 6
    private const val HEX_RADIX = 16
    private const val HEX_PAIR_WIDTH = 2
    private const val HEX_PAIR_1_END = 2
    private const val HEX_PAIR_2_START = 2
    private const val HEX_PAIR_2_END = 4
    private const val HEX_PAIR_3_START = 4
    private const val HEX_PAIR_3_END = 6
    private const val SRGB_LINEAR_THRESHOLD = 0.03928
    private const val SRGB_LINEAR_DIVISOR = 12.92
    private const val SRGB_GAMMA_OFFSET = 0.055
    private const val SRGB_GAMMA_DIVISOR = 1.055
    private const val SRGB_GAMMA_EXPONENT = 2.4
    private const val LUMINANCE_R = 0.2126
    private const val LUMINANCE_G = 0.7152
    private const val LUMINANCE_B = 0.0722

    /**
     * Adjusts the brightness of a hex color by the given [percent] (positive = lighter, negative = darker). Returns the
     * original value when parsing fails.
     */
    fun adjustBrightness(hexColor: String, percent: Int): String {
        val rgb = parseHexRgb(hexColor)
        if (rgb.isEmpty()) return hexColor
        val factor = percent / PERCENT_DIVISOR
        val r = (rgb[0] + (MAX_CHANNEL - rgb[0]) * factor).coerceIn(0.0, MAX_CHANNEL_D).toInt()
        val g = (rgb[1] + (MAX_CHANNEL - rgb[1]) * factor).coerceIn(0.0, MAX_CHANNEL_D).toInt()
        val b = (rgb[2] + (MAX_CHANNEL - rgb[2]) * factor).coerceIn(0.0, MAX_CHANNEL_D).toInt()
        return "#${hex2(r)}${hex2(g)}${hex2(b)}"
    }

    /** Computes the relative luminance of a hex color per WCAG 2.0. Returns `null` when the string cannot be parsed. */
    fun relativeLuminance(hexColor: String): Double? {
        val rgb = parseHexRgb(hexColor)
        if (rgb.isEmpty()) return null
        fun channel(v: Int): Double {
            val s = v / MAX_CHANNEL_D
            return if (s <= SRGB_LINEAR_THRESHOLD) {
                s / SRGB_LINEAR_DIVISOR
            } else {
                Math.pow((s + SRGB_GAMMA_OFFSET) / SRGB_GAMMA_DIVISOR, SRGB_GAMMA_EXPONENT)
            }
        }
        return LUMINANCE_R * channel(rgb[0]) + LUMINANCE_G * channel(rgb[1]) + LUMINANCE_B * channel(rgb[2])
    }

    /** Parses "#RGB" or "#RRGGBB" into an [IntArray] of [r, g, b]. Returns an empty array when the input is invalid. */
    private fun parseHexRgb(hex: String): IntArray {
        if (!hex.startsWith("#")) return intArrayOf()
        val h = hex.substring(1)
        val expanded =
            when (h.length) {
                SHORT_HEX_LEN -> "${h[0]}${h[0]}${h[1]}${h[1]}${h[2]}${h[2]}"
                FULL_HEX_LEN -> h
                else -> return intArrayOf()
            }
        return try {
            intArrayOf(
                expanded.substring(0, HEX_PAIR_1_END).toInt(HEX_RADIX),
                expanded.substring(HEX_PAIR_2_START, HEX_PAIR_2_END).toInt(HEX_RADIX),
                expanded.substring(HEX_PAIR_3_START, HEX_PAIR_3_END).toInt(HEX_RADIX),
            )
        } catch (_: NumberFormatException) {
            intArrayOf()
        }
    }

    private fun hex2(value: Int): String = value.toString(HEX_RADIX).padStart(HEX_PAIR_WIDTH, '0')
}
