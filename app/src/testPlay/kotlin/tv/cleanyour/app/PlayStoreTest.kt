package tv.cleanyour.app

import android.app.Application
import android.os.Looper
import androidx.navigation3.runtime.NavKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.cleanyour.core.flows.AppSettingsIntent
import tv.cleanyour.core.flows.StoreRules
import tv.cleanyour.core.flows.UpdateCheck
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.model.EnabledState
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.PackageState
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.planner.DefaultPlanner
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.PlanResult
import tv.cleanyour.core.registry.FetchException
import tv.cleanyour.core.registry.FetchFailure
import tv.cleanyour.core.registry.FetchResult
import tv.cleanyour.core.registry.HttpFetcher
import tv.cleanyour.core.registry.HttpResponse
import tv.cleanyour.core.trust.DownloadHosts
import tv.cleanyour.testing.fixtures.Samples
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// The Google Play build (STORES-PLAN 1, D3), on its real graph: no update check, no add-on fetcher,
// no GitHub host anywhere the app could reach, and no "updates go off" choice.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val graphs = mutableListOf<AppGraph>()
    private fun graph(): AppGraph = AppGraph(context).also { graphs += it }

    @After fun closeGraphs() { graphs.forEach { it.close() } }

    private fun idle(ms: Long = 300) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
    }

    // Records every request; the Play build must make none.
    private class RecordingHttp : HttpFetcher {
        val urls = mutableListOf<String>()
        override suspend fun get(url: String, etag: String?, rangeFrom: Long?): HttpResponse { urls += url; return HttpResponse(404, null, null, null) }
    }

    @Test fun thisIsThePlayBuild() {
        assertEquals("play", Store.flavour)
        assertEquals(StoreRules.PLAY, Store.rules)
        val g = graph()
        assertSame(Store, g.store)
        assertEquals(StoreRules.PLAY, g.setupDeps.store)
        assertEquals(StoreRules.PLAY, g.manageDeps.store)
    }

    @Test fun thereIsNoUpdateCheck() = runBlocking {
        val http = RecordingHttp()
        assertNull(Store.updateChecker(http).latestNewerThan("0.3.0"))
        assertTrue(http.urls.isEmpty())
        assertNull(Store.releasePage)
        // App settings neither shows nor runs it.
        val g = graph()
        val m = g.appSettings(g.scope)
        idle()
        assertFalse(m.state.value.updateCheck)
        m.dispatch(AppSettingsIntent.CheckUpdates)
        idle()
        assertEquals(UpdateCheck.Idle, m.state.value.update)
    }

    @Test fun thereIsNoAddOnFetcher() = runBlocking {
        val g = graph()
        assertSame(Store.NoAddOns, g.seams.fetcher)
        assertSame(Store.NoAddOns, g.manageDeps.fetcher)
        assertFalse(g.seams.fetcher is InstallableOnlyFetcher)
        val ref = ArtifactRef("https://github.com/x/retro/releases/download/v1/retro.apk", 10, Sha256("a".repeat(64)), Sha256("b".repeat(64)))
        assertEquals(FetchResult.Failed(FetchFailure.HOST_NOT_ALLOWED, null), g.seams.fetcher.fetch(ref) { _, _ -> error("no progress without a download") })
        // Manage has no Cards and modules row, and the modules route opens nothing.
        val manage = g.manage(g.scope, null)
        idle()
        assertFalse(manage.state.value.addOns)
        assertFalse(g.setupDeps.store.addOns)
    }

    // F-Droid Inclusion Policy (security 5): the add-on page says, before the first download, that
    // add-ons come from their authors' GitHub releases outside any app store. The Play build has no
    // add-ons, so no screen that carries that line can open: no route reaches the modules screens,
    // Done offers no "Add a card" and Manage no "Cards and modules".
    @Test fun theAddOnNoticeIsNeverShown() {
        assertTrue(context.getString(tv.cleanyour.feature.modules.R.string.module_outside_store).contains("F-Droid"))
        val tv = "0123456789abcdef"
        for (start in listOf<NavKey>(Dest.Manage(tv), Dest.Setup(tv))) {
            val stack = mutableListOf(start)
            for (route in listOf("modules/$tv", "modules")) {
                assertTrue(applyRoute(stack, route, noTvs = false), route)
                assertEquals(listOf(start), stack, route)
            }
        }
        val g = graph()
        assertFalse(g.store.rules.addOns)
        assertFalse(g.setupDeps.store.addOns)
        val manage = g.manage(g.scope, null)
        idle()
        assertFalse(manage.state.value.addOns)
    }

    @Test fun noGithubHostIsReachable() = runBlocking {
        assertTrue(Store.hosts.isEmpty())
        val g = graph()
        // Every GitHub URL, the release check's among them, is refused before a connection opens.
        val urls = DownloadHosts.ALL.map { "https://$it/al3nkboot/mi5g-tv/releases/latest" } +
            "https://api.github.com/repos/al3nkboot/mi5g-tv/releases/latest"
        for (url in urls) {
            val e = assertFailsWith<FetchException>(url) { g.seams.http.get(url, null, null) }
            assertEquals(FetchFailure.HOST_NOT_ALLOWED, e.failure, url)
        }
        // The network security config names no host (AppGraphTest checks it equals Store.hosts).
        val xml = context.resources.getXml(R.xml.network_security_config)
        var domains = 0
        while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && (xml.name == "domain" || xml.name == "domain-config")) domains++
        }
        assertEquals(0, domains)
    }

    @Test fun softwareUpdatesCannotBeSwitchedOff() {
        assertFalse(Store.updatesMayGoOff)
        val g = graph()
        val ota = PackageName("com.mitv.ota")
        val on = PackageState(EnabledState.ENABLED, true, 1, null, false, false, null, false)
        val snap = Samples.snapshot(packages = Samples.miTvPackages(emptySet()) + (ota to on))
        val r = g.planner.plan(PlanRequest.SwitchOff(setOf(ota)), snap, g.facts, emptyList())
        assertIs<PlanResult.Refused>(r)
        assertTrue(r.reasons.single().contains(DefaultPlanner.REASON_UPDATES), r.reasons.toString())
    }
}
