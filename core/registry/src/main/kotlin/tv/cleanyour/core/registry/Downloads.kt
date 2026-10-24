package tv.cleanyour.core.registry

import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.Sha256
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// How an HttpFetcher reports a request it refused or could not make. Anything else it throws that
// is an IOException means the network is gone (OFFLINE).
class FetchException(val failure: FetchFailure, val httpCode: Int? = null, message: String, cause: Throwable? = null) : IOException(message, cause)

// Resumable partial downloads, keyed by the sha256 the index promises (ARCHITECTURE 5.1:
// cache/artifacts/<sha256>.part plus <sha256>.etag). DiskBlobStore implements it next to BlobStore,
// so a finished partial becomes a blob by a rename.
interface PartialStore {
    fun partialLength(sha256: Sha256): Long
    fun partialEtag(sha256: Sha256): String?
    fun startPartial(sha256: Sha256, etag: String?)          // empty the partial and remember the validator
    fun appendPartial(sha256: Sha256): OutputStream
    fun readPartial(sha256: Sha256): InputStream
    suspend fun commitPartial(sha256: Sha256, size: Long): BlobRef   // re-checks size and sha256, then moves it into the store
    fun dropPartial(sha256: Sha256)
}
