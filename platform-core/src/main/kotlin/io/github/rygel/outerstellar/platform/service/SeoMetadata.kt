package io.github.rygel.outerstellar.platform.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class SeoMetadata(
    private val title: String,
    private val description: String,
    private val canonicalUrl: String,
    private val ogImage: String = "",
    private val locale: String = "en",
    private val noIndex: Boolean = false,
) {
    companion object {
        fun forPage(
            title: String,
            description: String,
            canonicalUrl: String,
            ogImage: String = "",
            locale: String = "en",
            robots: String = "index, follow",
        ) =
            SeoMetadata(
                title = title,
                description = description,
                canonicalUrl = canonicalUrl,
                ogImage = ogImage,
                locale = locale,
                noIndex = robots.startsWith("noindex"),
            )
    }

    fun generateAllMetaTags(): String = buildString {
        if (title.isNotBlank()) {
            appendLine("<meta property=\"og:title\" content=\"${escapeHtml(title)}\">")
            appendLine("<meta name=\"twitter:title\" content=\"${escapeHtml(title)}\">")
        }
        if (description.isNotBlank()) {
            appendLine("<meta property=\"og:description\" content=\"${escapeHtml(description)}\">")
            appendLine("<meta name=\"twitter:description\" content=\"${escapeHtml(description)}\">")
        }
        if (canonicalUrl.isNotBlank()) {
            appendLine("<meta property=\"og:url\" content=\"${escapeHtml(canonicalUrl)}\">")
        }
        if (ogImage.isNotBlank()) {
            appendLine("<meta property=\"og:image\" content=\"${escapeHtml(ogImage)}\">")
            appendLine("<meta name=\"twitter:image\" content=\"${escapeHtml(ogImage)}\">")
        }
        appendLine("<meta property=\"og:type\" content=\"website\">")
        appendLine("<meta name=\"twitter:card\" content=\"summary\">")
        appendLine("<meta property=\"og:locale\" content=\"${escapeHtml(locale)}\">")
        if (noIndex) {
            appendLine("<meta name=\"robots\" content=\"noindex, nofollow\">")
        }
        if (canonicalUrl.isNotBlank() && title.isNotBlank()) {
            appendLine("<script type=\"application/ld+json\">")
            appendLine("{")
            appendLine("  \"@context\": \"https://schema.org\",")
            appendLine("  \"@type\": \"WebSite\",")
            appendLine("  \"name\": ${Json.encodeToString(JsonPrimitive(title))},")
            appendLine("  \"url\": ${Json.encodeToString(JsonPrimitive(canonicalUrl))}")
            if (description.isNotBlank()) {
                appendLine("  ,\"description\": ${Json.encodeToString(JsonPrimitive(description))}")
            }
            appendLine("}")
            appendLine("</script>")
        }
    }

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("'", "&#39;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
