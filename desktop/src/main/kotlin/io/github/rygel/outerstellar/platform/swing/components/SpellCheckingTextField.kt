package io.github.rygel.outerstellar.platform.swing.components

import io.github.rygel.outerstellar.platform.swing.util.SpellChecker
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTextField
import javax.swing.UIManager
import javax.swing.border.CompoundBorder
import javax.swing.border.LineBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * A [JTextField] with integrated spell-checking.
 * Highlights the border in red when unknown words are present, provides a context
 * menu to add words to the user dictionary, and shows spelling suggestions on double-click.
 */
class SpellCheckingTextField(columns: Int) : JTextField(columns) {

    private val spellChecker = SpellChecker.getInstance()

    init {
        setupSpellChecking()
    }

    private fun setupSpellChecking() {
        if (!spellChecker.isInitialized) return

        componentPopupMenu = JPopupMenu().apply {
            add(JMenuItem("Add to Dictionary").apply {
                addActionListener { addSelectedWordToDictionary() }
            })
        }

        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = checkSpelling()
            override fun removeUpdate(e: DocumentEvent) = checkSpelling()
            override fun changedUpdate(e: DocumentEvent) = checkSpelling()
        })

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) checkWordAtCaret()
            }
        })
    }

    private fun checkSpelling() {
        if (!spellChecker.isInitialized) return

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            border = NORMAL_BORDER
            toolTipText = null
            return
        }

        val words = trimmed.split("\\s+".toRegex())
        val unknownWords = spellChecker.findUnknownWords(words)

        if (unknownWords.isNotEmpty()) {
            border = ERROR_BORDER
            toolTipText = "Unknown words: ${unknownWords.joinToString(", ")}"
        } else {
            border = NORMAL_BORDER
            toolTipText = null
        }
    }

    private fun checkWordAtCaret() {
        if (!spellChecker.isInitialized) return

        val caretPos = caretPosition
        val txt = text

        var start = caretPos
        var end = caretPos

        while (start > 0 && txt[start - 1].isLetterOrDigit()) start--
        while (end < txt.length && txt[end].isLetterOrDigit()) end++

        if (start < end) {
            val word = txt.substring(start, end)
            if (!spellChecker.isCorrect(word)) {
                val suggestions = spellChecker.getSuggestions(word)
                showSuggestions(word, suggestions, start, end)
            }
        }
    }

    private fun showSuggestions(word: String, suggestions: List<String>, start: Int, end: Int) {
        val popup = JPopupMenu()

        if (suggestions.isNotEmpty()) {
            suggestions.take(MAX_SUGGESTIONS).forEach { suggestion ->
                popup.add(JMenuItem(suggestion).apply {
                    addActionListener {
                        val txt = text
                        text = txt.substring(0, start) + suggestion + txt.substring(end)
                    }
                })
            }
            popup.addSeparator()
        }

        popup.add(JMenuItem("Add \"$word\" to dictionary").apply {
            addActionListener {
                spellChecker.addWordToUserDictionary(word)
                checkSpelling()
            }
        })

        caret.magicCaretPosition?.let { pos ->
            popup.show(this, pos.x, pos.y)
        }
    }

    private fun addSelectedWordToDictionary() {
        val selected = selectedText?.trim()
        if (!selected.isNullOrEmpty()) {
            spellChecker.addWordToUserDictionary(selected)
            checkSpelling()
        }
    }

    fun hasSpellingErrors(): Boolean {
        if (!spellChecker.isInitialized) return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return spellChecker.findUnknownWords(trimmed.split("\\s+".toRegex())).isNotEmpty()
    }

    companion object {
        private const val MAX_SUGGESTIONS = 5

        private val NORMAL_BORDER = UIManager.getBorder("TextField.border")

        private val ERROR_BORDER = CompoundBorder(
            LineBorder(Color.RED, 1),
            BorderFactory.createEmptyBorder(1, 1, 1, 1),
        )
    }
}
