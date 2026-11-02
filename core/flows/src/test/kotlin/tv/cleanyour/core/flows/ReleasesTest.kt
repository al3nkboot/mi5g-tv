package tv.cleanyour.core.flows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleasesTest {
    @Test fun numericNotTextual() {
        assertTrue(Releases.isNewer("2.0.0", "2.0.0-dev"))
        assertFalse(Releases.isNewer("1.6.0", "2.0.0-dev"))
        assertTrue(Releases.isNewer("1.10.0", "1.9.3"))
        assertFalse(Releases.isNewer("v2.0.0", "2.0.0"))
        assertTrue(Releases.isNewer("2.0.1", "2.0"))
    }

    @Test fun parsePicksTheMainApkOnOurRepoOnly() {
        val body = """{"tag_name":"v2.1.0","assets":[
            {"browser_download_url":"https://github.com/al3nkboot/mi5g-tv/releases/download/v2.1.0/mi5g-tv-prayer.apk"},
            {"browser_download_url":"https://evil.example/Mi5G-TV-v2.1.0.apk"},
            {"browser_download_url":"https://github.com/al3nkboot/mi5g-tv/releases/download/v2.1.0/Mi5G-TV-v2.1.0.apk"}]}"""
        val l = Releases.parse(body)!!
        assertEquals("2.1.0", l.version)
        assertEquals("https://github.com/al3nkboot/mi5g-tv/releases/download/v2.1.0/Mi5G-TV-v2.1.0.apk", l.apkUrl)
        assertNull(Releases.parse("""{"tag_name":"v2.1.0","assets":[]}""")!!.apkUrl)
        assertNull(Releases.parse("{}"))
    }
}

class ReleaseApkUrlTest {
    @Test fun apkUrl() {
        kotlin.test.assertEquals("https://github.com/al3nkboot/mi5g-tv/releases/download/v1.6.0/Mi5G-TV-v1.6.0.apk", Releases.apkUrlFor("1.6.0"))
        kotlin.test.assertFailsWith<IllegalArgumentException> { Releases.apkUrlFor("1.0/../x") }
    }
}
