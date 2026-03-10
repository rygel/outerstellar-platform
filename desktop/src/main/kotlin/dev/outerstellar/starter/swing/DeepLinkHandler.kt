package dev.outerstellar.starter.swing

import java.awt.Desktop
import java.net.URI
import java.net.URISyntaxException
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.swing.DeepLinkHandler")

object DeepLinkHandler {
    private var onSearchRequested: ((String) -> Unit)? = null
    private var onSyncRequested: (() -> Unit)? = null

    fun setup(onSearch: (String) -> Unit, onSync: () -> Unit) {
        this.onSearchRequested = onSearch
        this.onSyncRequested = onSync

        try {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_OPEN_URI)) {
                desktop.setOpenURIHandler { event ->
                    handleUri(event.uri)
                }
                logger.info("DeepLinkHandler initialized for outerstellar://")
            } else {
                logger.warn("DeepLinkHandler: APP_OPEN_URI not supported on this platform")
            }
        } catch (e: UnsupportedOperationException) {
            logger.warn("DeepLink setup not supported: {}", e.message)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Unexpected error setting up DeepLinkHandler: {}", e.message)
        }
    }

    private fun handleUri(uri: URI) {
        logger.info("Handling deep link: $uri")
        if (uri.scheme == "outerstellar") {
            val action = uri.host ?: uri.path.trimStart('/')
            when {
                action.startsWith("search") -> {
                    val query = uri.query?.split('&')
                        ?.find { it.startsWith("q=") }
                        ?.substringAfter("q=")
                        ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    if (query != null) {
                        onSearchRequested?.invoke(query)
                    }
                }
                action == "sync" -> {
                    onSyncRequested?.invoke()
                }
            }
        }
    }

    /**
     * Fallback for platforms where APP_OPEN_URI handler doesn't work or for manual testing.
     */
    fun processRawArgs(args: Array<String>) {
        args.forEach { arg ->
            if (arg.startsWith("outerstellar://")) {
                try {
                    handleUri(URI(arg))
                } catch (e: URISyntaxException) {
                    logger.error("Failed to parse deep link arg {}: {}", arg, e.message)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    logger.error("Unexpected error processing deep link {}: {}", arg, e.message)
                }
            }
        }
    }
}
