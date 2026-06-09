package io.github.rygel.outerstellar.platform.web.assembly

import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import org.http4k.routing.ResourceLoader

internal class FilesystemFirstResourceLoader(directory: Path, private val fallback: ResourceLoader) : ResourceLoader {
    private val root = directory.toAbsolutePath().normalize()

    override fun load(path: String): URL? {
        val relativeName = path.trimStart('/')
        val candidate = root.resolve(relativeName).normalize()
        if (candidate.startsWith(root) && Files.isRegularFile(candidate)) {
            return candidate.toUri().toURL()
        }
        return fallback.load(path)
    }
}
