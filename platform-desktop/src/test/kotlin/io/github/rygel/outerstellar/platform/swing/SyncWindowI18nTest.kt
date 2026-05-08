package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.SyncService
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class SyncWindowI18nTest {
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
    fun `refreshTranslations updates primary window labels and menus`() {
        val en = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }
        val fr = I18nService.create("messages").also { it.setLocale(Locale.FRENCH) }
        val viewModel = SyncViewModel(messageService, null, syncService, en)
        lateinit var window: SyncWindow
        runOnEdt { window = SyncWindow(viewModel, ThemeManager(), en) }

        runOnEdt { window.configureForTest() }
        runOnEdt { window.refreshTranslations(fr) }

        runOnEdt {
            assertTrue(
                window.frame.title.startsWith(fr.translate("swing.app.title")),
                "Title should start with '${fr.translate("swing.app.title")}', got: ${window.frame.title}",
            )
            assertEquals(fr.translate("swing.label.search"), label(window.frame, "searchLabel").text)
            assertEquals(fr.translate("swing.label.author"), label(window.frame, "authorLabel").text)
            assertEquals(fr.translate("swing.button.sync"), button(window.frame, "syncButton"))
            assertEquals(fr.translate("swing.button.create"), button(window.frame, "createButton"))
            assertEquals(fr.translate("swing.menu.file"), menu(window.frame, "appMenu").text)
            assertEquals(fr.translate("swing.menu.help"), menu(window.frame, "helpMenu").text)
            assertEquals(fr.translate("swing.menu.settings"), menuItem(window.frame, "settingsItem"))
            assertEquals(fr.translate("swing.auth.login"), menuItem(window.frame, "loginItem"))
            assertEquals(fr.translate("swing.auth.logout.simple"), menuItem(window.frame, "logoutItem"))
            assertEquals(fr.translate("swing.auth.register"), menuItem(window.frame, "registerItem"))
            assertEquals(fr.translate("swing.statusbar.version", "dev"), label(window.frame, "statusMetaLabel").text)
        }
    }

    private fun label(root: Container, name: String): JLabel = find(root, name) as JLabel

    private fun menu(root: Container, name: String): JMenu = find(root, name) as JMenu

    private fun button(root: Container, name: String): String = (find(root, name) as AbstractButton).text

    private fun menuItem(root: Container, name: String): String = (find(root, name) as JMenuItem).text

    private fun find(root: Container, name: String): Component {
        val queue = ArrayDeque<Component>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if ((current as? JComponent)?.name == name) return current
            enqueueChildren(current, queue)
        }
        throw AssertionError("Component not found: $name")
    }

    private fun enqueueChildren(component: Component, queue: ArrayDeque<Component>) {
        if (component is JMenu) {
            for (i in 0 until component.itemCount) {
                component.getItem(i)?.let { queue.add(it) }
            }
        }
        if (component is Container) {
            component.components.forEach { queue.add(it) }
        }
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeAndWait(block)
        }
    }
}
