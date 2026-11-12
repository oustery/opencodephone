package io.github.oustery.opencodephone.core.model

import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class AppLogTest {

    @Test fun `buffer is bounded and keeps the newest entries in order`() {
        // The global buffer may already carry entries from other tests;
        // record the watermark and only reason about what this test adds.
        val before = AppLog.entries.value.size
        repeat(AppLog.CAPACITY + 50) { AppLog.info("Test", "line $it") }
        val after = AppLog.entries.value
        assertEquals(before + AppLog.CAPACITY, after.size)
        val lines = after.map { it.message }
        assertTrue(lines.contains("line 50"))
        assertTrue(lines.contains("line ${AppLog.CAPACITY + 49}"))
        assertFalse(lines.contains("line 49"))
        // Chronological order preserved.
        assertEquals(after.size, after.map { it.timestampMs }.zipWithNext().count { (a, b) -> a <= b } + 1)
    }

    @Test fun `throwables become one-line summaries and never throw`() {
        AppLog.error("Test", "boom", IllegalStateException("root cause details"))
        val entry = AppLog.entries.value.last { it.message == "boom" }
        assertEquals(AppLog.Level.ERROR, entry.level)
        assertTrue(entry.error!!.contains("IllegalStateException: root cause details"))
        AppLog.sink = { throw RuntimeException("broken sink must not break logging") }
        AppLog.info("Test", "still alive")
        assertEquals("still alive", AppLog.entries.value.last().message)
        AppLog.sink = null
    }

    @Test fun `export is chronological human-readable text with levels`() {
        AppLog.warn("ExportTest", "something to export")
        val text = AppLog.exportText()
        val line = text.lineSequence().last { it.contains("something to export") }
        assertTrue(line.contains("W/ExportTest"))
        assertTrue(Regex("\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}").containsMatchIn(line))
    }

    @Test fun `clear empties the visible buffer`() {
        AppLog.clear()
        assertTrue(AppLog.entries.value.isEmpty())
        AppLog.info("Test", "after clear")
        assertEquals(1, AppLog.entries.value.size)
    }
}
