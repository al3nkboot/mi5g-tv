package tv.cleanyour.app

import tv.cleanyour.core.flows.StoreRules
import tv.cleanyour.core.flows.UpdateChecker
import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.registry.ArtifactFetcher
import tv.cleanyour.core.registry.FetchFailure
import tv.cleanyour.core.registry.FetchResult
import tv.cleanyour.core.registry.HttpFetcher

// The Google Play build (STORES-PLAN 1, decision D3): Google Play updates the app, and Play allows
// no app downloads from outside Play, so there is no update check, no add-on download and no host
// to reach; the TV's software updaters always stay on. The ports are no-ops and the flows hide
// what they would have offered (StoreRules.PLAY).
object Store : StorePorts {
    override val flavour = "play"
    override val hosts: Set<String> = emptySet()
    override val rules = StoreRules.PLAY
    override val updatesMayGoOff = rules.updatesOff
    override val releasePage: String? = null

    // Never asked (Settings has no "Check for updates"); answers "couldn't check" without a request.
    override fun updateChecker(http: HttpFetcher) = UpdateChecker { null }

    // The real fetcher is never built: nothing is downloaded, for any URL.
    override fun addOnFetcher(make: () -> ArtifactFetcher): ArtifactFetcher = NoAddOns

    object NoAddOns : ArtifactFetcher {
        override suspend fun fetch(ref: ArtifactRef, onProgress: (Long, Long) -> Unit): FetchResult =
            FetchResult.Failed(FetchFailure.HOST_NOT_ALLOWED, null)
    }
}
