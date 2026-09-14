package com.argent.http

/**
 * Minimal logging seam — swap [sink] for your real logger (Napier, Timber, OSLog).
 * Kept as a mutable object so it can be configured once at startup without
 * threading a logger through every constructor.
 */
object Log {
    var enabled: Boolean = false
    var sink: (String) -> Unit = ::println

    fun d(message: String) = emit("D", message)
    fun w(message: String) = emit("W", message)
    fun e(message: String) = emit("E", message)

    private fun emit(level: String, message: String) {
        if (enabled) sink("[argent-http][$level] $message")
    }
}
