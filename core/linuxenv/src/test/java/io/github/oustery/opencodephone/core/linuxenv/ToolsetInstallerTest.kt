package io.github.oustery.opencodephone.core.linuxenv

import org.junit.Assert.*
import org.junit.Test

class ToolsetInstallerTest {
    @Test fun `mirror prefix routes only github release assets and keeps direct URLs intact`() {
        val asset = "https://github.com/oustery/opencodephone/releases/download/toolset-v1/toolset-arm64.tar.gz"
        // No mirror configured: byte-identical URL.
        assertEquals(asset, ToolsetInstaller.applyMirror(asset, ""))
        assertEquals(asset, ToolsetInstaller.applyMirror(asset, "   "))
        // Mirror prefixes the URL; trailing slashes on the mirror are trimmed.
        assertEquals(
            "https://ghproxy.net/$asset",
            ToolsetInstaller.applyMirror(asset, "https://ghproxy.net"),
        )
        assertEquals(
            "https://ghproxy.net/$asset",
            ToolsetInstaller.applyMirror(asset, "https://ghproxy.net/"),
        )
        // Non-github endpoints (npm, docker registry) are never mirrored.
        val npm = "https://registry.npmjs.org/opencode-linux-arm64/1.19.5.tgz"
        assertEquals(npm, ToolsetInstaller.applyMirror(npm, "https://ghproxy.net"))
    }
}
