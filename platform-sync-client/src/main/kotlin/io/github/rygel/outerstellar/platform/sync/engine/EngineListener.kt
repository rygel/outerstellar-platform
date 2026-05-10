package io.github.rygel.outerstellar.platform.sync.engine

interface EngineListener {
    fun onStateChanged(newState: EngineState) {}

    fun onSessionExpired() {}

    fun onError(operation: String, message: String) {}
}

interface EngineNotifier {
    fun notifySuccess(message: String)

    fun notifyFailure(message: String)
}
