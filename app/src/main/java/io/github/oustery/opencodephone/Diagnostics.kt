package io.github.oustery.opencodephone

import android.content.Context
import android.util.Log
import io.github.oustery.opencodephone.core.model.AppLog
import java.io.File

/**
 * Bridges [AppLog] to the platform: every entry is mirrored to Logcat (the
 * same priority mapping adb users expect) and appended to a small rotating
 * file pair under filesDir/logs. The file survives process death, so a bug
 * report can include what happened before the app was restarted.
 */
object Diagnostics {

    private const val ROTATE_BYTES: Long = 512 * 1024
    private const val TAG_PREFIX = "OC"

    @Volatile
    private var logDir: File? = null

    fun install(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        logDir = dir
        AppLog.sink = { entry -> write(entry) }
        AppLog.info("App", "Diagnostics installed (logs in ${dir.name}/)")
    }

    /** Current diagnostic files, oldest first; used by the share action. */
    fun files(): List<File> = listOfNotNull(
        logDir?.let { File(it, "app.old") },
        logDir?.let { File(it, "app.log") },
    ).filter { it.isFile && it.length() > 0 }

    private fun write(entry: AppLog.Entry) {
        val priority = when (entry.level) {
            AppLog.Level.DEBUG -> Log.DEBUG
            AppLog.Level.INFO -> Log.INFO
            AppLog.Level.WARN -> Log.WARN
            AppLog.Level.ERROR -> Log.ERROR
        }
        val line = buildString {
            append(entry.message)
            entry.error?.let { append(" — ").append(it) }
        }
        Log.println(priority, "$TAG_PREFIX/${entry.tag}", line)

        val dir = logDir ?: return
        runCatching {
            synchronized(Diagnostics) {
                val current = File(dir, "app.log")
                if (current.length() > ROTATE_BYTES) {
                    // One-generation rotation: app.log -> app.old (replaced).
                    if (!current.renameTo(File(dir, "app.old"))) current.delete()
                }
                current.appendText(format(entry) + "\n")
            }
        }
    }

    private fun format(entry: AppLog.Entry): String {
        val time = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date(entry.timestampMs))
        val error = entry.error?.let { " — $it" }.orEmpty()
        return "$time ${entry.level.name}/${entry.tag}: ${entry.message}$error"
    }
}
