# W1 decisions: foundation and fakes

Stream W1, 2026-10-19. Built against product spec section 5 as fixed by gate R0 (verdict FREEZE
WITH FIXES). Every pin below was resolved from Google Maven, Maven Central or the Gradle Plugin
Portal by a green `./gradlew check assembleDebug :app:assembleRelease :app:checkApkSizeRelease` on
forge (`scripts/build-on-forge.sh`, `eclipse-temurin:21-jdk`, 3 GB cap) on that date.

## Toolchain pins (D34)

| Item | Pin | Note |
| :- | :- | :- |
| Android Gradle Plugin | 9.4.1 | As ARCHITECTURE 7.2. 9.5.0 is alpha only |
| Gradle | 9.7.1 | The spec says "the 9 line AGP 9.4 requires" without a number. 9.7.1 (2026-08-19) is the newest stable that predates both AGP 9.4.0 (2026-09-01) and Kotlin 2.4.20 (2026-09-07), so both were released against it; 9.8.x came later. Wrapper pinned by `distributionSha256Sum` |
| Kotlin (and the Compose compiler and serialization plugins) | 2.4.21 | As ARCHITECTURE 7.1. 2.5.0 is Beta |
| JDK | 21 (Temurin) | Gradle runs on it; JVM modules use a JDK 21 toolchain; Android `compileOptions` 21 |
| Compose BOM | 2026.09.00 | UI, foundation, runtime 1.12.1; Material 3 1.4.0, as ARCHITECTURE 7.1 |
| Navigation 3 | 1.2.0 | Catalogued; first used by W8 |
| kotlinx-coroutines | 1.11.0 | |
| kotlinx-serialization | 1.11.0 | 1.12.0 is RC |
| activity-compose 1.13.0, lifecycle 2.11.0, profileinstaller 1.4.1 | as listed | Catalogued for W8 |
| okio 3.18.2, bcprov-jdk18on 1.86 | as listed | Catalogued for W2 and W5 |
| JUnit 4.13.2, Robolectric 4.17, Roborazzi 1.76.0, Turbine 1.2.1, kotest-property 6.2.6, apksig 9.4.1, androidx.test ext junit 1.3.0, uiautomator 2.4.0, baselineprofile 1.5.0 | as listed | Test and build only |
| foojay-resolver-convention | 1.0.0 | Settings plugin |
| compileSdk / targetSdk / minSdk | 37 / 36 / 28 | See RFC-W1-3 |

Every pin named in the spec exists as a stable release; none had to be replaced.

## Decisions taken inside W1

- D-W1-1 Module placement follows section 5 after R0: every shared type and seam is in
  `:core:model` (packages `core.model`, `core.session`, `core.discovery`, `core.store`). There is
  no separate `:core:store` module: the W1 row's "`:core:store` seams" are the `core.store` package
  inside `:core:model`, as section 5.13 says.
- D-W1-2 Layers in `ModuleRules.kt`: model 0; spec, ops, adb wire, adb pairing 1; adb session,
  probe, trust, fake-adbd 2; planner, registry, backup 3; engine 4; flows and testing fixtures 5;
  android modules and e2e 6; ui 7; features 8; app 9; benchmark 10. A module may depend only on a
  lower number. Test configurations may use anything (that is how `:testing:fixtures` reaches every
  module's tests). Rules 2, 3, 5 and 6 of ARCHITECTURE 1.3 are checked too; see RFC-W1-1 for the
  engine row.
- D-W1-3 AGP 9 built-in Kotlin for Android modules (no `kotlin-android` plugin); configuration
  cache and build cache on; Kotlin compiler in process (forge's 3 GB cap).
- D-W1-4 Explicit API mode is off. Section 5 is copied verbatim and has no visibility modifiers;
  strict mode would reject it, warning mode would bury real warnings.
- D-W1-5 `SnapshotCodec` (W1-owned, R0: 15) is implemented in `:core:model` by `SnapshotJson` with
  kotlinx-serialization-json's element API (no compiler plugin): compact JSON, `schema` 1, fixed key
  order, maps sorted by key, sets sorted, components as `pkg/cls`, overlay as base64, and
  `contentHash` = sha256 of the canonical bytes with `"contentHash":""`. `decode` rejects another
  schema and a hash that does not match. This adds serialization-json to `:core:model`
  (ARCHITECTURE 1.2 lists serialization-core only); the allowlist reflects it.
- D-W1-6 APK size gate: `checkApkSize<Variant>` on `:app`, 8 MiB (8,388,608 bytes) hard; a proof
  run may lower it with `-Pcleanyourtv.apkSizeLimitBytes=N`. The release APK of the empty tree is
  226,415 bytes; the gate failed as expected with the limit at 100,000. The debug APK (30 MB,
  unminified Compose) is not gated.
- D-W1-7 `:benchmark` has a build file but no plugin yet: W9 applies `com.android.test` and the
  baseline profile plugin. `:adb:pairing` is empty (0.2).
- D-W1-8 Upstream submodule `third_party/cleanyourtv` from `https://github.com/Gathin23/cleanyourtv.git`
  at `7205769cf97316c923c5b6f8a9903b5c0edf5a30` (the commit the spec names; upstream main has moved
  to `1bbf0c9` since). W5's launcher pin must agree with this commit (ARCHITECTURE 6.2).
- D-W1-9 Fixtures. The Mi TV capture went through `scripts/import-mitv-capture.py`: serials, OEM
  key, `android_id`, Bluetooth address and name replaced; MAC, private IPv4 and SSID patterns
  scrubbed (none were present); the capture's `restore.sh` (host names, addresses) not copied.
  `RedactionTest` keeps it that way. The module card fixtures keep only sources, README, digests
  and the xmltree dump: APKs are build outputs and the throwaway keystores stay outside git
  (ARCHITECTURE 6.8), so their digests are reproducible only with those keys.
- D-W1-10 Fakes are behavioural where it is cheap and matters to other streams: `FakeAdbSigner`
  signs with a real RSA-2048 key in the adbd format (public key struct, MD5 fingerprint), so W2's
  fake adbd can verify it; `FakeEngine` applies the 5.9 start contract (refusal order, hash, gate);
  `FakeHttp` does ETag, Range, If-Range and a host allowlist; `MemorySnapshotStore` stores through
  `SnapshotCodec`; `FakeCredentialGate` is the test-side `@GateIssuer`. `FakeOpRenderer` is not a
  renderer: its lines are `fake ...` and must never reach a golden file or a TV.
- D-W1-11 `Recording` (the store behind `FakeTvSession`) normalises lines by dropping single
  quotes and collapsing spaces, so recordings keyed by plain commands keep working whatever
  quoting W3's renderer settles on.
- D-W1-12 Forge notes: Java in the build container intermittently timed out connecting to
  GitHub's release asset hosts, which the wrapper download and the wrapper task's URL check use.
  The 9.7.1 distribution was fetched once with curl, checked against the pinned sha256 and placed
  in the shared cache; the wrapper keeps `validateDistributionUrl=true` and `retries=2`. The cached
  `sdkmanager` is old (it warns about SDK XML version 4); the script only accepts licences and AGP
  downloads the platform and build tools itself.

## Edits to the verbatim section 5 text

The generator copies each section 5 block into its module with a header, the package line, the
imports it needs and the block body. Edits beyond that, all of them bodies (the preamble says the
owning stream supplies bodies):

- `adb.wire.AdbConnection`: the elided `private constructor(...)` became
  `private constructor(val features: Set<String>, val maxPayload: Int)` (the two member properties
  moved into it, same names and types); `open`, `openStream` and `close` got `TODO("W2")` bodies.
- `core.ops.Protected.isProtected`, `core.spec.DebloatTxtParser.parse`, `DockRanking.pick`,
  `LauncherConfigCodec.decode`, `encode`, `effective`, `builtInDefaults`: `TODO` bodies naming the
  owner (W3, W4).
- `core.store.SnapshotCodec`: bodies delegating to the internal `SnapshotJson` (D-W1-5).
- `core.model.PackageName`: `init` also admits the literal `android` (RFC-W1-2, ruled 2026-10-19).
- `core.spec.LauncherConfigDoc` gains `inputAction: String? = null, moduleCards: Boolean? = null`
  and `EffectiveConfig` gains `inputAction: String, moduleCards: Boolean`, both after the existing
  parameters and marked `// RFC-W4-1` (accepted 2026-10-19; PRODUCT-SPEC 5.7 carries the RFC).
- `core.planner.PlanRequest.Setup` gains `config: BlobRef? = null, wallpaper: BlobRef? = null`, after
  the existing parameters and defaulted so every caller compiles, marked `// RFC-DOCK-1` (applied on
  setupcfg 2026-10-20, pending Fable review; RFCS.md carries the RFC). PRODUCT-SPEC 5.8 shows the
  two fields since cardsoptin (RF-CARDS-1 F5).
- `core.planner.PlanRequest.AddModule` gains `config: BlobRef? = null, wallpaper: BlobRef? = null`,
  after the existing parameters and defaulted so every caller compiles, marked `// RFC-CARDS-1`
  (applied on cardsoptin 2026-10-20, Fable approved in RF-CARDS-1; RFCS.md carries the RFC). PRODUCT-SPEC
  5.8 shows the two fields (RF-CARDS-1 F5).
- `core.model.Op` gains `CurtainCue(receiver, token, stage, count, total, group, groupCount,
  groupTotal)` and the enums `CurtainStage` and `CurtainGroup`, marked `// RFC-CURTAIN-1` (applied on
  curtain 2026-10-20; RFCS.md carries the RFC and the owner's amendment for the group words).
- `core.trust` gains `interface CurtainPin { fun bundled(): LauncherArtifact }` next to `LauncherPin`,
  marked `// RFC-CURTAIN-1`.
- `core.planner.PlanRequest.Setup` gains `curtain: LauncherArtifact? = null` after `wallpaper`, and
  `core.planner.Choices` gains `curtain: Boolean = true` after `keepDebuggingOn`, both defaulted so
  every caller compiles and marked `// RFC-CURTAIN-1`; PRODUCT-SPEC 5.8 shows both (RF-CURTAIN-1 F5).
- `core.flows.SetupIntent` gains `ShowOnTv(on: Boolean)` (the Customise switch "Show the progress on
  the TV") and `core.model.WizardDraft` (growable) gains `curtain: Boolean = true`, both marked
  `// RFC-CURTAIN-1`.
- `core.spec.LauncherConfigDoc` gains `fillDock, animations, advancedEffects, greetingName,
  reducedMotion, dockTitle, dockRow, reorderButton, showStats, appLibrary, skyIntensity,
  settingsKeyKeycodes, settingsKeyPanel, festival, festivalUntil, greeting, accentColor` (nullable,
  defaulted null) and `EffectiveConfig` the same seventeen non-null, defaulted to launcher v2.4.0's
  `config.xml` values (`accentColor` is `accentColorArgb: Int` there, like `backgroundColorArgb`),
  after the RFC-W4-1 fields and marked `// RFC-W4-2` (applied on launcher240 2026-10-20, for Opus
  review; RFCS.md carries the RFC).
- `core.planner.Choices` gains `wizardWifi: WizardWifi = WizardWifi.UNKNOWN` after `curtain`, with
  the new `enum class WizardWifi { NOT_OWNER, OWNER, UNKNOWN }`; `core.planner.PlanRequest` gains
  `UpdateLauncher(launcher: LauncherArtifact)` and `PlanKind` gains `UPDATE_LAUNCHER`, all marked
  `// RFC-LAUNCHER-1` (applied on launcher240 2026-10-20, for Opus review; RFCS.md carries the RFC).
  Not frozen, listed for the merge: `core.probe.SavedWifiOwners` (beside `LaunchableApps`),
  `AdbProber(clock, signed = SIGNED_PACKAGES)`, `SetupDeps.savedWifi`, `HomeScreenIntent.PreviewUpdate`
  and `UpdateHomeScreen`, `HomeScreenState.updateTo` and `updatePreview`, `SnackKind.HOME_UPDATED`,
  `CopyKey.STOP_HOME_SETUPWRAITH_WIFI_BODY` and `_PRIMARY`, `TvWriter.write(..., fresh)` and
  `preview(..., fresh)`.
  App-design edits made for launcher240 (that folder has no history): PRODUCT-SPEC 5.7 (RFC-W4-2 text,
  the "built from" sentence), 5.8 (`wizardWifi`, `WizardWifi`, `REBOOT` and `UPDATE_LAUNCHER`,
  `Reboot` and `UpdateLauncher`), the D36 row (narration and the S3 row) and two new decision rows
  (D3 and D4, launcher 2.4); SECURITY V1 (the keycode note), 3.1 (`ProbeKind` `signer_of_installed`
  and `wifi_owners`) and 3.3 (the accessibility restore after `RestartHome`).
  Not frozen, added after review: `SavedWifiOwners.savedWifi(session): SavedWifi` (replacing the
  owners set), `AdbProber(..., known)`, `CopyKey.STOP_HOME_SETUPWRAITH_WIFI_UNKNOWN_BODY`,
  `HomeScreenState.tvSideSettings`.
- `core.spec.LauncherConfigDoc` gains `launchActions, appLabels, appBanners, inputKeyKeycodes,
  appKeys, cinematicHome, wallpaperRotationMinutes, wallpaperDim, raceCalendar` (nullable, defaulted
  null) and `EffectiveConfig` the same nine non-null (the three maps as `Map<String, String>`,
  `raceCalendar: List<LauncherRace>`), defaulted to launcher v2.5.0's `config.xml` values, after the
  RFC-W4-2 fields and marked `// RFC-W4-3`; 5.7 gains `data class LauncherRace(round, name, place,
  sessions)` and `data class LauncherRaceSession(name, start)` (applied on launcher250 2026-10-21, for
  review; RFCS.md carries the RFC). App-design edits for the lead (not made on the branch):
  PRODUCT-SPEC 5.7 (an RFC-W4-3 paragraph like RFC-W4-2's, and "built from v2.5.0") and SECURITY V1
  (the key services sentence in LAUNCHER-250-PLAN section 1).
  Made on release020 (RF-LAUNCHER-250 F6; the app-design folder has no history): PRODUCT-SPEC 5.7
  (the RFC-W4-3 paragraph with `LauncherRace` and `LauncherRaceSession`, "built from v2.5.0", the
  six-columns to four-tiles cut), SECURITY V1 (the key services sentence, ThemeProvider) and 3.3 (the
  `accessibility_enabled 0` step of the restore and "Stop here" finishing it), LAUNCHER-250-PLAN
  sections 2 and 5. Not frozen, listed for the record: `core.planner.HomeSwitchRisk`, the `HomeBlock`
  values `WIZARD_WIFI`, `WIZARD_WIFI_UNKNOWN`, `CASTING` and `BOOT_LOOP` (RF-SAFETY-250),
  `SetupExtras.doneKept`, `SnackKind.KEPT_TV_INPUT`, `RunLedger.keptTvInputs`,
  `DefaultEngine.PROTECTED` and `KEPT_TV_INPUT` (RF-LAUNCHER-250 F1), the `:e2e:emulatorTestTheme` task,
  HomeBlock `WIZARD_STAYS_ON` (RF-SAFETY-250), `AppGraph.versionOf` (the app version from the APK; 0.2.0, versionCode 2).

Added because the section 5 prose names it: `core.probe.ProbeException(stage, detail)` (R0: 7).

## RFCs for the Fable R0 gate

- RFC-W1-1 `:core:engine` must depend on `:core:planner`. `Engine.start(plan: Plan, ...)`,
  `RunState.*.plan: Plan`, `RunState.Finished.skipped: List<Step>` and
  `RunState.AwaitingConfirm.section: GuardedSection` all name planner types, while R0's notes put
  engine and planner in the same layer ("the executor (same layer as the planner)"), and same-layer
  edges are forbidden. Smallest fix, applied: the engine sits one layer above the planner in
  `ModuleRules`; no type moves and the planner never sees the engine. Alternative if Fable prefers
  the layers as drawn: move `Plan`, `Step`, `GuardedSection`, `PlanSummary`, `GroupSummary`,
  `RowSummary` and `PlanKind` into `:core:model`. Affects W3 only.
- RFC-W1-2 `PACKAGE_RE` requires at least one dot, so the framework package `android` (on every
  TV; 1 of the 107 packages in the Mi TV capture) cannot be a `PackageName`. A snapshot cannot
  record it and `Protected` cannot name it. Nothing writes to it, so 0.1 is safe either way;
  `Samples.miTvPackages()` drops it and says why. Proposal: keep the grammar and document that the
  probe skips `android` (it is protected by definition), or admit the single literal `android` in
  `PackageName.init`. Affects W3 (denylist data) and W4 (packages parser).
- RFC-W1-3 compileSdk 37 instead of D10's 36. The Compose BOM pinned by D34 (2026.09.00, Compose
  1.12.1) declares in its AAR metadata that consumers must compile against API 37; with 36 every
  Compose module fails `checkDebugAarMetadata`. ARCHITECTURE 7.4 already says "move to 37 when
  final" and API 37 is final. Applied: compileSdk 37, targetSdk stays 36, minSdk 28, so runtime
  behaviour and D10's intent are unchanged. Alternative: an older BOM (Compose 1.11) and stay on 36.
  Affects W7, W8, W9 (the `aapt2 dump badging` assertion still reads `targetSdkVersion 36`).
- RFC-W1-4 (documentation only) ARCHITECTURE 1.2 lists `:adb:wire -> (stdlib, okio)`, but the
  frozen 5.3 signatures take `Endpoint`, `Sha256` and `ShellLine` from `:core:model`, so the wire
  module depends on `:core:model` (layer 0, depends on nothing of ours, so no cycle is possible).
  Likewise `:core:model` gains serialization-json for `SnapshotCodec` (D-W1-5). Proposal: update
  the ARCHITECTURE 1.2 table; no code change.

## Open for other streams

- Each new third-party dependency needs a line in `ModuleRules.allowedExternal` (W1 reviews it in
  the same PR). okio (`:adb:wire`), bcprov (`:core:trust`, `:android:platform`) and the test
  libraries are already listed.
- Roborazzi and baseline profile plugins are catalogued but not wired into build-logic (W7, W9).
- `gradle/verification-metadata.xml` (dependency verification) is not generated yet; `pr.yml`
  lists it as a later job (W9). Generating it now would freeze the dependency set on day 1.
- The `Plan` JSON codec (R0 "deliberately left open") will be the third `@RawShell` opt-in; W3's
  single-call-site lint must allow it.

## Rulings (main loop, 2026-10-19)

- RFC-W1-1 accepted: `:core:engine` sits one layer above `:core:planner`; the engine executes plans,
  so the edge is natural. Plan types stay in `:core:planner`.
- RFC-W1-2: allow the single literal `android` in `PackageName` in addition to `PACKAGE_RE`, so the
  probe reports the framework package faithfully. It is on the hard-coded protected list, so no
  op can ever target it; W3 adds a test that proves the planner and the renderer refuse it.
  Applied on main (x0, T2): `PackageName.init` admits `android` by name, `Samples.miTvPackages()`
  keeps all 107 packages. Follow-ups: W4 (M1 step 4, done in the same change since W4 is on main)
  the probe no longer skips `android` (D-W4-22) and `AdbProberTest` expects 107 packages; W3 (M4
  step 2) unboxes the literal in `PlannerTest.kt:327`, `FrameworkPackageTest` and
  `WatchdogAndProtectedTest.kt:124`, keeps the test that the planner and the renderer refuse it,
  and rebases onto the fixtures change (107 packages, `android` among them).
- RFC-W1-3 accepted: compileSdk 37, targetSdk 36, minSdk 28. D10 reads "target 36, compile 37".
- RFC-W1-4 accepted: ARCHITECTURE 1.2 gains `:adb:wire -> :core:model` and serialization-json in
  `:core:model`.
- Forge has about 7 GB free beside its model server, so `build-on-forge.sh` takes a host-wide
  `flock` and builds run one at a time. Streams should batch their checks rather than build
  after every small edit.

## Integration follow-ups (main loop)

- I2 blocker from R5 of W6 (M1): the laptop restore scripts must undo to the earliest recorded
  state of each item, the same way `DefaultPlanner.undoEverything` does, from one shared helper,
  so no ordering can leave a TV without a home screen. Do this once W3 merges. Also rerun
  `RestoreShellTest` with W3's real renderer (M2).
- RFC-W4-1 accepted: `LauncherConfigDoc` and `EffectiveConfig` gain `inputAction` and
  `moduleCards` (upstream main 1bbf0c9 keys). Applied on main (x0, T3): `LauncherConfigExtras`
  is deleted.
- From R5 of W5, open Lows to close before I4: F5 (index high-water marks lost with the cache),
  F6 (revocation in a fetched keys file not learned when the fetched index fails), F7 (Android 9
  signer fallback on rotated module keys, verify on the emulator matrix).
- Per-branch R5 reviews live in `reviews/R5-<stream>.md` after merge.
- From R5 of W4 (merged after fixes): O1 probe SCREENSHOT batches mkdir and screencap, and
  `installedSha256` uses a pipeline; decide at R1 to split or record an exception to D40. O2
  classify install refusal codes at I1 from real captures. O3 setupwraith overrides curated
  takeover quirks (rca-ai-pont, tcl-65c725), O4 sony BRAVIA_ATV2 model vs device (product call),
  O5 the Xiaomi sure set disables PatchWall: R3 must verify `Protected` covers the current HOME.
- RFCs from streams live in `docs/rfcs/<stream>.md` after merge.
- From R5 of W8 (merge after fixes, fixed on w8). RFC rulings: RFC-W8-1 change (add
  `SetupIntent.StopAction(action)` and `SetupIntent.RejectIdentity`, keep `back(): Boolean`),
  RFC-W8-2 accept with the rule that nothing gating a write lives only in `SetupExtras`, RFC-W8-3
  accept, RFC-W8-4 accept for 0.1 but never cache the app-level pass, RFC-W8-5 accept (W3 adds
  `PlanRequest.Reboot` / `PlanKind.REBOOT`, journaled, not gated), RFC-W8-6 accept (manual card
  answer buttons; `Prober.cardAnswer` in 0.2), RFC-W8-7 accept (D16 gains USE_BIOMETRIC; W9's
  aapt2 check lists it). Open, to fix at the W7 swap (T8): O1 obscured-touch filtering only on
  confirming buttons, with a hint when a touch is dropped. Open, at the W3 merge: O2 a Manage
  write paused by a lost connection leaves its run open. Lows O3 to O13 are in reviews/R5-W8.md
  after merge; O11 adds the GitHub Pages host to the network security config before I4.
- From the W2 report: RFC-W2-1 (`:adb:session` opts in to `@RawShell` once for five constant lines)
  must be on W3's single-call-site lint allowlist at the W3 merge; RFC-W2-2 and RFC-W2-3 go to the
  Fable R2 gate.
- Fable R2 of W2: PASS WITH FIXES (merged). RFC-W2-1, RFC-W2-2 and RFC-W2-3 approved. Owner lows:
  a pump idle-read timeout or SO_TIMEOUT for a header-then-stall peer, `SecretBox.open` length
  bound `>=`, keep the CI 10-minute fuzz with the seed printed. I1 device checks: reviews/R2-W2.md.
- Fable R1 of W3: PASS WITH FIXES. Required before I3: R-1 (watchdog vs the 120 s window) and R-2
  (step R from the journal); R-3 and lows in progress on w3. Rulings: probe SCREENSHOT batching and
  `installedSha256` accepted as recorded exceptions to D40.
