package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.sync.engine.module.ModuleNotifier
import java.awt.AWTException
import java.awt.SystemTray
import java.awt.TrayIcon
import javax.swing.ImageIcon
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.swing.SystemTrayNotifier")

class SystemTrayNotifier(private val i18nService: I18nService) : ModuleNotifier {
    private val trayIcon: TrayIcon? by lazy {
        if (!SystemTray.isSupported()) {
            logger.warn("SystemTray is not supported")
            null
        } else {
            val tray = SystemTray.getSystemTray()
            val image =
                SystemTrayNotifier::class.java.getResource("/icon.png")?.let { ImageIcon(it).image }
                    ?: return@lazy run {
                        logger.error("System tray icon resource is missing: /icon.png")
                        null
                    }

            val icon = TrayIcon(image, i18nService.translate("swing.app.title"))
            icon.isImageAutoSize = true
            try {
                tray.add(icon)
                icon
            } catch (e: AWTException) {
                logger.error("Failed to add TrayIcon: {}", e.message)
                null
            }
        }
    }

    override fun notifySuccess(message: String) {
        trayIcon?.displayMessage(
            i18nService.translate("swing.notification.success.title"),
            message,
            TrayIcon.MessageType.INFO,
        )
    }

    override fun notifyFailure(message: String) {
        trayIcon?.displayMessage(
            i18nService.translate("swing.notification.failure.title"),
            message,
            TrayIcon.MessageType.ERROR,
        )
    }
}
