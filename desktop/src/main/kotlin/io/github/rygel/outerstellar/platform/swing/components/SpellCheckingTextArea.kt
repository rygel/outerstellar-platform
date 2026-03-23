package io.github.rygel.outerstellar.platform.swing.components

import io.github.rygel.outerstellar.platform.swing.util.DialogUtil
import io.github.rygel.outerstellar.platform.swing.util.SpellChecker
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPopupMenu
import javax.swing.JTextArea
import javax.swing.UIManager
import javax.swing.border.CompoundBorder
import javax.swing.border.LineBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * A [JTextArea] with integrated spell-checking. Highlights the border in red when unknown words are present and
 * provides a context menu to add words to the user dictionary or trigger a full check.
 */
class SpellCheckingTextArea(rows: Int, columns: Int) : JTextArea(rows, columns) {

    private val spellChecker = SpellChecker.getInstance()

    init {
        setupSpellChecking()
    }

    private fun setupSpellChecking() {
        if (!spellChecker.isInitialized) return

        componentPopupMenu =
            JPopupMenu().apply {
                add(JMenuItem("Add to Dictionary").apply { addActionListener { addSelectedWordToDictionary() } })
                add(JMenuItem("Check Spelling").apply { addActionListener { checkSpelling() } })
            }

        document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = checkSpelling()

                override fun removeUpdate(e: DocumentEvent) = checkSpelling()

                override fun changedUpdate(e: DocumentEvent) = checkSpelling()
            }
        )
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
            val display = unknownWords.take(MAX_TOOLTIP_WORDS).joinToString("<br/>")
            val extra =
                if (unknownWords.size > MAX_TOOLTIP_WORDS) {
                    "<br/>... and ${unknownWords.size - MAX_TOOLTIP_WORDS} more"
                } else {
                    ""
                }
            toolTipText = "<html>Unknown words:<br/>$display$extra<br/><br/>Right-click for options</html>"
        } else {
            border = NORMAL_BORDER
            toolTipText = null
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

    fun showSpellCheckDialog() {
        if (!spellChecker.isInitialized) {
            DialogUtil.showWarning(this, "Spell checker not initialized.", "Spell Check")
            return
        }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            DialogUtil.showInfo(this, "No text to check.", "Spell Check")
            return
        }

        val unknownWords = spellChecker.findUnknownWords(trimmed.split("\\s+".toRegex()))

        if (unknownWords.isNotEmpty()) {
            val message = spellChecker.getSpellCheckMessage(unknownWords)
            if (message != null) {
                val option = DialogUtil.showConfirmYesNo(this, message, "Spell Check")
                if (option == JOptionPane.YES_OPTION) {
                    spellChecker.addToUserDictionary(unknownWords)
                    checkSpelling()
                }
            }
        } else {
            DialogUtil.showInfo(this, "No spelling errors found!", "Spell Check")
        }
    }

    companion object {
        private const val MAX_TOOLTIP_WORDS = 10

        private val NORMAL_BORDER = BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true)

        private val ERROR_BORDER = CompoundBorder(LineBorder(Color.RED, 2), BorderFactory.createEmptyBorder(2, 2, 2, 2))
    }
}
