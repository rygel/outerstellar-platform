package io.github.rygel.outerstellar.platform.swing

import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.SwingUtilities

inline fun <reified T> findByName(root: Container, name: String): T {
    val queue = ArrayDeque<Component>()
    queue.add(root)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current is JComponent && current.name == name && current is T) return current
        if (current is Container) current.components.forEach { queue.add(it) }
    }
    throw AssertionError("Component '$name' of type ${T::class.simpleName} not found")
}

fun runOnEdt(block: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
}

fun <T> runOnEdtResult(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var result: T? = null
    SwingUtilities.invokeAndWait { result = block() }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
