package io.github.rygel.outerstellar.platform.swing.util

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.UIManager
import javax.swing.border.EmptyBorder

/**
 * Utility for showing dialogs with copyable text.
 * All messages are displayed in [JTextArea]s that allow text selection and copying.
 */
object DialogUtil {

    /** Shows an information message dialog with copyable text. */
    @JvmStatic
    fun showInfo(parent: Component?, message: String, title: String) {
        showMessage(parent, message, title, JOptionPane.INFORMATION_MESSAGE)
    }

    /** Shows a warning message dialog with copyable text. */
    @JvmStatic
    fun showWarning(parent: Component?, message: String, title: String) {
        showMessage(parent, message, title, JOptionPane.WARNING_MESSAGE)
    }

    /** Shows an error message dialog with copyable text. */
    @JvmStatic
    fun showError(parent: Component?, message: String, title: String) {
        showMessage(parent, message, title, JOptionPane.ERROR_MESSAGE)
    }

    /** Shows a message dialog with copyable text and specified message type. */
    @JvmStatic
    @Suppress("UnusedParameter")
    fun showMessage(parent: Component?, message: String, title: String, messageType: Int) {
        val dialog = JDialog(getParentFrame(parent), title, true).apply {
            layout = BorderLayout(10, 10)
            size = Dimension(700, 400)
            setLocationRelativeTo(parent)
            isResizable = true
            minimumSize = Dimension(500, 200)
        }

        addEscapeKeyHandling(dialog)

        val panel = JPanel(BorderLayout(10, 10)).apply {
            border = EmptyBorder(15, 15, 15, 15)
            add(createCopyableMessagePane(message), BorderLayout.CENTER)
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val okButton = JButton("OK").apply {
            addActionListener { dialog.dispose() }
        }
        buttonPanel.add(okButton)

        dialog.add(panel, BorderLayout.CENTER)
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        dialog.rootPane.defaultButton = okButton
        dialog.isVisible = true
    }

    /** Shows a confirmation dialog with copyable text and YES/NO options. */
    @JvmStatic
    fun showConfirmYesNo(parent: Component?, message: String, title: String): Int =
        showConfirm(parent, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)

    /** Shows a confirmation dialog with copyable text and YES/NO options with warning icon. */
    @JvmStatic
    fun showConfirmYesNoWarning(parent: Component?, message: String, title: String): Int =
        showConfirm(parent, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)

    /** Shows a confirmation dialog with copyable text and OK/CANCEL options. */
    @JvmStatic
    fun showConfirmOkCancel(parent: Component?, message: String, title: String): Int =
        showConfirm(parent, message, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE)

    /** Shows a confirmation dialog with copyable text, specified option type and message type. */
    @JvmStatic
    @Suppress("UnusedParameter")
    fun showConfirm(
        parent: Component?,
        message: String,
        title: String,
        optionType: Int,
        messageType: Int = JOptionPane.QUESTION_MESSAGE,
    ): Int {
        val dialog = JDialog(getParentFrame(parent), title, true).apply {
            layout = BorderLayout(10, 10)
            size = Dimension(700, 400)
            setLocationRelativeTo(parent)
            isResizable = true
            minimumSize = Dimension(500, 200)
        }

        addEscapeKeyHandling(dialog)

        val panel = JPanel(BorderLayout(10, 10)).apply {
            border = EmptyBorder(15, 15, 15, 15)
            add(createCopyableMessagePane(message), BorderLayout.CENTER)
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val result = intArrayOf(JOptionPane.NO_OPTION)

        if (optionType == JOptionPane.YES_NO_OPTION || optionType == JOptionPane.YES_NO_CANCEL_OPTION) {
            val yesButton = JButton("Yes").apply {
                name = "Yes"
                addActionListener {
                    result[0] = JOptionPane.YES_OPTION
                    dialog.dispose()
                }
            }
            buttonPanel.add(yesButton)
            dialog.rootPane.defaultButton = yesButton
        }

        if (optionType == JOptionPane.OK_CANCEL_OPTION || optionType == JOptionPane.YES_NO_CANCEL_OPTION) {
            val okButton = JButton("OK").apply {
                name = "OK"
                addActionListener {
                    result[0] = JOptionPane.OK_OPTION
                    dialog.dispose()
                }
            }
            buttonPanel.add(okButton)
            if (optionType == JOptionPane.OK_CANCEL_OPTION) {
                dialog.rootPane.defaultButton = okButton
            }
        }

        val cancelButton = JButton("Cancel").apply {
            name = "Cancel"
            addActionListener { dialog.dispose() }
        }
        buttonPanel.add(cancelButton)

        if (optionType == JOptionPane.YES_NO_OPTION) {
            val noButton = JButton("No").apply {
                name = "No"
                addActionListener {
                    result[0] = JOptionPane.NO_OPTION
                    dialog.dispose()
                }
            }
            buttonPanel.add(noButton)
        }

        dialog.add(panel, BorderLayout.CENTER)
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        dialog.isVisible = true

        return result[0]
    }

    /** Shows a confirmation dialog with a custom component plus a copyable message area. */
    @JvmStatic
    @Suppress("UnusedParameter")
    fun showConfirmWithComponent(
        parent: Component?,
        message: String?,
        title: String,
        optionType: Int,
        messageType: Int,
        customComponent: JComponent?,
    ): Int {
        val dialog = JDialog(getParentFrame(parent), title, true).apply {
            layout = BorderLayout(10, 10)
            size = Dimension(700, 500)
            setLocationRelativeTo(parent)
            isResizable = true
            minimumSize = Dimension(600, 300)
        }

        addEscapeKeyHandling(dialog)

        val panel = JPanel(BorderLayout(0, 10)).apply {
            border = EmptyBorder(15, 15, 15, 15)
        }

        if (!message.isNullOrEmpty()) {
            panel.add(createCopyableMessagePane(message), BorderLayout.NORTH)
        }
        if (customComponent != null) {
            panel.add(customComponent, BorderLayout.CENTER)
        }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        val result = intArrayOf(JOptionPane.CANCEL_OPTION)

        if (optionType == JOptionPane.YES_NO_OPTION || optionType == JOptionPane.YES_NO_CANCEL_OPTION) {
            val yesButton = JButton("Yes").apply {
                addActionListener {
                    result[0] = JOptionPane.YES_OPTION
                    dialog.dispose()
                }
            }
            buttonPanel.add(yesButton)
            dialog.rootPane.defaultButton = yesButton
        }

        if (optionType == JOptionPane.OK_CANCEL_OPTION || optionType == JOptionPane.YES_NO_CANCEL_OPTION) {
            val okButton = JButton("OK").apply {
                addActionListener {
                    result[0] = JOptionPane.OK_OPTION
                    dialog.dispose()
                }
            }
            buttonPanel.add(okButton)
            if (optionType == JOptionPane.OK_CANCEL_OPTION) {
                dialog.rootPane.defaultButton = okButton
            }
        }

        val cancelButton = JButton("Cancel").apply {
            addActionListener { dialog.dispose() }
        }
        buttonPanel.add(cancelButton)

        if (optionType == JOptionPane.YES_NO_OPTION) {
            val noButton = JButton("No").apply {
                addActionListener {
                    result[0] = JOptionPane.NO_OPTION
                    dialog.dispose()
                }
            }
            buttonPanel.add(noButton)
        }

        dialog.add(panel, BorderLayout.CENTER)
        dialog.add(buttonPanel, BorderLayout.SOUTH)
        dialog.isVisible = true

        return result[0]
    }

    private fun addEscapeKeyHandling(dialog: JDialog) {
        dialog.rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "ESCAPE")
        dialog.rootPane.actionMap.put(
            "ESCAPE",
            object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent) {
                    dialog.dispose()
                }
            },
        )
    }

    private fun createCopyableMessagePane(message: String): JScrollPane {
        val textArea = JTextArea(message).apply {
            isEditable = false
            wrapStyleWord = true
            lineWrap = true
            isOpaque = true
            border = EmptyBorder(5, 5, 5, 5)
            font = UIManager.getFont("Label.font")
            foreground = UIManager.getColor("Label.foreground")
            background = UIManager.getColor("Panel.background")
            caretColor = foreground
            isFocusable = true
            isEnabled = true
            selectionColor = UIManager.getColor("textHighlight")
            selectedTextColor = UIManager.getColor("textHighlightText")
        }
        textArea.requestFocusInWindow()

        return JScrollPane(textArea).apply {
            border = EmptyBorder(5, 5, 5, 5)
            isOpaque = true
            preferredSize = Dimension(650, 300)
        }
    }

    private fun getParentFrame(component: Component?): Frame? =
        when (component) {
            null -> null
            is Frame -> component
            else -> getParentFrame(component.parent)
        }
}
