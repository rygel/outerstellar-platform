package io.github.rygel.outerstellar.platform.swing.util

import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

/**
 * Simple dictionary-based spell checker.
 * Loads a main dictionary (`dictionary_en.txt`) and a user dictionary (`user_dictionary.txt`)
 * from the classpath, then provides word-level validation and user-dictionary management.
 */
class SpellChecker private constructor() {

    private val dictionary = mutableSetOf<String>()
    private val userDictionary = mutableSetOf<String>()

    init {
        try {
            loadDictionary("dictionary_en.txt", dictionary)
            loadDictionary("user_dictionary.txt", userDictionary)
            logger.info("Spell checker initialized with {} words", dictionary.size)
        } catch (e: Exception) {
            logger.error("Failed to initialize spell checker", e)
        }
    }

    val isInitialized: Boolean get() = dictionary.isNotEmpty()

    fun isCorrect(word: String): Boolean {
        if (!isInitialized) return true
        val lower = word.lowercase()
        return lower in dictionary || lower in userDictionary
    }

    fun getSuggestions(word: String): List<String> {
        if (!isInitialized) return emptyList()
        val lower = word.lowercase()
        // Simple edit-distance-1 suggestions from the dictionary
        return dictionary.filter { editDistance(it, lower) == 1 }.take(MAX_SUGGESTIONS)
    }

    fun addWordToUserDictionary(word: String): Boolean {
        val normalized = word.lowercase().trim()
        if (normalized.isEmpty()) return false
        val added = userDictionary.add(normalized)
        if (added) logger.info("Added word to user dictionary: {}", normalized)
        return added
    }

    fun addToUserDictionary(words: List<String>) {
        words.forEach { addWordToUserDictionary(it) }
    }

    fun findUnknownWords(words: List<String>): List<String> {
        if (!isInitialized) return emptyList()
        return words.filter { w ->
            val trimmed = w.trim()
            trimmed.isNotEmpty() && !isCorrect(trimmed)
        }
    }

    fun getSpellCheckMessage(unknownWords: List<String>): String? {
        if (unknownWords.isEmpty()) return null
        val plural = if (unknownWords.size == 1) "" else "s"
        return buildString {
            append("Found ${unknownWords.size} unknown word$plural: ")
            val display = unknownWords.take(MAX_DISPLAY_WORDS)
            append(display.joinToString(", "))
            if (unknownWords.size > MAX_DISPLAY_WORDS) {
                append("... and ${unknownWords.size - MAX_DISPLAY_WORDS} more")
            }
            append("\n\nWould you like to add these words to your dictionary?")
        }
    }

    private fun loadDictionary(fileName: String, target: MutableSet<String>) {
        val stream = javaClass.classLoader.getResourceAsStream(fileName)
        if (stream != null) {
            stream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .forEach { target.add(it) }
            }
        } else {
            logger.warn("Dictionary file not found: {}", fileName)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SpellChecker::class.java)
        private const val MAX_SUGGESTIONS = 5
        private const val MAX_DISPLAY_WORDS = 5

        @Volatile
        private var instance: SpellChecker? = null

        @JvmStatic
        fun getInstance(): SpellChecker =
            instance ?: synchronized(this) {
                instance ?: SpellChecker().also { instance = it }
            }

        /** Simple Levenshtein edit distance. */
        private fun editDistance(a: String, b: String): Int {
            val m = a.length
            val n = b.length
            val dp = Array(m + 1) { IntArray(n + 1) }
            for (i in 0..m) dp[i][0] = i
            for (j in 0..n) dp[0][j] = j
            for (i in 1..m) {
                for (j in 1..n) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                }
            }
            return dp[m][n]
        }
    }
}
