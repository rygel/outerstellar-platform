package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.fx.service.FxStateProvider
import io.github.rygel.outerstellar.platform.fx.service.FxTheme
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import java.util.Locale
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SettingsController : KoinComponent {

    private val themeManager: FxThemeManager by inject()
    private val i18nService: I18nService by inject()

    fun showAndWait() {
        val stage = Stage()
        stage.initModality(Modality.APPLICATION_MODAL)
        stage.title = "Settings"

        val languages = listOf("en" to "English", "fr" to "French")
        val themes = FxTheme.entries.sortedBy { it.label }

        val themeCombo = ComboBox<String>()
        themeCombo.items.setAll(themes.map { it.label })
        val currentThemeName = themeManager.currentThemeName()
        themeCombo.selectionModel.select(themes.indexOfFirst { it.label == currentThemeName }.coerceAtLeast(0))

        val langCombo = ComboBox<String>()
        langCombo.items.setAll(languages.map { it.second })
        langCombo.selectionModel.select(
            languages.indexOfFirst { it.first == Locale.getDefault().language }.coerceAtLeast(0)
        )

        val sampleLabel = Label("Sample Label")
        val sampleField = TextField("Sample text")
        val sampleButton = Button("Sample Button")
        val previewPanel = VBox(10.0, sampleLabel, sampleField, sampleButton)

        val applyBtn = Button("Apply")
        val cancelBtn = Button("Cancel")

        themeCombo.selectionModel.selectedItemProperty().addListener { _, _, _ ->
            val selectedTheme = themes.getOrNull(themeCombo.selectionModel.selectedIndex)
            if (selectedTheme != null) {
                Application.setUserAgentStylesheet(selectedTheme.atlantafx.userAgentStylesheet)
            }
        }

        applyBtn.setOnAction {
            val selectedTheme = themes.getOrNull(themeCombo.selectionModel.selectedIndex) ?: return@setOnAction
            themeManager.applyTheme(selectedTheme)

            val selectedLang = languages.getOrNull(langCombo.selectionModel.selectedIndex)?.first ?: return@setOnAction
            val newLocale = Locale.of(selectedLang)
            Locale.setDefault(newLocale)
            i18nService.setLocale(newLocale)

            val currentState = FxStateProvider.loadState()
            if (currentState != null) {
                FxStateProvider.saveState(currentState.copy(themeId = selectedTheme.label, language = selectedLang))
            }

            stage.close()
        }

        cancelBtn.setOnAction { stage.close() }

        val buttonBar = HBox(10.0, applyBtn, cancelBtn)
        val root = VBox(10.0, Label("Theme:"), themeCombo, Label("Language:"), langCombo, previewPanel, buttonBar)

        val scene = Scene(root)
        themeManager.setScene(scene)
        stage.scene = scene
        stage.showAndWait()
    }
}
