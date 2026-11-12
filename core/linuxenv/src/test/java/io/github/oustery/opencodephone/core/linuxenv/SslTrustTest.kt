package io.github.oustery.opencodephone.core.linuxenv

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SslTrustTest {

    private fun cert(name: String, serial: String): String = """
        -----BEGIN CERTIFICATE-----
        MIIB-$name-$serial
        filler line for the test PEM body
        -----END CERTIFICATE-----
    """.trimIndent() + "\n"

    @Test fun `rebuild concatenates packaged certificates into the bundle path`() {
        val rootfs = Files.createTempDirectory("rootfs").toFile()
        try {
            val mozilla = File(rootfs, "usr/share/ca-certificates/mozilla").apply { mkdirs() }
            val local = File(rootfs, "usr/local/share/ca-certificates").apply { mkdirs() }
            File(mozilla, "b.crt").writeText(cert("B", "1111"))
            File(mozilla, "a.crt").writeText(cert("A", "2222"))
            File(local, "custom.crt").writeText(cert("C", "3333"))
            File(mozilla, "readme.txt").writeText("not a certificate")

            val count = SslTrust.rebuild(rootfs)
            assertEquals(3, count)
            val bundle = SslTrust.bundleFile(rootfs)
            assertTrue(bundle.isFile)
            val text = bundle.readText()
            // Deterministic order: sorted by absolute path, so the local
            // store (usr/local/share) precedes usr/share.
            assertTrue(text.indexOf("C-3333") < text.indexOf("A-2222"))
            assertTrue(text.indexOf("A-2222") < text.indexOf("B-1111"))
            assertEquals(3, Regex("BEGIN CERTIFICATE").findAll(text).count())
            // No temp file left behind.
            assertEquals(listOf("ca-certificates.crt"), bundle.parentFile.list()?.sorted())
        } finally { rootfs.deleteRecursively() }
    }

    @Test fun `rebuild returns null when the rootfs carries no certificates`() {
        val rootfs = Files.createTempDirectory("rootfs-empty").toFile()
        try {
            assertNull(SslTrust.rebuild(rootfs))
            File(rootfs, "usr/share/ca-certificates").mkdirs()
            assertNull(SslTrust.rebuild(rootfs))
        } finally { rootfs.deleteRecursively() }
    }

    @Test fun `ensure rewrites only when the bundle is missing or empty`() {
        val rootfs = Files.createTempDirectory("rootfs-ensure").toFile()
        try {
            val mozilla = File(rootfs, "usr/share/ca-certificates/mozilla").apply { mkdirs() }
            File(mozilla, "a.crt").writeText(cert("A", "4444"))
            // Nothing yet: repair writes the bundle.
            assertFalse(SslTrust.bundleFile(rootfs).exists())
            assertTrue(SslTrust.ensure(rootfs))
            val bundle = SslTrust.bundleFile(rootfs)
            val stamp = bundle.lastModified()
            // Present and non-empty: no rewrite (timestamp unchanged).
            assertTrue(SslTrust.ensure(rootfs))
            assertEquals(stamp, bundle.lastModified())
            // Emptied bundle (failed previous repair): rewritten.
            bundle.writeText("")
            assertTrue(SslTrust.ensure(rootfs))
            assertTrue(bundle.length() > 0)
        } finally { rootfs.deleteRecursively() }
    }

    @Test fun `guest env points git and openssl clients at the bundle`() {
        assertEquals(SslTrust.BUNDLE_PATH, SslTrust.guestBundleEnv["GIT_SSL_CAINFO"])
        assertEquals(SslTrust.BUNDLE_PATH, SslTrust.guestBundleEnv["SSL_CERT_FILE"])
        assertTrue(SslTrust.BUNDLE_PATH.startsWith("/etc/ssl/certs/"))
    }
}
