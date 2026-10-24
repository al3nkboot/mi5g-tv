// FROZEN INTERFACE (PRODUCT-SPEC section 5.10, after gate R0). Names and signatures are binding;
// a change needs a one-paragraph RFC in the repo and Fable sign-off. Copied verbatim by W1; every
// edit to the copied text is listed in DECISIONS-W1.md. Owner: W6.

package tv.cleanyour.core.backup

import tv.cleanyour.core.model.*
import tv.cleanyour.core.spec.FactsTable
import java.io.OutputStream

// SnapshotStore is a storage seam in :core:model (section 5.13, R0: 1); W6 implements it here over DocumentStore
interface RestoreBundleWriter { suspend fun write(tv: Tv, baseline: Snapshot, changes: List<ChangeRecord>, overlay: ByteArray?, wallpaper: ByteArray?, sink: OutputStream) }
interface TvRestoreFiles { fun restorePointJson(tv: Tv, changes: List<ChangeRecord>): ByteArray; fun restoreTxt(tv: Tv, changes: List<ChangeRecord>, profile: Profile?): ByteArray }
interface RestoreNote { fun text(tv: Tv, changes: List<ChangeRecord>, facts: FactsTable, stockHomeName: String, dateText: String): String }
