package io.github.rygel.outerstellar.platform.export

interface ExportProvider {
    val entityType: String
    val displayName: String

    fun exportCsv(): String

    fun exportJson(): String

    fun headers(): List<String>
}

object CsvUtils {
    private val DANGEROUS_PREFIXES = charArrayOf('=', '+', '-', '@', '\t', '\r')

    fun escapeCsv(value: String?): String {
        if (value == null) return ""
        val prefixed = if (value.isNotEmpty() && value[0] in DANGEROUS_PREFIXES) "'$value" else value
        val escaped = prefixed.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    fun toCsvRow(values: List<String?>): String = values.joinToString(",") { escapeCsv(it) }
}
