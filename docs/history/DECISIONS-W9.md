# W9 decisions: release, CI, e2e, perf, runbook

Stream W9, 2026-10-19. Built against product spec section 5 as fixed by gate R0 and W1's tree.
No frozen signature changed and no RFC was needed.

## What W9 delivers and where

| Deliverable | Files |
| :- | :- |
| TV e2e harness | `e2e/` (`:e2e`): `test` source set in `check`; `emulatorTest` source set with `TvE2eSuite` |
| Emulator sessions | `docker/emulator/Dockerfile`, `scripts/emulator-session.sh` (any KVM host or CI), `scripts/emulator-on-forge.sh` (forge), `scripts/emulator-smoke.sh` |
| e2e fixtures | `e2e/fixtures/crashing-launcher/` (S10), `scripts/build-e2e-fixtures.sh` (that plus the upstream launcher at the pinned commit) |
| Benchmarks | `:benchmark` (`StartupBenchmark`, `BaselineProfileGenerator`), build-logic `cleanyourtv.android.benchmark`, the app's `benchmark` build type |
| Reproducible release | `docker/repro/Dockerfile`, `scripts/repro-build.sh`, `scripts/sign-and-compare.sh`, `scripts/build-internal-apk.sh` (forge), `.github/workflows/release.yml` |
| APK and device assertions | `scripts/check-apk.py` (aapt2, apksigner), `scripts/check-installed.sh` (dumpsys, pulled APK) |
| Redaction check | `scripts/check-redaction.py`, `scripts/tests/test_check_redaction.py` |
| Nightly | `.github/workflows/nightly.yml`, `scripts/dependency-report.py` |
| SBOM | `scripts/sbom.py` (CycloneDX 1.5 from the release runtime classpath and the verification hashes) |
| Dependency verification | `gradle/verification-metadata.xml`, `scripts/update-verification-metadata.sh`, `scripts/verification/resolve-catalog.init.gradle.kts` |
| Runbook | `docs/runbook-0.1.md` |

## Decisions taken inside W9

- D-W9-1 Forge has `/dev/kvm` (AMD, `svm`), 12 cores and about 7 GB free beside the model server.
  The API 28 Android TV emulator runs there in a container capped at 3 GB (`--memory 3g
  --memory-swap 3g`), holding the same host lock as `build-on-forge.sh`, so no Gradle build runs
  beside it. Guest RAM 1024 MB, the Mi TV 4A's. Gradle compiles the harness and builds the fixture
  APKs before the emulator boots and is stopped before it; the scenario JVM gets a 384 MB heap.
  Every session is torn down by trap (emulator killed through its console, container removed,
  remote checkout deleted). Cold boot takes about 30 s with `-no-snapshot -wipe-data`. The
  emulator SDK (3.7 GB with the API 28 image) lives in its own volume `cyt-emulator-sdk`, so the
  shared build SDK volume is untouched.
- D-W9-2 The harness talks to the emulator's adb port (console port + 1, `127.0.0.1:5555`), which
  speaks the ADB protocol over TCP like a TV with network debugging on (ARCHITECTURE 6.6).
  `adb tcpip 5555` is not used: on the API 28 TV image it restarts adbd without a guest TCP
  listener. The host adb server and the harness are served at the same time.
- D-W9-3 The API 28 Android TV image (`sdk_google_atv_x86`, r10) is a userdebug build with
  `ro.adb.secure=0`: adbd answers CNXN without AUTH, so the emulator cannot exercise the Allow
  dialog or the AUTH signature path. Those stay with the fake adbd (W2) and the Mi TV session.
  The harness still signs with the host key when a device asks (`AdbdProbe`, tested in `check`
  against an in-process adbd that verifies the signature). The image has the setupwraith package
  and two HOME claimants (`tvlauncher`, `leanbacklauncher`), so the takeover scenarios meet the
  same quirks as a real Google Android TV.
- D-W9-4 The oracle is the platform's own adb client (`HostAdb`), never our code: scenarios drive
  `:adb:session`, `:core:probe`, `:core:planner` and `:core:engine`, then ask `HostAdb` whether
  the TV agrees. It also does setup the app never does (installing fixtures, clean-up).
- D-W9-5 Scenarios are written against the frozen interfaces only. The single meeting point with
  the implementations is `E2eWiring` (each function returns null until its stream merges); a null
  piece, or a frozen stub that still throws `TODO("Wn")`, makes the scenario skipped with
  "PENDING: <piece>", never passed. nightly.yml lists the PENDING lines in the job summary.
- D-W9-6 Timing numbers (connect, probe, debloat per package scaled to 40, push MB/s, watchdog
  restore time, startup) are recorded against the ARCHITECTURE 4 rows as JSON lines
  (`timings-api<N>.jsonl`) and never fail a run in 0.1 (PRODUCT-SPEC 7.2). Two numbers are
  assertions because they are properties, not speeds: the probe uses fewer than 10 shell calls
  (W4's definition of done) and the watchdog does not restore before 100 s.
- D-W9-7 The S10 fixture is a launcher with package `com.gathin.tvhome` that starts normally by
  name (so the takeover precondition `am start -W` passes) and throws when started as HOME, the
  failure the guarded section must survive. Plain Java, no AndroidX, debug signed by the building
  machine; its signer digest is read with apksig at run time.
- D-W9-8 Until W5's bundled launcher is wired, install and theme scenarios use the upstream
  launcher built from the submodule at the pinned commit with its own wrapper (Gradle 8.9, AGP
  8.5.2), as upstream builds it.
- D-W9-9 Reproducible release: the input is `git archive` of the tag plus the submodule at the
  commit the tag pins (no `.git`, no working files), unpacked at `/src` in both builds. Build A
  uses the shared Gradle cache, build B an empty Gradle home of its own, so a poisoned cache
  cannot hide a difference; both run without build cache or configuration cache. The unsigned
  outputs must be byte identical; then A is signed and `apksigcopier compare` checks it against B.
  The app's release build type now sets `vcsInfo.include = false`, so a clone and an archive
  build the same bytes. `SOURCE_DATE_EPOCH` is the tagged commit's time.
- D-W9-10 Signing happens after the comparison, outside Gradle: the key never enters a build
  container. On forge the keystore and its password file are streamed over ssh into a tmpfs of
  the signing container and vanish with it; in CI they come from the `release` environment's
  secrets into `/dev/shm`. v2 and v3, v1 off; a rotation lineage is passed when one exists (the
  first key has none). apksigner gets `--min-sdk-version 24` because with 28 it writes v3 alone
  (Android 9 verifies v3 itself) while 7.3 asks for v2 and v3, and `--alignment-preserved` so the
  signed zip is the built zip plus the signing block, which `apksigcopier compare` needs. The
  password file is passed once (`--ks-pass`): given twice, apksigner reads the key password from
  its second line. Verification uses `--min-sdk-version 24` too, else `apksigner verify` checks
  only v3 and prints v2 as false. The app release key does not exist yet: the user generates it
  offline (runbook section F); `--test-key` proves the pipeline with a throwaway key generated in
  the container and names every output `...-TESTKEY-not-for-distribution`.
- D-W9-11 `check-apk.py` counts exported components the way the manifest declares them. The
  ProfileInstaller receiver is exported behind `android.permission.DUMP` (only the shell and the
  system hold it) and is reported beside the count, not in it. androidx.core's own
  `tv.cleanyour.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature level) is accepted next
  to the D16 set. Both are for the R6 reviewer to confirm.
- D-W9-12 SBOM without a Gradle plugin: `scripts/sbom.py` turns `:app:dependencies
  --configuration releaseRuntimeClasspath` plus the verification metadata's SHA-256 values into
  CycloneDX 1.5, deterministic (sorted, timestamp from `SOURCE_DATE_EPOCH`). A CycloneDX plugin
  would have added build dependencies to verify and a moving part to the release build.
- D-W9-13 Dependency verification (sha256, signatures off) covers every configuration the build
  paths resolve (check, debug, release and benchmark APKs, the benchmark test APK, the e2e
  scenarios, the SBOM classpath) plus every library in the version catalog, resolved leniently by
  an init script, so streams that start using a catalogued library at merge do not trip it.
  A new or bumped dependency needs `scripts/update-verification-metadata.sh` in the same change.
  The fixture builds (`e2e/fixtures/crashing-launcher`, the upstream launcher) are separate builds
  without verification; they are test inputs, never shipped. The update script runs with
  `--refresh-dependencies` and no caches: a warm build takes the settings plugin (foojay) and the
  build-logic classpath from its caches without recording them, and the first clean release build
  then failed on `kotlinx-coroutines-bom-1.8.0.pom` until they were recorded.
- D-W9-14 `:benchmark` is `com.android.test` with only the `benchmark` variant enabled, against
  the app's `benchmark` build type (release code, R8, debug signed, `profileable shell`). The
  baseline profile is generated by `BaselineProfileRule` and copied into
  `app/src/main/baseline-prof.txt` by nightly.yml as a pull request; the
  `androidx.baselineprofile` Gradle plugin is not applied, so no extra app variants exist and
  release builds read only the committed file.
- D-W9-15 The emulator e2e and the macrobenchmark run in nightly CI as well as on forge; on forge
  only short API 28 sessions run (one at a time, by hand). API 34 runs in nightly only.

## Evidence

- 2026-10-19, forge, `scripts/emulator-on-forge.sh run 28`: boot 30 s; the e2e fixtures built
  inside the session container before boot (upstream launcher sha256 `d2b89c57...4170`, crashing
  launcher `c14173ba...c32c`, both build outputs, not committed); `TvE2eSuite`: S00 4 of 4 passed
  (oracle sees the emulator; CNXN on `127.0.0.1:5555`, banner `device::...features=stat_v2,cmd,shell_v2`,
  maxPayload 262144, the echo stream round trip, connect 9 ms; two concurrent connections; W1's
  `AdbKeyFormat` produces exactly the public key line the platform's adb wrote for the same key),
  17 scenarios PENDING on W2 to W5; container, emulator and remote checkout gone afterwards.
- The API 28 image's package list and HOME resolution are kept as
  `e2e/src/emulatorTest/resources/captures/` (67 packages, no device identifiers: it is an SDK image).
- 2026-10-19, forge: strict dependency verification on. `check assembleDebug :app:assembleRelease
  :app:checkApkSizeRelease :benchmark:assembleBenchmark :e2e:emulatorTestClasses` green with the
  metadata (739 components). Tamper test: one changed hex digit in the recorded sha256 of
  `junit-4.13.2.jar` made `:core:model:compileTestKotlin` fail with "Dependency verification
  failed ... junit-4.13.2.jar" (SECURITY 6.4 item 8, T-S4); the file was restored.
- 2026-10-19, forge: `scripts/build-internal-apk.sh <tag> --test-key` from a scratch clone with a
  throwaway tag (no tag was created in the shared repository): build A (shared cache) 21 s, build B
  (empty Gradle home) 3 min 50 s, both unsigned APKs `2dc31b81...9ee3`, byte identical;
  `apksigcopier compare: match`; signed v2 and v3, v1 off; signed APK sha256 `7d397d95...6ade`
  printed and written to the release notes. `check-apk.py` passes 12 of 15: the three failures are
  the skeleton manifest W8 has not written yet (D16 permissions, network security config, the one
  exported launcher activity), so the gate works and the APK is correctly "not releasable".
