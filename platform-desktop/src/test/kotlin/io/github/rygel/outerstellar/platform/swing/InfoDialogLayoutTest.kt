package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine
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
    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)

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

    @Test
    fun `info dialog action button is anchored below content`() {
        val i18n = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }
        val engine = DesktopSyncEngine(syncService, messageService, null, NoOpAnalyticsService())
        val viewModel = SyncViewModel(engine, i18n)
        val window = runOnEdtResult { SyncWindow(viewModel, ThemeManager(), i18n) }

        runOnEdt { window.configureForTest() }
        val dialog = runOnEdtResult { window.buildInfoDialog("About", "Some message text", null) }
        runOnEdt {
            dialog.pack() // force MigLayout to size and position all children
            dialog.validate()
        }

        runOnEdt {
            val messageArea = findByName<JTextArea>(dialog, "infoDialogMessageArea")
            val closeButton = findByName<JButton>(dialog, "infoDialogCloseButton")
            val pane = dialog.contentPane

            // Convert component positions to dialog content-pane coordinates so we compare
            // the same coordinate space (components live in different nested panels).
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
