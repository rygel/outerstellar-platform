package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JToolBar

class SyncStatusBar(
    private val i18nService: I18nService,
) {
    val statusLabel =
        JLabel(i18nService.translate("swing.status.ready")).apply {
            name = "statusLabel"
            toolTipText = i18nService.translate("swing.status.ready")
        }
    val offlineBadge =
        JLabel().apply {
            name = "offlineBadge"
            isVisible = false
            foreground = SyncDialogs.COLOR_DANGER
            font = font.deriveFont(Font.BOLD, 11f)
        }
    val statusHintLabel = JLabel().apply { name = "statusHintLabel" }
    val statusMetaLabel = JLabel().apply { name = "statusMetaLabel" }
    val statusBar =
        JToolBar().apply {
            name = "statusBarPanel"
            isFloatable = false
            isRollover = false
        }

    fun configure() {
        statusBar.removeAll()
        statusBar.add(statusLabel)
        statusBar.add(Box.createHorizontalGlue())
        statusBar.add(offlineBadge)
        statusBar.addSeparator(Dimension(10, 0))
        statusBar.add(statusHintLabel)
        statusBar.addSeparator(Dimension(10, 0))
        statusBar.add(statusMetaLabel)
    }
}
