package tv.cleanyour.core.flows

import tv.cleanyour.core.model.GroupDefault
import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.model.Profile
import tv.cleanyour.core.ops.Denylist

// M4 item 33: the Mi5G device list. The TV's model picks its profile at connect time (BundledSources);
// here the profile becomes the App Manager's "Suggested" list: every package its groups name, minus
// what it must never switch off and anything the denylist protects. Groups that ask first ("ask")
// (and kept groups) are left out, so the list holds only what is safe without a question.
object DeviceProfiles {
    // Mi5G's own names for the devices it ships tuned profiles for.
    private val LABELS = mapOf(
        "xiaomi-mitv-stick-aesp0" to "Xiaomi TV Stick 4K (MiTV-AESP0)",
        "xiaomi-tv-stick-4k-axsr0" to "Xiaomi TV Stick 4K (MiTV-AXSR0)",
        "xiaomi-tv-box-s-3rd-gen" to "Xiaomi TV Box S الجيل الثالث (MDZ-32-AA)",
        "xiaomi-mi-box-s-2nd-gen" to "Xiaomi TV Box S الجيل الثاني",
        "ikon-ik-e500ff" to "Ikon IK-E500FF",
        "stctv-box-sei" to "STC TV Box",
    )

    fun label(profileId: String, model: String): String = LABELS[profileId] ?: model

    fun suggestions(p: Profile): Set<PackageName> =
        p.groups.filter { it.default == GroupDefault.DISABLE }.flatMap { g -> g.packages.map { it.name } }
            .filter { it !in p.neverDisable && it != p.liveTv && it != p.setupWraith && Denylist.staticReason(it.value) == null }
            .filter { pkg -> p.stockLaunchers.none { it.pkg == pkg } }
            .toSet()
}
