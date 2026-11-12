package io.github.oustery.opencodephone.core.linuxenv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class GitAuthTest {
    @Test fun `network options toggle ssl verification and proxy without touching credentials`() {
        val plain = GitAuth.command("clone", "--", "https://github.com/a/b")
        assertTrue(plain.contains("http.sslVerify=true"))
        assertFalse(plain.any { it.startsWith("http.proxy=") })
        val relaxed = GitAuth.command(
            GitAuth.NetOptions(sslVerify = false, proxy = "127.0.0.1:8888"),
            "clone", "--", "https://github.com/a/b",
        )
        assertTrue(relaxed.contains("http.sslVerify=false"))
        assertTrue(relaxed.contains("http.proxy=127.0.0.1:8888"))
        // The credential-helper contract is untouched by the options: secrets
        // still flow only through the environment, never argv.
        assertTrue(relaxed.contains("credential.helper="))
        assertFalse(GitAuth.command(GitAuth.NetOptions(proxy = "  "), "pull").any { it.startsWith("http.proxy=") })
    }
    @Test fun `helper releases a fake token only for HTTPS github without persisting it`() {
        fun fill(host: String): Pair<Int, String> {
            val builder = ProcessBuilder(GitAuth.command("credential", "fill")).redirectErrorStream(true)
            builder.environment().putAll(GitAuth.environment("https://github.com/owner/repo", "FAKE_TOKEN"))
            val proc = builder.start()
            proc.outputStream.use { it.write("protocol=https\nhost=$host\n\n".toByteArray()) }
            val text = proc.inputStream.bufferedReader().readText()
            return proc.waitFor() to text
        }
        assertTrue(fill("github.com").second.contains("password=FAKE_TOKEN"))
        assertFalse(fill("evil.example").second.contains("FAKE_TOKEN"))
        assertFalse(GitAuth.command("push").joinToString().contains("FAKE_TOKEN"))
        assertFalse(GitAuth.environment("https://evil.example/a", "FAKE_TOKEN").containsKey("OPENCODEPHONE_GIT_TOKEN"))
    }
    @Test fun `migrates all old remote and push URLs without putting old tokens in command arguments`() = runBlocking {
        val root = Files.createTempDirectory("git-migration").toFile()
        try {
            suspend fun run(args: List<String>) = runCapturedProcess(ProcessBuilder(args).directory(root), 10000)
            assertEquals(0, run(listOf("git", "init", "-q")).exitCode)
            for (key in listOf("remote.origin.url", "remote.origin.pushurl")) {
                run(listOf("git", "config", key, "https://x-access-token:FAKE_TOKEN@github.com/a/b"))
            }
            GitAuth.migrateRemotes { args ->
                assertFalse(args.any { it.contains("FAKE_TOKEN") })
                run(args)
            }
            assertFalse(java.io.File(root, ".git/config").readText().contains("FAKE_TOKEN"))
            assertEquals("https://github.com/a/b", GitAuth.cleanUrl("https://x:FAKE_TOKEN@github.com/a/b"))
        } finally { root.deleteRecursively() }
    }
}
