package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.mockk.mockk
import java.awt.GraphicsEnvironment
import java.util.Locale
import javax.swing.JButton
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class InfoDialogLayoutTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun assumeNotHeadless() {
            org.junit.jupiter.api.Assumptions.assumeFalse(
                GraphicsEnvironment.isHeadless(),
                "Test requires a display (SyncWindow creates JFrame)",
            )
        }
    }

    private fun createVm(i18n: I18nService): SyncViewModel {
        val authModule = mockk<AuthModule>(relaxed = true)
        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        val profileModule = mockk<ProfileModule>(relaxed = true)
        val adminModule = mockk<AdminModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)
        return SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18n)
    }

    @Test
    fun `info dialog action button is anchored below content`() {
        val i18n = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }
        val viewModel = createVm(i18n)
        val window = runOnEdtResult { SyncWindow(viewModel, ThemeManager(), i18n) }

        runOnEdt { window.configureForTest() }
        val dialog = runOnEdtResult { window.buildInfoDialog("About", "Some message text", null) }
        runOnEdt {
            dialog.pack()
            dialog.validate()
        }

        runOnEdt {
            val messageArea = findByName<JTextArea>(dialog, "infoDialogMessageArea")
            val closeButton = findByName<JButton>(dialog, "infoDialogCloseButton")
            val pane = dialog.contentPane

            val msgY = SwingUtilities.convertPoint(messageArea.parent, 0, messageArea.y, pane).y
            val btnY = SwingUtilities.convertPoint(closeButton.parent, 0, closeButton.y, pane).y

            assertTrue(
                btnY > msgY + (messageArea.height / 2),
                "Close button (pane-y=$btnY) should be below message area (pane-y=$msgY, h=${messageArea.height})",
            )
            assertTrue(
                btnY + closeButton.height <= pane.height - 8,
                "Close button should sit near bottom (btnY=$btnY, btnH=${closeButton.height}, paneH=${pane.height})",
            )
        }

        runOnEdt {
            dialog.dispose()
            window.frame.dispose()
        }
    }
}
