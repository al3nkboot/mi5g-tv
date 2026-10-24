package tv.cleanyour.core.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

// Parser limits and hostile shapes for index.json (SECURITY 6.1 "index parser limits"). Each case is
// a document that a valid signature could still carry (a buggy or compromised publisher).
class IndexDocumentTest {

    private fun rejects(why: String, bytes: ByteArray) {
        try { IndexDocument.parse(bytes); fail("accepted: $why") } catch (e: IndexFormatException) { /* expected */ }
    }

    @Test fun readsTheReferenceShape() {
        val doc = IndexDocument.parse(Docs.index())
        assertEquals(7, doc.sequence)
        assertEquals(1_792_368_000_000L, doc.generatedAtMs)
        val v = doc.modules.single().versions.single()
        assertEquals("0.1.1", v.versionName)
        assertEquals(26, v.minSdk)
        assertEquals(listOf("armeabi-v7a", "arm64-v8a"), v.abis)
        assertEquals("android.permission.INTERNET", v.permissions.single().permission)
    }

    @Test fun ignoresUnknownKeys() {
        val text = String(Docs.index()).replaceFirst("{", """{"later_field":{"x":1},""")
        assertEquals(7, IndexDocument.parse(text.toByteArray()).sequence)
    }

    @Test fun refusesHostileDocuments() {
        rejects("schema 2", String(Docs.index()).replace("\"schema\":1", "\"schema\":2").toByteArray())
        rejects("not json", "{".toByteArray())
        rejects("array top level", "[]".toByteArray())
        rejects("too large", ByteArray(IndexDocument.MAX_BYTES + 1) { ' '.code.toByte() })
        rejects("sequence as string", String(Docs.index()).replace("\"sequence\":7", "\"sequence\":\"7\"").toByteArray())
        rejects("sequence zero", Docs.index(sequence = 0))
        rejects("expires before generated", Docs.index(expires = "2026-10-18T00:00:00Z"))
        rejects("bad date", Docs.index(expires = "next week"))
        rejects("date past epoch milliseconds", Docs.index(expires = "+1000000000-01-01T00:00:00Z"))
        rejects("http apk", Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(url = "http://github.com/a.apk"))))))
        rejects("foreign host", Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(url = "https://evil.example/a.apk"))))))
        rejects("look-alike host", Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(url = "https://github.com.evil.example/a.apk"))))))
        rejects("user info", Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(url = "https://x@github.com/a.apk"))))))
        rejects("odd port", Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(url = "https://github.com:8443/a.apk"))))))
        rejects("uppercase sha", Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(sha = Docs.RETRO_SHA.uppercase()))))))
        rejects("short signer", Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(signer = "abc"))))))
        rejects("size zero", Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(size = 0))))))
        rejects("size over 200 MB", Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(size = 200L * 1024 * 1024 + 1))))))
        rejects("bad package", Docs.index(modules = listOf(Docs.module(pkg = "not a package"))))
        rejects("shell in package", Docs.index(modules = listOf(Docs.module(pkg = "a.b;reboot"))))
        rejects("unknown tier", Docs.index(modules = listOf(Docs.module(tier = "gold"))))
        rejects("bidi override in name", Docs.index(modules = listOf(Docs.module(name = "Retro\\u202Eexe"))))
        rejects("control char in name", Docs.index(modules = listOf(Docs.module(name = "Re\\u0007tro"))))
        rejects("empty name", Docs.index(modules = listOf(Docs.module(name = " "))))
        rejects("long name", Docs.index(modules = listOf(Docs.module(name = "R".repeat(41)))))
        rejects("bad id", Docs.index(modules = listOf(Docs.module(id = "Retro!"))))
        rejects("duplicate id", Docs.index(modules = listOf(Docs.module(), Docs.module(pkg = "io.github.other.app"))))
        rejects("duplicate package", Docs.index(modules = listOf(Docs.module(), Docs.module(id = "retro2"))))
        rejects("duplicate version", Docs.index(modules = listOf(Docs.module(versions = listOf(Docs.version(), Docs.version())))))
        rejects("no versions", Docs.index(modules = listOf(Docs.module(versions = emptyList()))))
        rejects("too many modules", Docs.index(modules = (0..IndexDocument.MAX_MODULES).map { Docs.module(id = "m$it", pkg = "io.github.m$it.app") }))
        rejects("unknown abi", String(Docs.index()).replace("\"arm64-v8a\"", "\"mips\"").toByteArray())
        rejects("bad revocation", Docs.index(revoked = listOf("xyz")))
    }

    @Test fun downloadHostsAreExact() {
        assertTrue(DownloadHosts.isAllowed("https://github.com/a/b/releases/download/v1/x.apk"))
        assertTrue(DownloadHosts.isAllowed("https://release-assets.githubusercontent.com/x?sig=1"))
        assertTrue(DownloadHosts.isAllowed("https://objects.githubusercontent.com/x"))
        assertTrue(DownloadHosts.isAllowed("https://GitHub.com/x"))
        assertTrue(DownloadHosts.isAllowed("https://github.com:443/x"))
        for (bad in listOf("http://github.com/x", "https://gist.github.com/x", "https://github.com.evil.example/x", "https://evil.example/github.com",
            "https://user:pw@github.com/x", "https://github.com:444/x", "ftp://github.com/x", "github.com/x", "https://github.com/x#frag",
            "https://raw.githubusercontent.com/x", "https://" + "a".repeat(3000), "https://[::1]/x", "file:///etc/passwd")) {
            assertFalse(bad, DownloadHosts.isAllowed(bad))
        }
    }
}
