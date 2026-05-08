package io.github.rygel.outerstellar.platform.fx.service

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import org.slf4j.LoggerFactory

object FxTrayNotifier {
    private val logger = LoggerFactory.getLogger(FxTrayNotifier::class.java)
    private var trayIcon: TrayIcon? = null

    fun init() {
        if (!SystemTray.isSupported()) {
            logger.warn("System tray is not supported on this platform")
            return
        }
        val image = Toolkit.getDefaultToolkit().getImage(javaClass.getResource("/icons/tray.png"))
        trayIcon = TrayIcon(image, "Outerstellar").apply { isImageAutoSize = true }
        val icon = trayIcon
        if (icon != null) {
            runCatching { SystemTray.getSystemTray().add(icon) }
                .onFailure { logger.warn("Failed to add tray icon: {}", it.message) }
        }
    }

    fun notify(title: String, message: String) {
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
            ?: logger.warn("Tray icon not initialized; cannot display notification: {} - {}", title, message)
    }

    fun dispose() {
        trayIcon?.let {
            runCatching { SystemTray.getSystemTray().remove(it) }
                .onFailure { logger.warn("Failed to remove tray icon: {}", it.message) }
            trayIcon = null
        }
    }
}
