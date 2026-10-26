package tv.cleanyour.app

import tv.cleanyour.core.flows.StoreRules
import tv.cleanyour.core.flows.AppUpdateAsset
import tv.cleanyour.core.flows.AppUpdateRelease
import tv.cleanyour.core.flows.AppUpdateReleaseSource
import tv.cleanyour.core.flows.SemanticVersion
import tv.cleanyour.core.flows.UpdateChecker
import tv.cleanyour.core.model.Sha256
import tv.cleanyour.core.registry.ArtifactFetcher
import tv.cleanyour.core.registry.HttpFetcher
import tv.cleanyour.core.trust.DownloadHosts
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

// The full build (GitHub and F-Droid, STORES-PLAN 1): everything the app did before the store
// flavours, unchanged.
object Store : StorePorts {
    override val flavour = "full"
    override val hosts: Set<String> = DownloadHosts.ALL
    override val rules = StoreRules.FULL
    override val updatesMayGoOff = rules.updatesOff
    override val releasePage: String = RELEASE_PAGE

    // Stable latest endpoint only. Limit the response before JSON parsing; validate every value
    // used later as a link or download, and ignore drafts/prereleases even if GitHub returns one.
    override fun updateReleaseSource(http: HttpFetcher) = AppUpdateReleaseSource {
        val response = http.get(RELEASES_URL, null, null)
        if (response.code != 200) { response.body?.close(); return@AppUpdateReleaseSource null }
        val bytes = response.body?.use { readBounded(it, MAX_RELEASE_JSON_BYTES) } ?: return@AppUpdateReleaseSource null
        val json = runCatching { JSONObject(bytes.decodeToString()) }.getOrNull() ?: return@AppUpdateReleaseSource null
        if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) return@AppUpdateReleaseSource null
        val rawTag = json.optString("tag_name").trim()
        val version = rawTag.removePrefix("v").removePrefix("V")
        val parsedVersion = SemanticVersion.parse(version) ?: return@AppUpdateReleaseSource null
        if (parsedVersion.prerelease != null) return@AppUpdateReleaseSource null
        val releaseUrl = "https://github.com/al3nkboot/mi5g-tv/releases/tag/$rawTag"
        val assetName = "Mi5G-TV-v$version.apk"
        val expectedAssetUrl = "https://github.com/al3nkboot/mi5g-tv/releases/download/$rawTag/$assetName"
        val assets = json.optJSONArray("assets")
        val asset = (0 until (assets?.length() ?: 0)).asSequence()
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").equals(assetName, ignoreCase = true) }
            ?.let { item ->
                val url = item.optString("browser_download_url")
                val size = item.optLong("size", -1L)
                val digestText = item.optString("digest").takeUnless { it.isBlank() || it == "null" }
                val digestHex = digestText?.removePrefix("sha256:")?.takeIf { it.matches(Regex("^[0-9a-fA-F]{64}$")) }
                if (size !in 1..MAX_UPDATE_APK_BYTES || url != expectedAssetUrl || !DownloadHosts.isAllowed(url, hosts)) null
                else AppUpdateAsset(item.optString("name"), url, size, digestHex?.lowercase()?.let(::Sha256))
            }
        AppUpdateRelease(
            version = version,
            title = json.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: "Mi5G TV $version",
            publishedAt = json.optString("published_at").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
            notes = json.optString("body").take(20_000),
            releaseUrl = releaseUrl,
            asset = asset,
        )
    }

    // Kept as a small adapter for the existing settings flow and its store tests.
    override fun updateChecker(http: HttpFetcher) = UpdateChecker { current ->
        val release = updateReleaseSource(http).latest() ?: return@UpdateChecker null
        if (SemanticVersion.isNewer(release.version, current)) release.version else ""
    }

    override fun addOnFetcher(make: () -> ArtifactFetcher): ArtifactFetcher = make()

    const val RELEASES_URL = "https://api.github.com/repos/al3nkboot/mi5g-tv/releases/latest"
    const val RELEASE_PAGE = "https://github.com/al3nkboot/mi5g-tv/releases"
    private const val MAX_RELEASE_JSON_BYTES = 512 * 1024
    private const val MAX_UPDATE_APK_BYTES = 100L * 1024 * 1024

    private fun readBounded(input: InputStream, limit: Int): ByteArray? {
        val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var count = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            count += read
            if (count > limit) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
