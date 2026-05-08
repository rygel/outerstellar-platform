package io.github.rygel.outerstellar.platform.fx.service

import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.fx.service.FxStateProvider")

private const val DEFAULT_X = 100.0
private const val DEFAULT_Y = 100.0
private const val DEFAULT_WIDTH = 1200.0
private const val DEFAULT_HEIGHT = 800.0
private const val NOT_SAVED_SENTINEL = -1.0

data class FxWindowState(
    val x: Double = DEFAULT_X,
    val y: Double = DEFAULT_Y,
    val width: Double = DEFAULT_WIDTH,
    val height: Double = DEFAULT_HEIGHT,
    val maximized: Boolean = false,
    val lastSearchQuery: String? = null,
    val themeId: String? = null,
    val language: String? = null,
)

object FxStateProvider {
    private val prefs = Preferences.userNodeForPackage(FxStateProvider::class.java)

    fun loadState(): FxWindowState? {
        val x = prefs.getDouble("window_x", NOT_SAVED_SENTINEL)
        if (x == NOT_SAVED_SENTINEL) {
            val themeId = prefs.get("theme_id", null)
            val language = prefs.get("language", null)
            if (themeId == null && language == null) return null
        }

        val y = prefs.getDouble("window_y", DEFAULT_Y)
        val width = prefs.getDouble("window_width", DEFAULT_WIDTH)
        val height = prefs.getDouble("window_height", DEFAULT_HEIGHT)
        val maximized = prefs.getBoolean("maximized", false)
        val lastSearch = prefs.get("last_search", null)
        val themeId = prefs.get("theme_id", "dark")
        val language = prefs.get("language", "en")

        return FxWindowState(
            x = if (x == NOT_SAVED_SENTINEL) DEFAULT_X else x,
            y = y,
            width = width,
            height = height,
            maximized = maximized,
            lastSearchQuery = lastSearch,
            themeId = themeId,
            language = language,
        )
    }

    fun saveState(state: FxWindowState) {
        try {
            prefs.putDouble("window_x", state.x)
            prefs.putDouble("window_y", state.y)
            prefs.putDouble("window_width", state.width)
            prefs.putDouble("window_height", state.height)
            prefs.putBoolean("maximized", state.maximized)
            state.lastSearchQuery?.let { prefs.put("last_search", it) } ?: prefs.remove("last_search")
            state.themeId?.let { prefs.put("theme_id", it) } ?: prefs.remove("theme_id")
            state.language?.let { prefs.put("language", it) } ?: prefs.remove("language")
            prefs.flush()
        } catch (e: BackingStoreException) {
            logger.error("Failed to save desktop state to backing store: {}", e.message)
        }
    }
}
