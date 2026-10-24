# W2 decisions: ADB transport and signer

Stream W2, 2026-10-19. Built against product spec section 5.3 as fixed by gate R0. RFCs are in
`RFCS.md`. The real Mi TV check (F1, F2) is a runbook: `docs/W2-device-check.md`.

## Vendored code

- dadb 2.0.0 is vendored, not depended on (D11): `:adb:wire` `Protocol.kt`, `Connection.kt`,
  `Streams.kt` and `AdbAuth.kt` derive from its `Constants`, `AdbMessage`, `AdbReader`,
  `AdbWriter`, `AdbConnection`, `AdbStream`, `MessageQueue`, `AdbShell`, `AdbSync` and
  `AdbKeyPair`. Each file keeps the Apache-2.0 header with mobile.dev's copyright and says what
  changed; `adb/wire/NOTICE` carries the licence and the file map. Nothing of dadb's raw-RSA signer
  survives.

## Wire (`:adb:wire`)

- D-W2-1 Protocol version 0x01000000 with checksums computed and verified both ways (what dadb
  speaks; proven on the Mi TV in spike S2). We advertise and accept at most 1 MiB per WRTE.
- D-W2-2 The reader bounds every length before allocating (SECURITY N7): WRTE 1 MiB; CNXN, AUTH and
  OPEN 256 KiB; OKAY, CLSE, STLS and SYNC zero. Bad magic, unknown command, bad checksum, truncated
  header or payload are `Transport.Protocol`. Shell v2 packets are bounded at 1 MiB, an exit packet
  must be exactly 1 byte, only stdout, stderr and exit ids are accepted from the TV. Sync DATA is
  bounded at 64 KiB, FAIL messages at 4 KiB, paths at 1 KiB; a pull over its cap stops before the
  extra bytes are kept.
- D-W2-3 Flow control: one bounded inbox of 4 WRTE frames per stream; OKAY is sent when the consumer
  takes a frame, so a well-behaved adbd never has more than one in flight. A peer that overfills
  the inbox is cut off (`Protocol`, the whole connection). Our writes wait for OKAY per frame and
  are chunked to the peer's window.
- D-W2-4 Handshake: CNXN, then AUTH. The first token is signed through `AdbSigner.sign(AuthToken)`;
  the token is checked to be 20 bytes before an `AuthToken` is built and again inside every signer.
  A second token means the key is unknown: the public key goes out once, `onAwaitingAllow`
  fires with the fingerprint, and the wait becomes `firstAuthTimeoutMs` (120 s, then
  `Auth.PendingTooLong`). A third token is `Auth.Denied`. Every other handshake step has 5 s. STLS
  is refused in 0.1 (`NotSupportedTlsUpgrader`, D9/Q5). Streams opened by the TV are answered with
  CLSE. A banner must say `device::`, and a peer window under 4 KiB is refused.
- D-W2-5 Services are spelled only by the connection (R0: 3): `shell,v2,raw:`, `sync:`,
  `exec:cmd package install -S <size>` (size 1 to 200 MiB, needs the `cmd` feature), `exec:echo`.
  A shell on a TV without `shell_v2` is `Transport.NoShellV2`; there is no legacy `shell:` path.
- D-W2-6 No general signing oracle: signing is JCA `NONEwithRSA` over the 35-byte
  DigestInfo(SHA-1) || token (spike S2 mode 2), never a raw RSA cipher over caller bytes.
  `AdbSigner.selfTest()` signs a random token and verifies it in adbd's terms, checks the D20
  comment, the struct decode and the fingerprint. `SoftwareRsaAdbSigner` is the JVM engine used by
  tests and the laptop check.

## Session (`:adb:session`)

- D-W2-7 Single flight: one mutex per session; every call (exec, lines, push, pull, install,
  reboot, awaitBoot) waits for the one before it. `Transport.Closed` is retried once for
  `IDEMPOTENT_READ` and `IDEMPOTENT_WRITE`, never for `NEVER`; timeouts are never retried.
- D-W2-8 Two clocks per exec (`exec-total`, `exec-idle`), two caps (stdout and stderr, `truncated`
  set). `lines()` cuts each line at 8 KiB and fails the flow over `stdoutCap`, since a parser must
  never see a silently shortened listing; a non-zero exit fails it with `NonZeroExit`.
- D-W2-9 `pm` is never chained (SECURITY 3.5): `PmGuard` refuses, before anything is sent, a line
  that holds a `pm`/`cmd package` write verb together with `;`, `&`, `|`, a newline, a backtick or
  `$(`, as `Policy.GuardFailed(0, reason)`. The renderer never emits such a line; this is the
  session's backstop, and the fake adbd refuses the same lines (exit 126) and records a violation.
- D-W2-9a The one exception (INTEGRATION-PLAN T4, D14, SECURITY 3.6): W3's HOME watchdog. `PmGuard`
  admits a chained pm line only when `PmGuard.WATCHDOG_RE` matches the whole line. The pattern is
  the two template shapes of W3's `Watchdog` (variant A `nohup setsid sh -c '...'`, variant B
  `( sh -c '...' & )`) written out in `:adb:session` (no `:core:ops` dependency) with only these
  variable parts: seconds `[0-9]{2,3}`, the token `[0-9a-f]{16}` (one value, enforced by
  backreference in all three places), the stock package from `PACKAGE_RE` and the component in
  that same package (backreference) with a class from `CLASS_RE`. Every other byte is fixed.
  `PmGuardWatchdogTest` reads copies of W3's goldens 16 and 17 (source and commit named in each
  file), asserts both are admitted, that one-character mutations (an extra `;`, a quote, a changed
  path, a token or package that differs in one place, a pipe) and mixed shapes are refused, that a
  plain `pm disable-user ...; pm enable ...` is refused, and that the session sends the watchdog to
  the fake while a mutated line never leaves the phone. If W3 changes the template, this pattern
  and the copied goldens change with it (R1 and R2 both read it).
- D-W2-10 Identity (D37, R0: 12): every connect, the manager's and every reconnect, reads `getprop`
  before anything else and computes `TvId` = first 16 hex of sha256(`ro.serialno` +
  `ro.product.device`) on the values exactly as getprop prints them (no separator, no trimming),
  byte-compatible with W6's `restore.sh`. `IdentityAndQueueTest.tvIdFormulaIsPinned` pins vectors
  computed with `printf | shasum -a 256`. A different TV is `Policy.WrongTv` and its socket is
  closed with nothing else sent.
- D-W2-11 Holds: the last hold closed starts a 60 s idle close; while held, `exec:echo` every 20 s
  (5 s to answer) and a background reconnect when the socket dies. Reconnect: 5 attempts, full
  jitter backoff from 500 ms doubling to 8 s; `Auth`, `Policy` and `NoShellV2` stop the loop at once.
- D-W2-12 Transfers: push streams the blob through sync with its sha256 checked while streaming and
  `sha256sum` read back (D39); 600 s total, 15 s idle, 200 MiB cap, mode 0644. Pull: 120 s total,
  10 s idle, the caller's byte cap. Streamed install: `exec:cmd package install -S`, 60 s plus 50 KB/s
  of size, 30 s idle, the result parsed as `Installed` or `Failed(code, detail)`. A sync FAIL is
  `NonZeroExit(1, message)`.
- D-W2-13 Boot: `reboot()` reads the boot id, sends `reboot`, and treats the socket dying as
  success. `awaitBoot(timeoutMs)` is relative to the call (R0: 16): waits 10 s, then polls with
  backoff 2 s to 10 s for a fresh connect with the same identity, `sys.boot_completed=1`,
  `pm path android` succeeding and a new boot id.

## Fake adbd (`:testing:fake-adbd`)

- D-W2-14 In-process adbd over a coroutine socket pair (virtual time for every timeout) plus a
  loopback TCP front for `PlainSocketFactory`. Real AUTH (it verifies signatures with the
  advertised key), shell v2, sync, `exec:`; a shell interpreter whose `pm`, `cmd package`,
  `settings`, `am` and file commands change a `FakeTv`, so lists after a write are real. Personas:
  the Mi TV from the redacted capture (getprop, package lists, settings and HOME byte for byte),
  synthetic TCL G05 and Chromecast (marked `synthetic`). Fault knobs: latency, jitter, bandwidth,
  drop after N bytes, half-open, stalled push, hostile flow control, pm delay and busy, hanging
  install, single-threaded, Allow behaviour, garbage before and after CNXN, split packets, wrong
  TV on reconnect, reboot timings, STLS-only, refused opens.

## Android (`:android:platform`, W2 files only)

- D-W2-15 `KeystoreAdbSigner`: RSA-2048 `PURPOSE_SIGN`, `DIGEST_NONE`, PKCS#1 padding, alias
  `cleanyourtv.adb.rsa.v1`, StrongBox first, TEE on failure, then a software key sealed by
  `SecretBox` (AES-256-GCM, alias `cleanyourtv.wrap.v1`, StrongBox when available) in
  `noBackupFilesDir`. A path is used only after its self-test passes; the reasons a path was
  skipped are kept as provisioning notes for the About screen. `reset()` deletes both aliases and
  the sealed file. These classes need the Android Keystore, so they are exercised on ART (W8/W9
  conformance on the GMD images, and the device runbook), not on the JVM.
- D-W2-16 `PlainSocketFactory`: literal IPv4/IPv6 only (no name resolution), TCP_NODELAY and
  SO_KEEPALIVE, I/O on `Dispatchers.IO`; TLS endpoints are refused in 0.1.

## Tests and fuzzing

- D-W2-17 Jazzer is not run: the forge build container has no Jazzer agent or native driver.
  `HostileFrameFuzzTest` is the equivalent bounded random-frame property test (random and mutated
  frames into the frame decoder, the shell v2 reader, the sync reader and a live connection; only a
  value, end of stream or `AdbException` is acceptable). It runs 15 s in `check`;
  `-Pcleanyourtv.fuzzSeconds=600` gives the 10-minute run, `-Pcleanyourtv.fuzzSeed=N` replays.
- D-W2-18 Every test class has a JUnit `Timeout` rule (60 s; the fuzz class scales with its
  budget), so a hang can never hold the shared forge lock. The 5-minute Gradle test timeout comes
  from build-logic on main; `:adb:wire` only raises it, by twice the fuzz budget, when a fuzz run
  longer than the default 15 s is asked for.
