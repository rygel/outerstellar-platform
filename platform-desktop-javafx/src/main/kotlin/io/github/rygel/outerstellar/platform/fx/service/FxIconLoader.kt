package io.github.rygel.outerstellar.platform.fx.service

import javafx.scene.image.Image
import javafx.scene.image.ImageView
import org.slf4j.LoggerFactory

object FxIconLoader {
    private val logger = LoggerFactory.getLogger(FxIconLoader::class.java)
    private val cache = mutableMapOf<String, Image>()

    fun get(iconName: String, size: Int = 16): ImageView {
        val image =
            cache.getOrPut(iconName) {
                val resource = javaClass.getResource("/icons/${iconName}.png")
                if (resource != null) {
                    Image(resource.toExternalForm())
                } else {
                    logger.warn("Icon not found: /icons/{}.png", iconName)
                    Image(javaClass.getResource("/icons/placeholder.png")?.toExternalForm() ?: "")
                }
            }
        return ImageView(image).apply {
            fitWidth = size.toDouble()
            fitHeight = size.toDouble()
        }
    }
}
