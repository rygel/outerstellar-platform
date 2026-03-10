package dev.outerstellar.starter.swing

import java.awt.Rectangle
import java.util.prefs.Preferences
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.swing.DesktopStateProvider")

data class DesktopState(
    val windowBounds: Rectangle,
    val isMaximized: Boolean,
    val lastSearchQuery: String? = null,
    val themeId: String? = null,
    val language: String? = null
)

object DesktopStateProvider {
    private val prefs = Preferences.userNodeForPackage(DesktopStateProvider::class.java)

    fun saveState(state: DesktopState) {
        try {
            prefs.putInt("window_x", state.windowBounds.x)
            prefs.putInt("window_y", state.windowBounds.y)
            prefs.putInt("window_width", state.windowBounds.width)
            prefs.putInt("window_height", state.windowBounds.height)
            prefs.putBoolean("is_maximized", state.isMaximized)
            state.lastSearchQuery?.let { prefs.put("last_search", it) } ?: prefs.remove("last_search")
            state.themeId?.let { prefs.put("theme_id", it) } ?: prefs.remove("theme_id")
            state.language?.let { prefs.put("language", it) } ?: prefs.remove("language")
            prefs.flush()
        } catch (e: Exception) {
            logger.error("Failed to save desktop state", e)
        }
    }

    fun loadState(): DesktopState? {
        val x = prefs.getInt("window_x", -1)
        if (x == -1) {
            // Even if bounds aren't saved, we might have theme/lang saved
            val themeId = prefs.get("theme_id", null)
            val language = prefs.get("language", null)
            if (themeId == null && language == null) return null
        }

        val y = prefs.getInt("window_y", 0)
        val width = prefs.getInt("window_width", 960)
        val height = prefs.getInt("window_height", 700)
        val isMaximized = prefs.getBoolean("is_maximized", false)
        val lastSearch = prefs.get("last_search", null)
        val themeId = prefs.get("theme_id", "dark")
        val language = prefs.get("language", "en")

        return DesktopState(
            windowBounds = Rectangle(if (x == -1) 100 else x, y, width, height),
            isMaximized = isMaximized,
            lastSearchQuery = lastSearch,
            themeId = themeId,
            language = language
        )
    }
}
