package tv.cleanyour.app

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.cleanyour.core.flows.StoreRules
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.PlanResult
import tv.cleanyour.core.registry.HttpFetcher
import tv.cleanyour.core.registry.HttpResponse
import tv.cleanyour.core.model.Op
import tv.cleanyour.core.trust.DownloadHosts
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

// The GitHub and F-Droid build (STORES-PLAN 1): the update check, the add-ons, the GitHub hosts
// and the "updates" choice, exactly as before the store flavours.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FullStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val graphs = mutableListOf<AppGraph>()
    private fun graph(): AppGraph = AppGraph(context).also { graphs += it }

    @After fun closeGraphs() { graphs.forEach { it.scheduleUpdateChecks(false); it.close() } }

    private class OneAnswer(val code: Int, val body: String) : HttpFetcher {
        val urls = mutableListOf<String>()
        override suspend fun get(url: String, etag: String?, rangeFrom: Long?): HttpResponse {
            urls += url
            return HttpResponse(code, null, ByteArrayInputStream(body.encodeToByteArray()), body.length.toLong())
        }
    }

    @Test fun thisIsTheFullBuild() {
        assertEquals("full", Store.flavour)
        assertEquals(StoreRules.FULL, Store.rules)
        assertEquals(DownloadHosts.ALL, Store.hosts)
        assertTrue(Store.updatesMayGoOff)
        val g = graph()
        assertSame(Store, g.store)
        assertIs<InstallableOnlyFetcher>(g.seams.fetcher)
        assertEquals(StoreRules.FULL, g.manageDeps.store)
        assertEquals(StoreRules.FULL, g.setupDeps.store)
        for (h in DownloadHosts.ALL) assertTrue(DownloadHosts.isAllowed("https://$h/x", Store.hosts), h)
    }

    @Test fun theUpdateCheckAsksGithubForTheLatestRelease() = runBlocking {
        val newer = OneAnswer(200, """{"tag_name": "v0.4.0", "name": "x"}""")
        assertEquals("0.4.0", Store.updateChecker(newer).latestNewerThan("0.3.0"))
        assertEquals(listOf(Store.RELEASES_URL), newer.urls)
        assertEquals("", Store.updateChecker(OneAnswer(200, """{"tag_name":"v0.3.0"}""")).latestNewerThan("0.3.0"))
        assertEquals(null, Store.updateChecker(OneAnswer(404, "")).latestNewerThan("0.3.0"))
        assertEquals("https://github.com/al3nkboot/mi5g-tv/releases", Store.releasePage)
    }

    @Test fun releaseMetadataSelectsOnlyTheVerifiedMainAppApk() = runBlocking {
        val digest = "a".repeat(64)
        val body = """{"tag_name":"v1.5.0","name":"Mi5G TV v1.5.0","published_at":"2026-10-26T13:05:34Z","body":"Release notes","draft":false,"prerelease":false,"assets":[{"name":"mi5g-tv-dns.apk","browser_download_url":"https://github.com/al3nkboot/mi5g-tv/releases/download/v1.5.0/mi5g-tv-dns.apk","size":1234,"digest":"sha256:$digest"},{"name":"Mi5G-TV-v1.5.0.apk","browser_download_url":"https://github.com/al3nkboot/mi5g-tv/releases/download/v1.5.0/Mi5G-TV-v1.5.0.apk","size":2345,"digest":"sha256:$digest"}]}"""
        val http = OneAnswer(200, body)
        val release = requireNotNull(Store.updateReleaseSource(http).latest())
        assertEquals("1.5.0", release.version)
        assertEquals("Release notes", release.notes)
        assertEquals("https://github.com/al3nkboot/mi5g-tv/releases/tag/v1.5.0", release.releaseUrl)
        assertEquals("Mi5G-TV-v1.5.0.apk", release.asset?.name)
        assertEquals(2345L, release.asset?.sizeBytes)
        assertEquals(digest, release.asset?.sha256?.hex)
        assertEquals(listOf(Store.RELEASES_URL), http.urls)
    }

    @Test fun releaseMetadataRejectsPrereleasesAndMalformedTags() = runBlocking {
        assertEquals(null, Store.updateReleaseSource(OneAnswer(200, """{"tag_name":"v1.6.0-rc.1","prerelease":true}""")).latest())
        assertEquals(null, Store.updateReleaseSource(OneAnswer(200, """{"tag_name":"v1.6.0-rc.1","prerelease":false}""")).latest())
        assertEquals(null, Store.updateReleaseSource(OneAnswer(200, """{"tag_name":"main"}""")).latest())
    }

    @Test fun releaseMetadataRejectsAnApkOutsideTheOfficialReleasePath() = runBlocking {
        val response = """{"tag_name":"v1.5.0","assets":[{"name":"Mi5G-TV-v1.5.0.apk","browser_download_url":"https://github.com/attacker/mi5g-tv/releases/download/v1.5.0/Mi5G-TV-v1.5.0.apk","size":2345,"digest":"sha256:${"a".repeat(64)}"}]}"""
        val release = requireNotNull(Store.updateReleaseSource(OneAnswer(200, response)).latest())
        assertEquals(null, release.asset)
    }

    @Test fun automaticUpdateJobIsDailyNetworkAwareAndCanBeCancelled() {
        val g = graph()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        g.scheduleUpdateChecks(true)
        val job = requireNotNull(scheduler.allPendingJobs.singleOrNull { it.service.className == AppUpdateJobService::class.java.name })
        assertTrue(job.isPeriodic)
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.networkType)
        assertEquals(24L * 60 * 60 * 1000, job.intervalMillis)

        g.scheduleUpdateChecks(false)
        assertTrue(scheduler.allPendingJobs.none { it.service.className == AppUpdateJobService::class.java.name })
    }

    @Test fun softwareUpdatesStayTheOwnersChoice() {
        val g = graph()
        val ota = PackageName("com.mitv.ota")
        val on = PackageState(EnabledState.ENABLED, true, 1, null, false, false, null, false)
        val snap = Samples.snapshot(packages = Samples.miTvPackages(emptySet()) + (ota to on))
        val r = g.planner.plan(PlanRequest.SwitchOff(setOf(ota)), snap, g.facts, emptyList())
        assertIs<PlanResult.Ok>(r)
        assertEquals(listOf<Op>(Op.Disable(ota)), r.plan.steps.map { it.op })
    }
}
