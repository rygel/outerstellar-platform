package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.fx.service.FxStateProvider
import io.github.rygel.outerstellar.platform.fx.service.FxTheme
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import java.util.Locale
import javafx.application.Application
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SettingsController : KoinComponent {

    private val themeManager: FxThemeManager by inject()
    private val i18nService: I18nService by inject()

    @FXML private lateinit var langCombo: ComboBox<String>
    @FXML private lateinit var themeCombo: ComboBox<String>
    @FXML private lateinit var previewPanel: VBox
    @FXML private lateinit var sampleLabel: Label
    @FXML private lateinit var sampleField: TextField
    @FXML private lateinit var sampleButton: Button

    private val languages = listOf("en" to "English", "fr" to "French")

    @FXML
    fun initialize() {
        langCombo.items.setAll(languages.map { it.second })
        val currentLangIndex = languages.indexOfFirst { it.first == Locale.getDefault().language }.coerceAtLeast(0)
        langCombo.selectionModel.select(currentLangIndex)

        val themes = FxTheme.entries.sortedBy { it.label }
        themeCombo.items.setAll(themes.map { it.label })
        val currentThemeName = themeManager.currentThemeName()
        val currentThemeIndex = themes.indexOfFirst { it.label == currentThemeName }.coerceAtLeast(0)
        themeCombo.selectionModel.select(currentThemeIndex)

        themeCombo.selectionModel.selectedItemProperty().addListener { _, _, _ -> updatePreview() }
        updatePreview()
    }

    @FXML
    fun onApply() {
        val themes = FxTheme.entries.sortedBy { it.label }
        val selectedTheme = themes.getOrNull(themeCombo.selectionModel.selectedIndex) ?: return
        themeManager.applyTheme(selectedTheme)

        val selectedLang = languages.getOrNull(langCombo.selectionModel.selectedIndex)?.first ?: return
        val newLocale = Locale.of(selectedLang)
        Locale.setDefault(newLocale)
        i18nService.setLocale(newLocale)

        val currentState = FxStateProvider.loadState()
        if (currentState != null) {
            FxStateProvider.saveState(currentState.copy(themeId = selectedTheme.label, language = selectedLang))
        }

        close()
    }

    @FXML
    fun onCancel() {
        close()
    }

    private fun updatePreview() {
        val themes = FxTheme.entries.sortedBy { it.label }
        val selectedTheme = themes.getOrNull(themeCombo.selectionModel.selectedIndex) ?: return
        Application.setUserAgentStylesheet(selectedTheme.atlantafx.userAgentStylesheet)
    }

    private fun close() {
        (langCombo.scene.window as? Stage)?.close()
    }
}
