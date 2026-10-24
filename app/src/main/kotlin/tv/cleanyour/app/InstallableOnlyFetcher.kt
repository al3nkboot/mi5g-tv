package tv.cleanyour.app

import tv.cleanyour.core.model.ArtifactRef
import tv.cleanyour.core.registry.ArtifactFetcher
import tv.cleanyour.core.registry.FetchFailure
import tv.cleanyour.core.registry.FetchResult
import tv.cleanyour.core.trust.VerifiedIndex
import tv.cleanyour.core.trust.installable

// R5-W5 F8 at the app's seam: a module APK is downloaded (and so installed) only when the index the
// registry may install from right now (IndexRegistry.installableIndex: verified, not stale, not
// the last list behind a failed refresh) offers that exact artifact, its module passes
// VerifiedIndex.installable (not revoked, not quarantined) and neither the APK nor its signer is on
// the revocation list. `Registry.module(id)` also answers from a stale or failed list; this keeps
// that answer from ever reaching the network or the TV, whatever the machine asked with. The
// fetched APK's package, versionCode and signer are then compared with the entry by the machine.
class InstallableOnlyFetcher(
    private val delegate: ArtifactFetcher,
    private val installableIndex: () -> VerifiedIndex?,
) : ArtifactFetcher {
    override suspend fun fetch(ref: ArtifactRef, onProgress: (Long, Long) -> Unit): FetchResult {
        val index = installableIndex() ?: return refused
        if (!index.offers(ref)) return refused
        return delegate.fetch(ref, onProgress)
    }

    companion object {
        // No download was attempted; the frozen FetchFailure set has no "list out of date" case.
        val refused = FetchResult.Failed(FetchFailure.HTTP_ERROR, null)
    }
}

fun VerifiedIndex.offers(ref: ArtifactRef): Boolean {
    val revoked = revocations.toSet()
    if (ref.sha256 in revoked || ref.signer in revoked) return false
    return modules.any { m -> installable(m) != null && m.versions.any { it.artifact == ref } }
}
