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
        /** How many consecutive identical records collapsed into this line. */
        val repeat: Int = 1,
    )

    const val CAPACITY = 1500
    private const val MESSAGE_CAP = 600
    private const val ERROR_CAP = 300

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
            message = truncate(message, MESSAGE_CAP),
            error = throwable?.let {
                "${it.javaClass.simpleName}: ${truncate(it.message.orEmpty(), ERROR_CAP)}"
            },
        )
        synchronized(lock) {
            // Collapse consecutive identical records (retry storms, per-event
            // refetches of the same dead endpoint) into one line with a
            // repeat counter, so the tail stays readable and the diagnostics
            // export does not fill with a wall of identical warnings.
            val last = buffer.lastOrNull()
            if (last != null && last.level == level && last.tag == entry.tag &&
                last.message == entry.message && last.error == entry.error
            ) {
                buffer.removeLast()
                buffer.addLast(entry.copy(repeat = last.repeat + 1, timestampMs = entry.timestampMs))
            } else {
                buffer.addLast(entry)
            }
            while (buffer.size > CAPACITY) buffer.removeFirst()
            // Publish oldest-first for a natural reading order; the copy is
            // small (≤ CAPACITY) and appends are rare relative to UI frames.
            _entries.value = buffer.toList()
        }
        runCatching { sink?.invoke(entry) }
    }

    /**
     * Keep both ends of over-long records: for command lines (git, shell) the
     * tail carries the actual subcommand after long `-c …` option blocks, for
     * response bodies the head carries the status. A head-only cut dropped
     * exactly the informative half of long git commands.
     */
    private fun truncate(text: String, cap: Int): String {
        if (text.length <= cap) return text
        val cut = text.length - cap
        val head = text.take(cap * 2 / 3)
        val tail = text.takeLast(cap / 4)
        return "$head …[cut $cut chars]… $tail"
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
                val repeat = if (e.repeat > 1) " (×${e.repeat})" else ""
                "${format.format(java.util.Date(e.timestampMs))} ${e.level.name.first()}/${e.tag}: ${e.message}$error$repeat"
            }
    }
}
