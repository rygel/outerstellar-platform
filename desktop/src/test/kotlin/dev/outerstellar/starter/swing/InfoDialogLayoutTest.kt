package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import io.mockk.mockk
import java.util.Locale
import javax.swing.JButton
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
            dialog.setLocationRelativeTo(window.frame)
            dialog.doLayout()
            dialog.validate()
        }

        runOnEdt {
            val messageArea = findByName<JTextArea>(dialog, "infoDialogMessageArea")
            val closeButton = findByName<JButton>(dialog, "infoDialogCloseButton")

            assertTrue(
                closeButton.y > messageArea.y + (messageArea.height / 2),
                "Close button should be below message area",
            )
            assertTrue(
                closeButton.y + closeButton.height <= dialog.contentPane.height - 8,
                "Close button should sit near bottom",
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
