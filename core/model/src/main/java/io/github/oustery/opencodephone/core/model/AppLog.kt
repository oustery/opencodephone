package io.github.oustery.opencodephone.core.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Application-wide diagnostic log.
 *
 * A bounded in-memory ring buffer every module can append to without any
 * Android dependency, mirrored to optional sinks (rotating file, Logcat)
 * installed by the app graph. The hidden diagnostics screen (Settings →
 * About → tap the version footer) tails the same buffer live, so a user can
 * capture what happened before reporting a bug — including the exact git
 * commands, bootstrap phases and server errors that message bars truncate.
 *
 * Appending is synchronized and cheap; entries carry wall-clock time so an
 * export stays readable without a shared clock reference.
 */
object AppLog {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timestampMs: Long,
        val level: Level,
        val tag: String,
        val message: String,
        /** Single-line `Throwable` summary, when one was attached. */
        val error: String? = null,
    )

    const val CAPACITY = 1500

    private val lock = Any()
    private val buffer = ArrayDeque<Entry>(CAPACITY)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())

    /** Latest-first view for UI tailing; oldest entries fall out at [CAPACITY]. */
    val entries: StateFlow<List<Entry>>
        @Suppress("MemberVisibilityCanBePrivate") get() = _entries

    /** Installed by the app graph; must never throw. */
    @Volatile
    var sink: ((Entry) -> Unit)? = null

    fun debug(tag: String, message: String, throwable: Throwable? = null) =
        append(Level.DEBUG, tag, message, throwable)

    fun info(tag: String, message: String, throwable: Throwable? = null) =
        append(Level.INFO, tag, message, throwable)

    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        append(Level.WARN, tag, message, throwable)

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        append(Level.ERROR, tag, message, throwable)

    private fun append(level: Level, tag: String, message: String, throwable: Throwable?) {
        val entry = Entry(
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag.take(24),
            message = message.take(600),
            error = throwable?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty().take(300)}" },
        )
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > CAPACITY) buffer.removeFirst()
            // Publish oldest-first for a natural reading order; the copy is
            // small (≤ CAPACITY) and appends are rare relative to UI frames.
            _entries.value = buffer.toList()
        }
        runCatching { sink?.invoke(entry) }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _entries.value = emptyList()
        }
    }

    /** Human-readable export (chronological), used by copy/share. */
    fun exportText(minLevel: Level = Level.DEBUG): String {
        val snapshot = synchronized(lock) { buffer.toList() }
        val format = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
        return snapshot
            .filter { it.level >= minLevel }
            .joinToString("\n") { e ->
                val error = e.error?.let { " — $it" }.orEmpty()
                "${format.format(java.util.Date(e.timestampMs))} ${e.level.name.first()}/${e.tag}: ${e.message}$error"
            }
    }
}
