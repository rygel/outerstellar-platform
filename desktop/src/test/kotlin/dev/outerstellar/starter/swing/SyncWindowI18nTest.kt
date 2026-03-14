package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import io.mockk.mockk
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.util.Locale
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test

class SyncWindowI18nTest {
    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)

    @Test
    fun `refreshTranslations updates primary window labels and menus`() {
        assumeFalse(
            GraphicsEnvironment.isHeadless(),
            "Skipping Swing i18n UI test in headless mode",
        )

        val en = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }
        val fr = I18nService.create("messages").also { it.setLocale(Locale.FRENCH) }
        val viewModel = SyncViewModel(messageService, null, syncService, en)
        val window = SyncWindow(viewModel, ThemeManager(), en)

        runOnEdt { window.configureForTest() }
        runOnEdt { window.refreshTranslations(fr) }

        runOnEdt {
            assertEquals(fr.translate("swing.app.title"), window.frame.title)
            assertEquals(
                fr.translate("swing.label.search"),
                label(window.frame, "searchLabel").text,
            )
            assertEquals(
                fr.translate("swing.label.author"),
                label(window.frame, "authorLabel").text,
            )
            assertEquals(fr.translate("swing.button.sync"), button(window.frame, "syncButton"))
            assertEquals(fr.translate("swing.button.create"), button(window.frame, "createButton"))
            assertEquals(fr.translate("swing.menu.file"), menu(window.frame, "appMenu").text)
            assertEquals(fr.translate("swing.menu.help"), menu(window.frame, "helpMenu").text)
            assertEquals(
                fr.translate("swing.menu.settings"),
                menuItem(window.frame, "settingsItem"),
            )
            assertEquals(fr.translate("swing.auth.login"), menuItem(window.frame, "loginItem"))
            assertEquals(
                fr.translate("swing.auth.logout.simple"),
                menuItem(window.frame, "logoutItem"),
            )
            assertEquals(
                fr.translate("swing.auth.register"),
                menuItem(window.frame, "registerItem"),
            )
            assertEquals(
                fr.translate("swing.statusbar.version", "dev"),
                label(window.frame, "statusMetaLabel").text,
            )
        }
    }

    private fun label(root: Container, name: String): JLabel = find(root, name) as JLabel

    private fun menu(root: Container, name: String): JMenu = find(root, name) as JMenu

    private fun button(root: Container, name: String): String =
        (find(root, name) as AbstractButton).text

    private fun menuItem(root: Container, name: String): String =
        (find(root, name) as JMenuItem).text

    private fun find(root: Container, name: String): Component {
        val queue = ArrayDeque<Component>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val compName = (current as? JComponent)?.name
            if (compName == name) return current
            if (current is JMenu) {
                for (i in 0 until current.itemCount) {
                    current.getItem(i)?.let { queue.add(it) }
                }
            }
            if (current is Container) {
                current.components.forEach { queue.add(it) }
            }
        }
        throw AssertionError("Component not found: $name")
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeAndWait(block)
        }
    }
}
