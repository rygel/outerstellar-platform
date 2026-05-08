package io.github.rygel.outerstellar.platform.swing

import java.awt.GraphicsEnvironment
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.UIManager
import org.assertj.swing.edt.GuiActionRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ThemeE2ETest {

    @Test
    fun `dark theme should apply FlatLaf defaults to components`() {
        val themeManager = ThemeManager()

        GuiActionRunner.execute { themeManager.applyTheme(DesktopTheme.DARK) }

        val panel = GuiActionRunner.execute<JPanel> { JPanel() }!!
        val textField = GuiActionRunner.execute<JTextField> { JTextField() }!!

        assertEquals("Dark", UIManager.get("current_theme_name"))
        assertNotNull(panel.background)
        assertNotNull(textField.background)
        assertNotNull(textField.foreground)
    }

    @Test
    fun `light theme should apply FlatLaf defaults to components`() {
        val themeManager = ThemeManager()

        GuiActionRunner.execute { themeManager.applyTheme(DesktopTheme.LIGHT) }

        val panel = GuiActionRunner.execute<JPanel> { JPanel() }!!
        val textField = GuiActionRunner.execute<JTextField> { JTextField() }!!

        assertEquals("Light", UIManager.get("current_theme_name"))
        assertNotNull(panel.background)
        assertNotNull(textField.background)
        assertNotNull(textField.foreground)
    }

    @Test
    fun `UIManager should update colors when theme is changed at runtime`() {
        val themeManager = ThemeManager()

        GuiActionRunner.execute { themeManager.applyTheme(DesktopTheme.LIGHT) }
        assertEquals("Light", UIManager.get("current_theme_name"))

        GuiActionRunner.execute { themeManager.applyTheme(DesktopTheme.DARK) }
        assertEquals("Dark", UIManager.get("current_theme_name"))
    }

    @Test
    fun `settings dialog theme preview should update live when selection changes`() {
        org.junit.jupiter.api.Assumptions.assumeFalse(
            GraphicsEnvironment.isHeadless(),
            "Test requires a display (AssertJ Swing BasicRobot)",
        )

        val themeManager = ThemeManager()
        val i18nService = io.github.rygel.outerstellar.i18n.I18nService.create("messages")
        val viewModel =
            io.mockk.mockk<io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel>(relaxed = true)

        val robot = org.assertj.swing.core.BasicRobot.robotWithNewAwtHierarchy()
        val syncWindow =
            GuiActionRunner.execute<SyncWindow> {
                SyncWindow(viewModel, themeManager, i18nService).also { it.configureForTest() }
            }!!
        val w = org.assertj.swing.fixture.FrameFixture(robot, syncWindow.frame)

        try {
            w.show()

            w.menuItem("settingsItem").click()
            val dialog = w.dialog("settingsDialog")
            val themeCombo = dialog.comboBox("themeCombo")

            themeCombo.selectItem("Dark")
            val previewPanel = dialog.panel("previewPanel").target()
            assertNotNull(previewPanel.background, "Preview panel should have a background color")

            themeCombo.selectItem("Light")
            assertNotNull(previewPanel.background, "Preview panel should update to light background")

            dialog.button("cancelButton").click()
        } finally {
            w.cleanUp()
            robot.cleanUp()
        }
    }
}
