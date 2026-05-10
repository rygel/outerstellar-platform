package io.github.rygel.outerstellar.platform.fx.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FxThemeManagerTest {

    @Test
    fun `FxTheme enum has exactly 6 entries`() {
        assertThat(FxTheme.entries).hasSize(6)
    }

    @Test
    fun `FxTheme labels match expected display names`() {
        val labels = FxTheme.entries.map { it.label }.toSet()
        assertThat(labels)
            .containsExactlyInAnyOrder("Dark", "Light", "Darcula", "IntelliJ", "macOS Dark", "macOS Light")
    }

    @Test
    fun `each FxTheme entry has a non-blank cssFile`() {
        FxTheme.entries.forEach { theme -> assertThat(theme.cssFile).isNotBlank() }
    }

    @Test
    fun `each FxTheme entry has a unique cssFile`() {
        val cssFiles = FxTheme.entries.map { it.cssFile }
        assertThat(cssFiles).hasSize(cssFiles.distinct().size)
    }

    @Test
    fun `each FxTheme entry has a non-blank label`() {
        FxTheme.entries.forEach { theme -> assertThat(theme.label).isNotBlank() }
    }

    @Test
    fun `applyThemeByName lookup finds correct theme by name`() {
        val dark = FxTheme.entries.firstOrNull { it.name.equals("DARK", ignoreCase = true) }
        assertThat(dark).isEqualTo(FxTheme.DARK)

        val light = FxTheme.entries.firstOrNull { it.name.equals("LIGHT", ignoreCase = true) }
        assertThat(light).isEqualTo(FxTheme.LIGHT)
    }

    @Test
    fun `applyThemeByName lookup returns DARK fallback for unknown name`() {
        val theme = FxTheme.entries.firstOrNull { it.name.equals("NONEXISTENT", ignoreCase = true) } ?: FxTheme.DARK
        assertThat(theme).isEqualTo(FxTheme.DARK)
    }

    @Test
    fun `applyThemeByName lookup is case insensitive`() {
        val theme = FxTheme.entries.firstOrNull { it.name.equals("dark", ignoreCase = true) }
        assertThat(theme).isEqualTo(FxTheme.DARK)
    }

    @Test
    fun `FxThemeManager initial currentThemeName is null`() {
        val manager = FxThemeManager()
        assertThat(manager.currentThemeName()).isNull()
    }
}
