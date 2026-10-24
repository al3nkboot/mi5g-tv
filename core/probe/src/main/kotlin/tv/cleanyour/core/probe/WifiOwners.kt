package tv.cleanyour.core.probe

import tv.cleanyour.core.session.TvSession

// Not part of the frozen Prober (section 5.5), like LaunchableApps: who saved the TV's Wi-Fi
// networks, as `dumpsys wifi` names them (cname=<package or shared uid>:<uid>). Owner decision D3,
// launcher v2.4.0's tools/disable.sh guard: a network saved during the first-run setup wizard
// belongs to the wizard, and Android deletes it the moment the wizard is switched off, taking the
// TV off Wi-Fi mid-run. One read-only call; only fixed words leave the TV: the cname owners, the
// two markers of WifiConfigManager's list of saved networks, and "ID: <n> SSID" for each saved
// network (the match stops before the network's name). Fails with AdbException like the Prober.
fun interface SavedWifiOwners {
    suspend fun savedWifi(session: TvSession): SavedWifi
}

// `listed`: the dump held WifiConfigManager's list of saved networks, start and end ("Configured
// networks Begin/End", Android 8 to 14). `networks`: entries in it. `owners`: every cname seen.
data class SavedWifi(val listed: Boolean, val networks: Int, val owners: Set<String>) {
    enum class Verdict { WIZARD_OWNS, OTHERS_OWN, NOTHING_SAVED, CANNOT_TELL }

    // RF-LAUNCHER-240 F6, F14: the wizard keeps its network when it owns one; with nothing saved
    // (a cable, or no network) there is nothing to lose; when the list is missing (dumpsys failed,
    // an unknown format) or a saved network names no owner, the TV could not tell.
    fun verdict(wizard: String): Verdict = when {
        owners.any { o -> o.substringBefore(':') == wizard || "setupwraith" in o.lowercase() } -> Verdict.WIZARD_OWNS
        !listed -> Verdict.CANNOT_TELL
        networks == 0 && owners.isEmpty() -> Verdict.NOTHING_SAVED
        owners.isEmpty() -> Verdict.CANNOT_TELL
        else -> Verdict.OTHERS_OWN
    }
}

object WifiOwnersParser {
    // A package name or shared user id, then an optional :uid. Anything else is dropped, so nothing
    // but an owner can come through.
    private val OWNER = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+(:[0-9]{1,7})?$")
    private val NETWORK = Regex("^ID: -?[0-9]+ SSID$")

    fun parse(lines: Sequence<String>): SavedWifi {
        val owners = LinkedHashSet<String>()
        var begin = false; var end = false; var networks = 0
        for (raw in skipLong(lines)) {
            val t = raw.trim()
            when {
                t == "Configured networks Begin" -> begin = true
                t == "Configured networks End" -> end = begin
                NETWORK.matches(t) -> if (begin && !end) networks++
                t.startsWith("cname=") -> t.removePrefix("cname=").substringBefore(' ').takeIf { OWNER.matches(it) }?.let { owners += it }
            }
        }
        return SavedWifi(begin && end, networks, owners)
    }
}
