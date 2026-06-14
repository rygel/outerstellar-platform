package io.github.rygel.outerstellar.platform.web

object PathPatternMatcher {

    private val regexSpecial = setOf('.', '+', '?', '^', '$', '{', '}', '[', ']', '(', ')', '|', '\\', '-', '/')

    fun matches(pattern: String, path: String): Boolean {
        val patternSegments = pattern.split("/").filter { it.isNotEmpty() }
        val pathSegments = path.split("/").filter { it.isNotEmpty() }
        return matchSegments(patternSegments, 0, pathSegments, 0)
    }

    private fun matchSegments(pattern: List<String>, pi: Int, path: List<String>, si: Int): Boolean {
        if (pi >= pattern.size) return si >= path.size

        val segment = pattern[pi]

        if (segment == "**") {
            if (pi == pattern.size - 1) return true
            for (skip in si..path.size) {
                if (matchSegments(pattern, pi + 1, path, skip)) return true
            }
            return false
        }

        if (si >= path.size) return false

        if (matchSegment(segment, path[si])) {
            return matchSegments(pattern, pi + 1, path, si + 1)
        }

        return false
    }

    private fun matchSegment(pattern: String, segment: String): Boolean {
        if (pattern == "*") return true
        if (!pattern.contains("*")) return pattern == segment
        val regex = StringBuilder()
        for (c in pattern) {
            when {
                c == '*' -> regex.append(".*")
                c in regexSpecial -> regex.append("\\").append(c)
                else -> regex.append(c)
            }
        }
        return regex.toString().toRegex().matches(segment)
    }
}
