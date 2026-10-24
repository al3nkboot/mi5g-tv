// FROZEN INTERFACE (PRODUCT-SPEC section 5.11, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W5.

package tv.cleanyour.core.registry

import tv.cleanyour.core.model.*
import tv.cleanyour.core.trust.IndexFailure
import tv.cleanyour.core.trust.VerifiedIndex
import java.io.InputStream
import kotlinx.coroutines.flow.StateFlow

sealed interface IndexState { data object Empty : IndexState; data class Ready(val index: VerifiedIndex, val stale: Boolean, val fetchedAtMs: Long) : IndexState
    data class Failed(val failure: FetchFailure?, val verdict: IndexFailure?, val last: VerifiedIndex?) : IndexState }   // R0: 13
interface Registry { val index: StateFlow<IndexState>; suspend fun refresh(): IndexState; fun module(id: String): Module? }
interface HttpFetcher { suspend fun get(url: String, etag: String?, rangeFrom: Long?): HttpResponse }      // host allowlist enforced inside
data class HttpResponse(val code: Int, val etag: String?, val body: InputStream?, val length: Long?)
enum class FetchFailure { OFFLINE, HOST_NOT_ALLOWED, HTTP_ERROR, TOO_LARGE, HASH_MISMATCH, SIZE_MISMATCH, SIGNER_MISMATCH }   // R0: 13
sealed interface FetchResult { data class Ok(val blob: BlobRef) : FetchResult; data class Failed(val failure: FetchFailure, val httpCode: Int?) : FetchResult }   // R0: 13
interface ArtifactFetcher { suspend fun fetch(ref: ArtifactRef, onProgress: (Long, Long) -> Unit): FetchResult }   // sha256 and size while streaming, signer by ApkInspector afterwards
// BlobStore is a storage seam in :core:model (section 5.13, R0: 1); W5 implements it as FileBlobStore
