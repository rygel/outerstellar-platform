package io.github.rygel.outerstellar.platform.fx.viewmodel

import javafx.concurrent.Task

fun <T> Task<T>.runInBackground(): Task<T> {
    val thread = Thread(this).also { it.isDaemon = true }
    thread.start()
    return this
}
