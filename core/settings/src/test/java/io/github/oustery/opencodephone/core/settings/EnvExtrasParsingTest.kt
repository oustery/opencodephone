package io.github.oustery.opencodephone.core.settings

import org.junit.Assert.*
import org.junit.Test

/** Pure companion parsers of SettingsStore — no Android context needed. */
class EnvExtrasParsingTest {
    @Test fun `parses KEY=value lines, trims and deduplicates keeping the last one`() {
        val parsed = SettingsStore.parseEnvExtras(
            """
            HTTPS_PROXY=http://127.0.0.1:7890
            # comment line
            LANG=C.UTF-8

            NO_PROXY=github.com
            LANG=C.UTF-8
            """.trimIndent(),
        )
        assertEquals(
            mapOf(
                "HTTPS_PROXY" to "http://127.0.0.1:7890",
                "LANG" to "C.UTF-8",
                "NO_PROXY" to "github.com",
            ),
            parsed,
        )
    }

    @Test fun `skips invalid lines and counts them`() {
        val raw = """
            GOOD=1
            no equals sign
            9BAD=2
            =3
            ALSO_GOOD=4
        """.trimIndent()
        val parsed = SettingsStore.parseEnvExtras(raw)
        assertEquals(mapOf("GOOD" to "1", "ALSO_GOOD" to "4"), parsed)
        assertEquals(3, SettingsStore.invalidEnvExtrasCount(raw))
        assertEquals(0, SettingsStore.invalidEnvExtrasCount(""))
        assertEquals(0, SettingsStore.invalidEnvExtrasCount("# only a comment"))
    }

    @Test fun `value may contain equals signs and spaces are trimmed`() {
        val parsed = SettingsStore.parseEnvExtras("  TOKEN = a=b=c  ")
        assertEquals(mapOf("TOKEN" to "a=b=c"), parsed)
    }
}
