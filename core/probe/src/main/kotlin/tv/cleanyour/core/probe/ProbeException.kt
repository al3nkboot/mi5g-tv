package tv.cleanyour.core.probe

// Named in the section 5.5 prose (R0: 7): a parser failure. Prober methods otherwise fail with
// AdbException like the session they wrap. Owner: W4.
class ProbeException(val stage: ProbeStage, val detail: String) : Exception("$stage: $detail")
