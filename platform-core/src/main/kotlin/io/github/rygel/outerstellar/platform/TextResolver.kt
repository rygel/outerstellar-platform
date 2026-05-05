package io.github.rygel.outerstellar.platform

import java.io.File
import java.io.FileInputStream
import java.util.Properties

interface TextResolver {
    fun resolve(key: String, vararg args: Any?): String
}

class DefaultTextResolver(private val texts: Map<String, String>) : TextResolver {

    override fun resolve(key: String, vararg args: Any?): String {
        val template = texts[key] ?: return key
        return if (args.isEmpty()) template else String.format(template, *args)
    }

    companion object {
        fun fromClasspath(resource: String = "texts.properties"): DefaultTextResolver {
            val props = Properties()
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resource)
            if (stream != null) {
                try {
                    props.load(stream)
                } finally {
                    stream.close()
                }
            }
            val map = props.stringPropertyNames().associateWith { props.getProperty(it) }
            return DefaultTextResolver(map)
        }

        fun fromFile(path: String): DefaultTextResolver {
            val props = Properties()
            val file = File(path)
            if (file.exists()) {
                val stream = FileInputStream(file)
                try {
                    props.load(stream)
                } finally {
                    stream.close()
                }
            }
            val map = props.stringPropertyNames().associateWith { props.getProperty(it) }
            return DefaultTextResolver(map)
        }
    }
}
