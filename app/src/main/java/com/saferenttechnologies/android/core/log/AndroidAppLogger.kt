package com.saferenttechnologies.android.core.log

import android.util.Log
import com.saferenttechnologies.android.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/** Logcat logger — debug builds only. Long messages are split (Logcat cuts ~4000 chars). */
@Singleton
class AndroidAppLogger @Inject constructor() : AppLogger {

    override val isEnabled: Boolean = BuildConfig.DEBUG

    override fun d(tag: String, message: String) {
        if (!isEnabled) return
        message.chunkedByLines().forEach { Log.d(tag, it) }
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (!isEnabled) return
        Log.w(tag, message, throwable)
    }

    private fun String.chunkedByLines(max: Int = 3500): List<String> {
        if (length <= max) return listOf(this)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (line in lines()) {
            if (current.length + line.length + 1 > max && current.isNotEmpty()) {
                chunks += current.toString()
                current.clear()
            }
            current.append(line.take(max)).append('\n')
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }
}
