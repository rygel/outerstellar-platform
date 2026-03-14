package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import java.awt.AWTException
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.swing.SystemTrayNotifier")
private const val FALLBACK_IMAGE_SIZE = 16

class SystemTrayNotifier(private val i18nService: I18nService) {
    private val trayIcon: TrayIcon? by lazy {
        if (!SystemTray.isSupported()) {
            logger.warn("SystemTray is not supported")
            null
        } else {
            val tray = SystemTray.getSystemTray()
            val image =
                try {
                    // Try to load an icon if it exists, otherwise use a blank image or fallback
                    val resource = SystemTrayNotifier::class.java.getResource("/icon.png")
                    if (resource != null) {
                        ImageIcon(resource).image
                    } else {
                        BufferedImage(
                            FALLBACK_IMAGE_SIZE,
                            FALLBACK_IMAGE_SIZE,
                            BufferedImage.TYPE_INT_ARGB,
                        )
                    }
                } catch (e: IllegalArgumentException) {
                    logger.debug("Icon resource not found: {}", e.message)
                    BufferedImage(
                        FALLBACK_IMAGE_SIZE,
                        FALLBACK_IMAGE_SIZE,
                        BufferedImage.TYPE_INT_ARGB,
                    )
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

    fun notifySuccess(message: String) {
        trayIcon?.displayMessage(
            i18nService.translate("swing.notification.success.title"),
            message,
            TrayIcon.MessageType.INFO,
        )
    }

    fun notifyFailure(message: String) {
        trayIcon?.displayMessage(
            i18nService.translate("swing.notification.failure.title"),
            message,
            TrayIcon.MessageType.ERROR,
        )
    }
}
