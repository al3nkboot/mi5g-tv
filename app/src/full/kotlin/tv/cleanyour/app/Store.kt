package tv.cleanyour.app

import tv.cleanyour.core.flows.StoreRules
import tv.cleanyour.core.flows.UpdateChecker
import tv.cleanyour.core.registry.ArtifactFetcher
import tv.cleanyour.core.registry.HttpFetcher
import tv.cleanyour.core.trust.DownloadHosts

// The full build (GitHub and F-Droid, STORES-PLAN 1): everything the app did before the store
// flavours, unchanged.
object Store : StorePorts {
    override val flavour = "full"
    override val hosts: Set<String> = DownloadHosts.ALL
    override val rules = StoreRules.FULL
    override val updatesMayGoOff = rules.updatesOff
    override val releasePage: String = RELEASE_PAGE

    // D12: one GET on user action (Settings, "Check for updates"); "couldn't check" when GitHub
    // does not answer or the repository has no published release yet.
    override fun updateChecker(http: HttpFetcher) = UpdateChecker { current ->
        val r = http.get(RELEASES_URL, null, null)
        if (r.code != 200) { r.body?.close(); null } else {
            val body = r.body?.use { it.readBytes().decodeToString() } ?: return@UpdateChecker null
            val tag = Regex("\"tag_name\"\\s*:\\s*\"v?([0-9][0-9A-Za-z.]*)\"").find(body)?.groupValues?.get(1) ?: return@UpdateChecker null
            if (tag != current) tag else ""
        }
    }

    override fun addOnFetcher(make: () -> ArtifactFetcher): ArtifactFetcher = make()

    const val RELEASES_URL = "https://api.github.com/repos/al3nkboot/mi5g-tv/releases/latest"
    const val RELEASE_PAGE = "https://github.com/al3nkboot/mi5g-tv/releases"
}
