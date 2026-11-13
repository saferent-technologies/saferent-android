package com.saferenttechnologies.android.core.log

/** Logging abstraction so data/domain code stays testable on the JVM. */
interface AppLogger {
    /** False in release builds — skip building expensive log messages. */
    val isEnabled: Boolean
    fun d(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
}

/** Logs nothing. Used in unit tests. */
object NoOpLogger : AppLogger {
    override val isEnabled: Boolean = false
    override fun d(tag: String, message: String) = Unit
    override fun w(tag: String, message: String, throwable: Throwable?) = Unit
}
