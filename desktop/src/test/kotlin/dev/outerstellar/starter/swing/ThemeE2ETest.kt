package dev.outerstellar.starter.swing

import dev.outerstellar.starter.model.ThemeCatalog
import java.awt.Color
import java.awt.GraphicsEnvironment
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.UIManager
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test

class ThemeE2ETest {

    @Test
    fun `components should use colors from UIManager after theme application`() {
        val themeManager = ThemeManager()
        val darkTheme = ThemeCatalog.allThemes().first { it.id == "dark" }
        val lightTheme = ThemeCatalog.allThemes().first { it.id == "default" }

        val expectedDarkBg = Color.decode(darkTheme.colors.getValue("background"))
        val expectedDarkCompBg = Color.decode(darkTheme.colors.getValue("componentBackground"))

        val expectedLightBg = Color.decode(lightTheme.colors.getValue("background"))
        val expectedLightCompBg = Color.decode(lightTheme.colors.getValue("componentBackground"))

        // Apply dark theme
        GuiActionRunner.execute { themeManager.applyTheme(darkTheme) }

        val panel = GuiActionRunner.execute<JPanel> { JPanel() }!!
        val textField = GuiActionRunner.execute<JTextField> { JTextField() }!!
        val button = GuiActionRunner.execute<javax.swing.JButton> { javax.swing.JButton() }!!

        assertEquals(
            expectedDarkBg.rgb,
            panel.background.rgb,
            "Panel background should match dark theme",
        )
        assertEquals(
            expectedDarkCompBg.rgb,
            textField.background.rgb,
            "TextField background should match dark theme",
        )
        assertEquals(
            Color.decode(darkTheme.colors.getValue("foreground")).rgb,
            textField.foreground.rgb,
            "TextField foreground should match dark theme",
        )
        assertEquals(
            Color.decode(darkTheme.colors.getValue("accent")).rgb,
            button.background.rgb,
            "Button background should match accent",
        )
        assertEquals(
            Color.decode(darkTheme.colors.getValue("foreground")).rgb,
            button.foreground.rgb,
            "Button foreground should match foreground",
        )
        assertEquals(
            Color.decode(darkTheme.colors.getValue("success")).rgb,
            (UIManager.get("Theme.success") as Color).rgb,
        )
        assertEquals(
            Color.decode(darkTheme.colors.getValue("danger")).rgb,
            (UIManager.get("Theme.danger") as Color).rgb,
        )

        // Verify some new keys
        assertEquals(expectedDarkCompBg.rgb, (UIManager.get("TextArea.background") as Color).rgb)
        assertEquals(
            Color.decode(darkTheme.colors.getValue("selectionBackground")).rgb,
            (UIManager.get("List.selectionBackground") as Color).rgb,
        )

        // Apply light theme
        GuiActionRunner.execute { themeManager.applyTheme(lightTheme) }

        val panel2 = GuiActionRunner.execute<JPanel> { JPanel() }!!
        val textField2 = GuiActionRunner.execute<JTextField> { JTextField() }!!
        val button2 = GuiActionRunner.execute<javax.swing.JButton> { javax.swing.JButton() }!!

        assertEquals(
            expectedLightBg.rgb,
            panel2.background.rgb,
            "Panel background should match light theme",
        )
        assertEquals(
            expectedLightCompBg.rgb,
            textField2.background.rgb,
            "TextField background should match light theme",
        )
        assertEquals(
            Color.decode(lightTheme.colors.getValue("foreground")).rgb,
            textField2.foreground.rgb,
            "TextField foreground should match light theme",
        )
        assertEquals(
            Color.decode(lightTheme.colors.getValue("accent")).rgb,
            button2.background.rgb,
            "Button background should match accent",
        )
        assertEquals(
            Color.decode(lightTheme.colors.getValue("foreground")).rgb,
            button2.foreground.rgb,
            "Button foreground should match foreground",
        )
        assertEquals(
            Color.decode(lightTheme.colors.getValue("success")).rgb,
            (UIManager.get("Theme.success") as Color).rgb,
        )
        assertEquals(
            Color.decode(lightTheme.colors.getValue("danger")).rgb,
            (UIManager.get("Theme.danger") as Color).rgb,
        )

        // Verify some new keys in light theme
        assertEquals(expectedLightCompBg.rgb, (UIManager.get("ComboBox.background") as Color).rgb)
        assertEquals(
            Color.decode(lightTheme.colors.getValue("background")).rgb,
            (UIManager.get("List.selectionForeground") as Color).rgb,
        )
    }

    @Test
    fun `UIManager should update colors when theme is changed at runtime`() {
        val themeManager = ThemeManager()
        val darkTheme = ThemeCatalog.allThemes().first { it.id == "dark" }
        val lightTheme = ThemeCatalog.allThemes().first { it.id == "default" }

        val expectedDarkBg = Color.decode(darkTheme.colors.getValue("background"))
        val expectedLightBg = Color.decode(lightTheme.colors.getValue("background"))

        // Apply light theme
        GuiActionRunner.execute { themeManager.applyTheme(lightTheme) }
        assertEquals(expectedLightBg.rgb, (UIManager.get("Panel.background") as Color).rgb)

        // Apply dark theme
        GuiActionRunner.execute { themeManager.applyTheme(darkTheme) }
        assertEquals(expectedDarkBg.rgb, (UIManager.get("Panel.background") as Color).rgb)
    }

    @Test
    fun `settings dialog theme preview should update live when selection changes`() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Run with: mvn -Ptest-desktop verify")
        val themeManager = ThemeManager()
        val i18nService = com.outerstellar.i18n.I18nService.create("messages")
        val viewModel =
            io.mockk.mockk<dev.outerstellar.starter.swing.viewmodel.SyncViewModel>(relaxed = true)

        // Robot must be created BEFORE the frame so the robot's AWT hierarchy tracks it.
        val robot = org.assertj.swing.core.BasicRobot.robotWithNewAwtHierarchy()
        val syncWindow = GuiActionRunner.execute<SyncWindow> {
            SyncWindow(viewModel, themeManager, i18nService).also { it.configureForTest() }
        }!!
        val w = org.assertj.swing.fixture.FrameFixture(robot, syncWindow.frame)

        try {
            w.show()

            w.menuItem("settingsItem").click()
            val dialog = w.dialog("settingsDialog")
            val themeCombo = dialog.comboBox("themeCombo")

            // Select 'Dark'
            themeCombo.selectItem("Dark")
            val previewPanel = dialog.panel("previewPanel").target()
            val darkTheme = ThemeCatalog.allThemes().first { it.name == "Dark" }
            val expectedDarkBg = Color.decode(darkTheme.colors.getValue("background"))

            assertEquals(
                expectedDarkBg.rgb,
                previewPanel.background.rgb,
                "Preview panel should update to dark background",
            )

            // Select 'Default' (Light)
            themeCombo.selectItem("Default")
            val lightTheme = ThemeCatalog.allThemes().first { it.id == "default" }
            val expectedLightBg = Color.decode(lightTheme.colors.getValue("background"))

            assertEquals(
                expectedLightBg.rgb,
                previewPanel.background.rgb,
                "Preview panel should update to light background",
            )

            dialog.button("cancelButton").click()
        } finally {
            w.cleanUp()
            robot.cleanUp()
        }
    }
}
