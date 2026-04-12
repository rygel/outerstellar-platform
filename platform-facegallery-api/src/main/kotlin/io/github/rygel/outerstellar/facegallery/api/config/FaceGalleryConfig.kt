package io.github.rygel.outerstellar.facegallery.api.config

data class FaceGalleryConfig(
    val mlService: MlServiceConfig = MlServiceConfig(),
    val storageDir: String = "/tmp/facegallery",
)

data class MlServiceConfig(
    val host: String = "localhost",
    val port: Int = 65432,
    val baseUrl: String = "http://$host:$port",
)
