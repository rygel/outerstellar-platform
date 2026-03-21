package io.github.rygel.outerstellar.platform.export

interface ExportProvider {
    val entityType: String
    val displayName: String

    fun exportCsv(): String

    fun headers(): List<String>
}

object CsvUtils {
    fun escapeCsv(value: String?): String {
        if (value == null) return ""
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    fun toCsvRow(values: List<String?>): String = values.joinToString(",") { escapeCsv(it) }
}
