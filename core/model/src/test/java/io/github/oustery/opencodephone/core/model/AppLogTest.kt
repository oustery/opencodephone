package io.github.oustery.opencodephone.core.model

import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class AppLogTest {

    @Test fun `buffer is bounded and keeps the newest entries in order`() {
        // Self-sufficient start: the buffer is a global shared by all tests.
        AppLog.clear()
        repeat(AppLog.CAPACITY + 50) { AppLog.info("Test", "line $it") }
        val after = AppLog.entries.value
        assertEquals(AppLog.CAPACITY, after.size)
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

    @Test fun `consecutive identical records collapse into one entry with a repeat counter`() {
        AppLog.clear()
        repeat(5) { AppLog.warn("Storm", "HTTP 404 GET /session/x/todo: Session not found") }
        AppLog.info("Storm", "different record breaks the run")
        repeat(2) { AppLog.warn("Storm", "HTTP 404 GET /session/x/todo: Session not found") }
        val entries = AppLog.entries.value
        assertEquals(3, entries.size)
        assertEquals(5, entries[0].repeat)
        assertEquals(1, entries[1].repeat)
        assertEquals(2, entries[2].repeat)
        // The export marks collapsed runs, so a copied log shows the scale.
        assertTrue(AppLog.exportText().contains("(×5)"))
        // A different level with the same text is NOT collapsed.
        AppLog.clear()
        AppLog.warn("Storm", "same text")
        AppLog.error("Storm", "same text")
        assertEquals(2, AppLog.entries.value.size)
    }

    @Test fun `over-long messages keep head and tail`() {
        AppLog.clear()
        val cmd = "a".repeat(500) + " git push origin main " + "b".repeat(500)
        AppLog.debug("Git", cmd)
        val message = AppLog.entries.value.single().message
        assertTrue("head kept", message.startsWith("aaaa"))
        assertTrue("tail kept — the actual subcommand must survive", message.endsWith("bbbb"))
        assertTrue("cut marker", message.contains("…[cut"))
        assertTrue("still bounded", message.length <= 600)
    }

    @Test fun `error summaries are truncated the same way`() {
        AppLog.clear()
        AppLog.error("Test", "boom", IllegalStateException("x".repeat(800)))
        val summary = checkNotNull(AppLog.entries.value.single().error)
        assertTrue(summary.contains("…[cut"))
        assertTrue(summary.length <= 320)
    }
}
