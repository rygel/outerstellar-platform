package io.github.rygel.outerstellar.platform.fx.service

import io.github.rygel.outerstellar.platform.sync.engine.module.ModuleNotifier
import java.awt.AWTException
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import org.slf4j.LoggerFactory

object FxTrayNotifier : ModuleNotifier {
    private val logger = LoggerFactory.getLogger(FxTrayNotifier::class.java)
    private var trayIcon: TrayIcon? = null

    fun init() {
        if (!SystemTray.isSupported()) {
            logger.warn("System tray is not supported on this platform")
            return
        }
        val resource =
            javaClass.getResource("/icons/tray.png")
                ?: return logger.error("System tray icon resource is missing: /icons/tray.png")
        val image = Toolkit.getDefaultToolkit().getImage(resource)
        val icon = TrayIcon(image, "Outerstellar").apply { isImageAutoSize = true }
        try {
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        } catch (e: AWTException) {
            logger.warn("Failed to add tray icon: {}", e.message, e)
        }
    }

    fun notify(title: String, message: String) {
        trayIcon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
            ?: logger.warn("Tray icon not initialized; cannot display notification: {} - {}", title, message)
    }

    override fun notifySuccess(message: String) {
        notify("Outerstellar", message)
    }

    override fun notifyFailure(message: String) {
        notify("Outerstellar", message)
    }

    fun dispose() {
        trayIcon?.let {
            SystemTray.getSystemTray().remove(it)
            trayIcon = null
        }
    }
}
