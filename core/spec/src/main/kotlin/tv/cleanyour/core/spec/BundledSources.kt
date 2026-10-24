package tv.cleanyour.core.spec

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.DeviceFacts
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.model.Sha256
import java.security.MessageDigest

private fun resourceBytes(path: String): ByteArray? = BundledProfileSource::class.java.getResourceAsStream(path)?.use { it.readBytes() }

// Profiles shipped inside the APK (D28), converted at build time from the pinned upstream
// examples. resolve(): an exact profile by model or device (with brand, SDK range and
// fingerprint prefix when the profile sets them; the highest score, then the longest fingerprint
// prefix), else the brand sure set (D26), else None with the generic stock launcher allowlist.
class BundledProfileSource(private val bundle: ProfileBundle = bundled) : ProfileSource {
    override suspend fun all(): List<Profile> = bundle.profiles

    override suspend fun resolve(facts: DeviceFacts): ProfileResolution = resolveNow(facts)

    fun sureSets(): List<SureSet> = bundle.sureSets

    fun resolveNow(facts: DeviceFacts): ProfileResolution {
        val exact = bundle.profiles.mapNotNull { p -> score(p, facts)?.let { it to p } }
            .sortedWith(compareByDescending<Pair<Int, Profile>> { it.first }.thenByDescending { specificity(it.second) }.thenBy { it.second.id })
            .firstOrNull()?.second
        if (exact != null) return ProfileResolution.Exact(exact)
        val sure = bundle.sureSets.firstOrNull { it.brand.equals(facts.brand, ignoreCase = true) || it.brand.equals(facts.manufacturer, ignoreCase = true) }
        if (sure != null) return ProfileResolution.BrandSureSet(sure.brand, sure.groups, sure.stockLaunchers)
        return ProfileResolution.None(KnownLaunchers.generic)
    }

    companion object {
        val bundled: ProfileBundle by lazy {
            val bytes = resourceBytes(ProfileBundleJson.RESOURCE) ?: error("profiles.json missing from the :core:spec resources")
            ProfileBundleJson.decode(bytes.decodeToString())
        }

        // RF-PROFILES-63 ruling 1: of two profiles with the same score, the one whose fingerprint
        // prefix is longer names the firmware more exactly and wins (none counts as 0); only a pair
        // that is still level falls back to the id, and ProfilesTest pins every such pair.
        fun specificity(p: Profile): Int = p.match.fingerprintPrefix?.length ?: 0

        // 3 for model and device, 2 for the model, 1 for the device; null when it does not match.
        fun score(p: Profile, f: DeviceFacts): Int? {
            val m = p.match
            val model = m.models.any { it.equals(f.model, ignoreCase = true) }
            val device = m.devices.any { it.equals(f.device, ignoreCase = true) }
            if (!model && !device) return null
            val brand = m.brand
            if (brand != null && !brand.equals(f.brand, ignoreCase = true) && !brand.equals(f.manufacturer, ignoreCase = true)) return null
            val min = m.sdkMin
            val max = m.sdkMax
            val prefix = m.fingerprintPrefix
            if (min != null && f.sdk < min) return null
            if (max != null && f.sdk > max) return null
            if (prefix != null && !f.fingerprint.startsWith(prefix)) return null
            return (if (model) 2 else 0) + (if (device) 1 else 0)
        }
    }
}

// Friendly names and one-line descriptions (English) for packages in the profiles and the dock
// table, from facts.tsv: package, name, description, category.
class BundledFactsTable(private val facts: Map<PackageName, PackageFacts> = bundled) : FactsTable {
    override fun lookup(pkg: PackageName): PackageFacts? = facts[pkg]

    val size: Int get() = facts.size

    companion object {
        const val RESOURCE = "/tv/cleanyour/core/spec/facts.tsv"

        fun parse(text: String): Map<PackageName, PackageFacts> {
            val out = LinkedHashMap<PackageName, PackageFacts>()
            text.lineSequence().forEachIndexed { i, line ->
                if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
                val f = line.split('\t')
                require(f.size == 4) { "facts.tsv:${i + 1}: expected 4 fields, got ${f.size}" }
                val pkg = PackageName(f[0])
                require(out.put(pkg, PackageFacts(f[1], f[2], f[3])) == null) { "facts.tsv:${i + 1}: ${f[0]} listed twice" }
            }
            return out
        }

        val bundled: Map<PackageName, PackageFacts> by lazy {
            parse(resourceBytes(RESOURCE)?.decodeToString() ?: error("facts.tsv missing from the :core:spec resources"))
        }
    }
}

// The three upstream themes, copied from the pinned submodule into the :core:spec resources at
// build time. A wallpaper is a bundled asset: BlobRef.path is "bundled:" plus the resource path,
// and bytes(blob) reads it back.
class BundledThemeSource : ThemeSource {
    override fun bundled(): List<Theme> = themes

    companion object {
        const val PREFIX = "bundled:"
        private const val DIR = "/tv/cleanyour/core/spec/themes"
        private val catalogue = listOf(
            Triple("default", "Default", "Black, four tiles a row, the dock at the very bottom."),
            Triple("aurora", "Aurora", "A dark aurora picture on navy behind the apps."),
            Triple("six-columns", "Six columns", "Six smaller tiles a row, one row showing under the dock."),
        )

        val themes: List<Theme> by lazy {
            catalogue.map { (id, name, description) ->
                val config = resourceBytes("$DIR/$id/config.json") ?: error("theme $id missing from the :core:spec resources")
                val doc = when (val r = LauncherConfigCodec.decode(config)) {
                    is DecodeResult.Ok -> r.doc
                    is DecodeResult.Rejected -> error("bundled theme $id does not decode: ${r.reason}")
                }
                val wallpaperPath = "$DIR/$id/wallpaper.jpg"
                val wallpaper = resourceBytes(wallpaperPath)?.let { BlobRef(sha256(it), it.size.toLong(), PREFIX + wallpaperPath.removePrefix("/")) }
                Theme(id, name, description, doc, wallpaper)
            }
        }

        // Bytes of a bundled theme asset, or null when the ref is not one of ours.
        fun bytes(blob: BlobRef): ByteArray? {
            if (!blob.path.startsWith(PREFIX)) return null
            val b = resourceBytes("/" + blob.path.removePrefix(PREFIX)) ?: return null
            return b.takeIf { sha256(it) == blob.sha256 }
        }

        private fun sha256(b: ByteArray) = Sha256(MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 0xff) })
    }
}
