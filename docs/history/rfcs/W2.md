# RFCs from W2 (ADB transport and signer)

For the Fable reviewer at gate R0/R2. None changes a section 5 signature; each records an additive
member or an opt-in that section 5 and CONTRIBUTING-agents.md did not foresee.

## RFC-W2-1 `@RawShell` opt-in inside `:adb:session` (`SessionLines`)

What: `:adb:session` builds five constant `ShellLine`s itself: `getprop` (the D37 identity read at
every connect, R0: 12), `cat /proc/sys/kernel/random/boot_id` and `pm path android` (the boot wait
of ARCHITECTURE 3.10), `reboot` (`TvSession.reboot()`), and `sha256sum '<RemotePath>'` (the push
verify, D39; the path grammar admits only `[A-Za-z0-9._-]` segments under a fixed root). They live
in one `internal object SessionLines` with the only opt-in in the module. Why: the session must
read identity before any caller's line is sent (D37: "no line is ever sent" for a wrong TV), so it
cannot take these lines from `:core:probe` (layer 2 beside it) or `:core:ops`. Affected: W3's
single-call-site lint for `exec` and the `@RawShell` opt-in list (`:core:ops`, `:core:probe`, the
Plan codec) gain `tv.cleanyour.adb.session.SessionLines`. Smallest alternative: move these five
lines into `:core:model` as named constants, which spreads raw lines into the shared model instead.

## RFC-W2-2 Two additive members on `AdbConnection`

What: `val isClosed: Boolean` and `fun invokeOnClose(handler: (AdbError) -> Unit)`, plus the
private constructor now wraps an internal `ConnectionCore` (section 5.3 elides the constructor as
"sketch; W2 owns the shape"; `features` and `maxPayload` keep their names and types). Why: the
session must learn that the message pump died (TV gone, half-open socket cut by the ping) without
polling, to move to `Reconnecting` while a hold is open and to `Idle` otherwise. Affected: nobody
outside W2 calls `AdbConnection`. Alternative: the session polls by opening a ping stream, which
costs a round trip per check.

## RFC-W2-3 (documentation) `AdbSessionManager` constructor

Not a section 5 type: `AdbSessionManager(socketFactory, signer, tls = null, clock = SystemClock,
scope = Default + SupervisorJob, blobIo = Dispatchers.IO)`. W8's `AppGraph` builds it with
`PlainSocketFactory()`, `KeystoreAdbSigner.create(context)`, `NotSupportedTlsUpgrader` (or null),
its `Clock`, and an application scope. `blobIo` exists so tests read blob files on the test
dispatcher; production keeps the default.
