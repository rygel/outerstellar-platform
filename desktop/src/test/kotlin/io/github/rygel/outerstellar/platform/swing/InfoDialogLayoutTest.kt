package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.i18n.I18nService
import io.mockk.mockk
import java.util.Locale
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InfoDialogLayoutTest {
    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)

    @Test
    fun `info dialog action button is anchored below content`() {
        val i18n = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }
        val viewModel = SyncViewModel(messageService, null, syncService, i18n)
        val window = SyncWindow(viewModel, ThemeManager(), i18n)

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

    private inline fun <reified T> findByName(root: java.awt.Container, name: String): T {
        val queue = ArrayDeque<java.awt.Component>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val compName = (current as? javax.swing.JComponent)?.name
            if (compName == name && current is T) return current
            if (current is java.awt.Container) current.components.forEach { queue.add(it) }
        }
        throw AssertionError("Component not found: $name")
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }

    private fun <T> runOnEdtResult(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: T? = null
        SwingUtilities.invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
