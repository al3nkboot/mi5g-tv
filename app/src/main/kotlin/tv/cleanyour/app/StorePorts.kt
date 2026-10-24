package tv.cleanyour.app

import tv.cleanyour.core.flows.StoreRules
import tv.cleanyour.core.flows.UpdateChecker
import tv.cleanyour.core.registry.ArtifactFetcher
import tv.cleanyour.core.registry.HttpFetcher

// The ports that differ between the two store builds (STORES-PLAN 1). Each flavour's source set
// defines the object `Store` with them, and AppGraph wires it; nothing else in the app knows which
// build it is.
//   app/src/full  GitHub and F-Droid: the GitHub hosts, the update check, the add-on downloads.
//   app/src/play  Google Play: no host, no update check (Play updates the app), no add-on downloads
//                 (Play allows no app downloads from outside Play), and the TV's software updaters
//                 always stay on (the "updates go off" choice does not exist).
// Each flavour's res/xml/network_security_config.xml lists exactly `hosts`.
interface StorePorts {
    val flavour: String
    // The only hosts the app's HTTP client and add-on fetcher may reach (D41); a subset of
    // DownloadHosts.ALL, empty in the Play build.
    val hosts: Set<String>
    // What the flows offer: "Check for updates" and the add-ons (Cards and modules, Done's card).
    val rules: StoreRules
    // False: the planner keeps every software updater on (DefaultPlanner keepUpdatesOn); the same
    // as rules.updatesOff, which the Switched off screen follows.
    val updatesMayGoOff: Boolean
    // Where "an update is available" opens; null when there is no update check.
    val releasePage: String?
    fun updateChecker(http: HttpFetcher): UpdateChecker
    // The add-on fetcher: `make` builds the real one (InstallableOnlyFetcher over the resumable
    // fetcher); a build without add-ons returns one that never downloads.
    fun addOnFetcher(make: () -> ArtifactFetcher): ArtifactFetcher
}
