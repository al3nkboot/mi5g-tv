package tv.cleanyour.core.probe

import tv.cleanyour.core.model.PackageName
import tv.cleanyour.core.session.TvSession

// Not part of the frozen Prober (section 5.5): the packages with an activity a TV launcher
// (LEANBACK_LAUNCHER) or a phone launcher (LAUNCHER) would show, which is what the dock sheet may
// offer. One read-only call (ProbeScripts.LAUNCHABLE). Fails with AdbException or ProbeException
// like the Prober; a caller that cannot tell falls back to its own rule.
fun interface LaunchableApps {
    suspend fun launchable(session: TvSession): Set<PackageName>
}

// `cmd package query-activities --brief` prints a "priority=N ..." line, then the component on the
// next line; "No activities found" prints nothing usable. Only component lines count.
object LaunchableParser {
    fun parse(lines: Sequence<String>): Set<PackageName> {
        val out = LinkedHashSet<PackageName>()
        for (raw in capped(ProbeStage.LAUNCHER, lines)) {
            val l = raw.trim()
            if (l.startsWith("priority=") || '/' !in l || ' ' in l) continue
            component(l)?.let { out += it.pkg }
        }
        return out
    }
}
