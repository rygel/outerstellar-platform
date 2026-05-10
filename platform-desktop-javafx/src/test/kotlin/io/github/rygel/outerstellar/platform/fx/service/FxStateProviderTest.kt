package io.github.rygel.outerstellar.platform.fx.service

import java.util.prefs.Preferences
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FxStateProviderTest {

    private fun clearFxPrefs() {
        val field = FxStateProvider::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        val fxPrefs = field.get(FxStateProvider) as Preferences
        fxPrefs.clear()
        fxPrefs.flush()
    }

    @BeforeEach
    fun cleanPrefs() {
        clearFxPrefs()
    }

    @AfterEach
    fun tearDown() {
        clearFxPrefs()
    }

    @Test
    fun `loadState returns null when no state has been saved`() {
        assertThat(FxStateProvider.loadState()).isNull()
    }

    @Test
    fun `default FxWindowState has expected values`() {
        val state = FxWindowState()
        assertThat(state.x).isEqualTo(100.0)
        assertThat(state.y).isEqualTo(100.0)
        assertThat(state.width).isEqualTo(1200.0)
        assertThat(state.height).isEqualTo(800.0)
        assertThat(state.maximized).isFalse()
        assertThat(state.lastSearchQuery).isNull()
        assertThat(state.themeId).isNull()
        assertThat(state.language).isNull()
    }

    @Test
    fun `saveState and loadState round-trip correctly`() {
        val state =
            FxWindowState(
                x = 200.0,
                y = 150.0,
                width = 1400.0,
                height = 900.0,
                maximized = true,
                lastSearchQuery = "test query",
                themeId = "Darcula",
                language = "fr",
            )
        FxStateProvider.saveState(state)
        val loaded = FxStateProvider.loadState()

        assertThat(loaded).isNotNull
        assertThat(loaded!!.x).isEqualTo(200.0)
        assertThat(loaded.y).isEqualTo(150.0)
        assertThat(loaded.width).isEqualTo(1400.0)
        assertThat(loaded.height).isEqualTo(900.0)
        assertThat(loaded.maximized).isTrue()
        assertThat(loaded.lastSearchQuery).isEqualTo("test query")
        assertThat(loaded.themeId).isEqualTo("Darcula")
        assertThat(loaded.language).isEqualTo("fr")
    }

    @Test
    fun `saveState handles null optional fields`() {
        val state =
            FxWindowState(
                x = 100.0,
                y = 100.0,
                width = 1200.0,
                height = 800.0,
                maximized = false,
                lastSearchQuery = null,
                themeId = null,
                language = null,
            )
        FxStateProvider.saveState(state)
        val loaded = FxStateProvider.loadState()

        assertThat(loaded).isNotNull
        assertThat(loaded!!.lastSearchQuery).isNull()
        assertThat(loaded.themeId).isEqualTo("dark")
        assertThat(loaded.language).isEqualTo("en")
    }

    @Test
    fun `saveState overwrites previous state`() {
        val first = FxWindowState(x = 50.0, y = 50.0, width = 800.0, height = 600.0, themeId = "Dark")
        FxStateProvider.saveState(first)

        val second = FxWindowState(x = 300.0, y = 200.0, width = 1920.0, height = 1080.0, themeId = "Light")
        FxStateProvider.saveState(second)

        val loaded = FxStateProvider.loadState()
        assertThat(loaded).isNotNull
        assertThat(loaded!!.x).isEqualTo(300.0)
        assertThat(loaded.width).isEqualTo(1920.0)
        assertThat(loaded.themeId).isEqualTo("Light")
    }

    @Test
    fun `FxWindowState data class copy works correctly`() {
        val original = FxWindowState()
        val modified = original.copy(width = 1600.0, height = 1000.0, themeId = "macOS Dark")

        assertThat(modified.width).isEqualTo(1600.0)
        assertThat(modified.height).isEqualTo(1000.0)
        assertThat(modified.themeId).isEqualTo("macOS Dark")
        assertThat(modified.x).isEqualTo(original.x)
        assertThat(modified.y).isEqualTo(original.y)
    }
}
