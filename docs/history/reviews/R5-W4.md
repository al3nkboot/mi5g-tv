# R5 per-PR review: branch w4 (probe, profiles, launcher config codec)

Reviewer: R5 (did not write W4). Date 2026-10-19. Scope: `git diff main...w4` against PRODUCT-SPEC
section 5.5 to 5.7, the W4 row, D22, D26, D27, D40; UX.md section 2; SECURITY.md section 3;
DECISIONS-W4.md and RFCS.md. The Sonnet-curated `profile-meta.json` and `facts.tsv` were audited
line by line (a read-only audit pass checked every profile against its upstream `debloat.txt`
header and every facts row), and `profiles.json` as generated at build time was inspected.

## Verdict: MERGE AFTER FIXES (fixes applied on w4, see below)

With the four R5 commits on the branch, W4 is mergeable. No frozen section 5 signature changed;
RFC-W4-1 is a proposal only and the branch ships the no-change alternative. The open items below
are medium or low and belong to follow-ups (some need a real device session, some sit with W3).

## Findings

Severity: High = wrong or unsafe behaviour on a real TV or in the app; Medium = hostile-input DoS,
data that misleads a user, or a safety default that is too eager; Low = hygiene.

### Fixed in R5 commits

| # | Sev | Where (as on main...w4 before R5) | Finding | Fix |
| :- | :- | :- | :- | :- |
| F1 | Medium | `core/probe/.../Parsers.kt:146` | `PORT_LINE_NEW` chained three lazy `.*?` wildcards. One hostile 8 KiB `dumpsys hdmi_control` line (`HdmiPortInfo{id=1,address=0x1 cec=true ` repeated, no `arc=`) backtracks in roughly n^4: measured 0.7 s at 3.9 KB with a backtracking engine, extrapolating to over 10 s per line and minutes for a 1 MiB dump. No crash, but the probe hangs on the phone. | Bounded record body `HdmiPortInfo\{id=(\d{1,3}),([^{}]{0,256})` and three independent field finds (`5a9b814`). |
| F2 | Medium | `Parsers.kt:205-221` | `SettingsListParser` rebuilt the whole value string for every continuation line: quadratic in the 4 MiB cap (one key followed by 2 million one-character lines is terabytes of copying). | `StringBuilder` per key and a 64 KiB `VALUE_CAP` (`5a9b814`). Regression test `hostileShapesStayFast`. |
| F3 | Medium | `core/probe/.../AdbProber.kt:195-210` | `installRefusal` cleanup was not "always": a timeout, lost connection or coroutine cancellation after `pm install` left `tv.cleanyour.noop` installed (the finally only removed the APK file); a failing `pm uninstall` was ignored; the push sat outside the try; `runCatching` inside a cancelled coroutine never ran its suspend call. A leftover no-op package also makes the next check fail with an update/downgrade error, which counts as "refusal". | `mayBeInstalled` tracked until install clearly failed or uninstall clearly succeeded; finally runs the uninstall (if needed) and the `rm` under `NonCancellable` (`79c4d81`). Test `installRefusalCleansUpAfterATimeout`. |
| F4 | Low | `AdbProber.kt:167-173`, `:35` | Screenshot temp file was not removed when the pull threw a transport error; every probe exec, including the writing ones (mkdir, screencap, install, uninstall, rm), was sent as `RetryClass.IDEMPOTENT_READ`. | Cleanup in a finally under `NonCancellable`; writing lines use `IDEMPOTENT_WRITE` (`79c4d81`). |
| F5 | Low | `AdbProber.kt:192` | `freeDataMb` converted a hostile 64-bit `df` value with `toInt()`, wrapping to a negative number. | Clamped (`79c4d81`). |
| F6 | Low | `core/probe/src/test/resources/probe/README.md` | Synthesized fixtures were marked, but `gtv34/` read as if it came from an emulator capture. | README now says every file under `mitv/` and `gtv34/` is synthesized and only the W1 conformance files are captures (`79c4d81`). |
| F7 | High (data) | `profile-meta.json:429` (tcl-g05-google-tv) | `com.tcl.suspension` mapped to `brand`, ask false, so Simple disables it; upstream's own note says the remote's Input key stops working without it (G10 and QM6K protect it). | ask true, so it lands in "More TCL features" (ASK) (`dcff900`). |
| F8 | Medium (data) | `profile-meta.json` insignia group "Amazon background sync..." and `:642` (xiaomi-mitv-mooq0) | Parental controls in default-disable groups: `com.amazon.tahoe` (Amazon Kids) and `com.mitv.tvlock` (parental password). Simple would remove a lock a family set without asking. | Per-package override, ask true (`dcff900`). |
| F9 | Medium (data) | `facts.tsv` | Wrong or misleading user-facing text. Wrong identity: `air.com.vudu.air.DownloaderTablet` is the Vudu app itself, `com.amazon.cloud9` is Silk Browser (not a service), `com.jio.web.androidtv` is JioPages (not JioTV), `com.tcl.browser` is BrowseHere (also on OnePlus and Xiaomi sets). Understated: `com.google.android.tts` (Speech Services, also speech to text), `com.google.android.videos` (the Google TV app with purchases), `com.android.packageinstaller` (also updates, uninstalls, permission prompts), `com.google.android.speech.pumpkin`. Misleading for keeps: `com.tcl.suspension`, `com.tcl.dashboard`, `com.tcl.keyhelp`, `com.tcl.useragreement`, `com.tcl.ttvs` (also Alexa, T-Cast), `com.homwee.dmt.upgrade`. Category: Alphonso ACR is tracking, not ads. Outdated: Duo (Meet), Max (HBO Max), Hotstar (JioHotstar). Profile-specific wording in a shared table ("spare copy", "(copy)"). Coverage gap against D-W4-17: the `com.apple.airplay.amazon.airplay` keep had no row. | 23 rows corrected or added (`abb7f0c`). |

### Open (not fixed here)

| # | Sev | Where | Finding | Recommendation |
| :- | :- | :- | :- | :- |
| O1 | Medium | `ProbeScripts.kt` `SCREENSHOT`, `installedSha256` | D40 says writes never batch; `SCREENSHOT` is `mkdir -p ... && screencap -p ...` (two writes under one exec) and `installedSha256` runs `pm path | grep | head` and a `sha256sum` of a TV-derived path. Both are constants with typed, quote-free arguments and a golden test, and the TV value is only expanded inside double quotes (never re-parsed), so no injection; but the SECURITY 3.5 rule text is not met literally. | Either split `SCREENSHOT` (MKDIR_TMP then screencap) or record the exception in DECISIONS/SECURITY with R1's sign-off. |
| O2 | Medium | `AdbProber.installRefusal` | Any non-storage install failure counts as "refusal", including `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, `VERSION_DOWNGRADE` (a leftover from an earlier run), parse or certificate errors in our own no-op APK. A false "refusal" sends the user down UX 4.6 for no reason. | Classify known refusal codes (user restricted, verification failure, not allowed) as refusal and anything else as an error; uninstall a leftover `tv.cleanyour.noop` first. Confirm at the I1 device session. |
| O3 | Medium | `DebloatTxt.kt:151` | A setupwraith line in a group always sets `setupWraith` and adds `HOME_TAKEOVER_SETUPWRAITH`, ignoring the curated meta (`setupWraith: null`, `quirks: []`) for rca-ai-pont and tcl-65c725, two tvlauncher sets where upstream says Home works without touching it. The meta overrides for that package are dead. Effect: setupwraith moves from the debloat into the takeover section (disabled during takeover, re-enabled by rollback); upstream disabled it on both TVs anyway, so no new risk, but the meta is silently overruled. Same pattern for `com.google.android.leanbacklauncher` on mooq0 (meta lists only tvlauncher). | Let an explicit meta value win, or fail the converter when meta and parse disagree. |
| O4 | Medium | `profile-meta.json` sony-xbr-x900e | `BRAVIA_ATV2` is the device codename but sits in `models` with `devices: []`, so the profile likely never matches a real TV. Putting it in `devices` would also match every 2016 and 2017 BRAVIA_ATV2 model on SDK 28, which is a product decision. | Decide with the data owner; until then this profile is effectively unreachable. |
| O5 | Medium | `profiles.json` (Xiaomi sure set) | The Xiaomi sure set disables `com.mitv.tvhome.atv` (PatchWall). On an unknown Xiaomi where PatchWall is the current HOME, that would leave the TV without a home screen; the only guard is W3's live `Protected` rule for the current HOME (and the sure set's stock launcher list does not include PatchWall). | R3 should confirm W3's `Protected` covers the resolved HOME before merge of W3; consider a W4 rule that drops HOME claimants from sure sets. |
| O6 | Low | default-disable choices kept from upstream | Upstream-tested but surprising defaults in Simple: `com.tcl.android.webview` (WebView provider, tcl-65c725), `com.amazon.tv.oobe`/`ooberesource` (Fire TV setup), USB media players `com.sony.dtv.smartmediaapp`, `com.sony.dtv.osat.music`, `com.tcl.ui_mediaCenter`. | Consider ask for media players (a parent loses USB playback) and the WebView provider; data owner call. |
| O7 | Low | `DebloatTxt.keepListIn` | Picks up `tv.agent` from "com.mediatek.* (extservice, tv.agent, ...)" in xiaomi-tv-a-pro-2026 as a never-disable package name; the intended `com.mediatek.tv.agent` is not added. Harmless (an unknown name only shrinks plans) but wrong data. | Skip tokens inside a parenthesis that follows a wildcard, or curate the list in meta. |
| O8 | Low | `profile-meta.json` | Data gaps from the audit: Fire OS and TCL China profiles do not use `STOCK_LAUNCHER_KEEP_ENABLED`; oneplus-tv-dubai is GOOGLE_TV with tvlauncher (header contradicts itself); toshiba lacks the Insignia's Live TV and settings keeps; chiq, tcl-beyondtv and sony-xbr never-disable lists are shorter than their headers' keep notes (mediatek services, `com.tcl.framework.custom`, `com.sony.dtv.tvapi`); rca brand excludes the realme build of the same board; nvidia `P2897` is a SKU, not the model (device still matches). | Data follow-up before profiles leave internal builds. |
| O9 | Low | `KnownLaunchers` | `com.amazon.firehomestarter` and `com.tcl.cyberui` are silently dropped from `stockLaunchers` (not in the allowlist), so tcl-ak30a5 has none. Fine while those platforms are refused the takeover (UX 4.6). | Note for W3. |
| O10 | Low | `facts.tsv` categories | `system`, `launcher` and `home` are not `ProfileCategory` ids. Display-only today (nothing calls `ProfileCategory.of` on them). | Keep display-only, or document the vocabulary. |

## Focus items, answered

- Probe safety: every probe line is a `const`-built `ShellLine` in `ProbeScripts`, pinned by
  `golden-scripts.txt`; the three argument-taking lines accept only `RemotePath`, `PackageName`,
  `RunToken` and `q()` refuses quotes and non-printables. Reads only, except `installRefusal`
  (mkdir, push, install, uninstall, rm) and the screenshot temp file (O1). Output caps: 512 KiB
  facts, 4 MiB package lists and settings, 1 MiB inputs, 64 KiB home, 16 KiB small reads; a
  truncated read is a `ProbeException`. `watchdogAlive`'s `[c]leanyourtv` bracket correctly avoids
  self-matching; the `ps` fallback cannot match itself either.
- Parser robustness: every parser applies the 8 KiB line cap (throw or skip), matches values
  against grammars, and builds `PackageName`/`Component` through `runCatching`; the fuzz test
  confirms only `ProbeException` escapes. The two hostile-time issues (F1, F2) are fixed with a
  timing test. `sections()` rejects forged or repeated markers.
- Profile conversion, spot check of 20 default actions in the generated `profiles.json`:
  axso0 `com.mitv.tvhome.atv` ads DISABLE (not HOME there, tvlauncher is) OK; axso0
  `com.xiaomi.mitv.updateservice` updates ASK (D22) OK; axso0 `com.google.android.katniss` voice ASK
  OK; axso0 `com.mitv.gallery` preloads DISABLE OK; axso0 `com.android.tv` is `liveTv`, not in a
  group OK; chiq `com.homwee.upgrade` updates ASK OK; insignia `com.amazon.device.software.ota`
  updates ASK OK; insignia `com.amazon.tahoe` was DISABLE, now ASK (F8); mooq0 `com.mitv.tvlock`
  was DISABLE, now ASK (F8); mooq0 `com.duokan.factorytest` (extra HOME claimer) DISABLE OK; g05
  `com.tcl.suspension` was DISABLE, now ASK (F7); qm6k `com.tcl.ttvs` DISABLE but in never-disable,
  so the planner skips it (reported in the feedback file) OK; chromecast setupwraith moved to
  `setupWraith` with the quirk OK; chromecast `com.google.android.tv.remotecontrol.logging` DISABLE
  (logging only; flagged by name in the feedback file) OK; tcl-65c725 `com.tcl.android.webview`
  DISABLE (O6); sony-xbr `com.sony.dtv.smartmediaapp` DISABLE (O6); mi-box-s
  `com.google.android.tv` (Live Channels, no tuner) DISABLE OK; hisense `com.hisense.tv.smartsense`
  never-disable OK; xiaomi-tv-a-pro-2026 `com.mediatek.tv.systemupdater` updates ASK OK; Xiaomi sure
  set `com.mitv.tvhome.atv` (O5). No KEEP group holds anything but launchers; no updater outside
  `updates` (checked against every override and the D22 rule); no system-critical package (IME,
  TIF input, CEC, remote pairing, settings provider) sits in a default-disable group apart from F7.
- Differential `Config.java` test: genuine. The vendored `com/gathin/tvhome/Config.java` is
  byte-identical to upstream `1bbf0c9` below its two header lines (`git show 1bbf0c9:...` diff); the
  pinned `7205769` copy is synced from the submodule at build time; the bridges call the real
  `Config.load` against AOSP org.json and a verbatim `Color.parseColor`, with the launcher's own
  `config.xml` defaults. The test fails on any disagreement in either direction (apply vs ignore vs
  crash) and checks the extras against the latest main.
- Determinism of build-time generation: folders sorted, quirks sorted, groups ordered by default
  then category ordinal, sure-set brands in a sorted map and entries sorted by name, UTF-8 reads,
  `%02x` hashing (no locale effect). `bundledProfilesEqualAFreshParse` re-derives the bundle from
  the sources. Inputs (examples dir, meta file, classpath) are declared on the `JavaExec` task.
- Synthesized fixtures: marked in the README since the branch; R5 made the `gtv34/` wording
  unambiguous (F6). They must be replaced by captures at I1 (DECISIONS D-W4-24).

## Evidence

- Last build: `scripts/build-on-forge.sh :core:spec:check :core:probe:check :testing:fixtures:check checkModuleGraph`
  -> `BUILD SUCCESSFUL in 8s` (40 actionable tasks); `profiles.json: 26 profiles, sure sets for TCL (10), Xiaomi (17)`.
- Tests: AdbProberTest 11, ParsersTest 11, ProfilesTest 12, ProtectedReportTest 1,
  LauncherConfigCodecTest 5, ConfigDifferentialTest 6, FixturesLoadTest 5, FakesTest 14,
  RedactionTest 2; all green. Baseline before R5: AdbProberTest 10, ParsersTest 10, same otherwise.
- Regex timing: Python `re` (also backtracking) on the old pattern: 975 chars 0.004 s, 1950 chars
  0.05 s, 3900 chars 0.72 s; the new pattern on the same 8 KiB line 100 times: under 1 ms.

## R5 commits on w4

- `5a9b814` R5: probe parsers: bounded HdmiPortInfo match and capped settings values
- `79c4d81` R5: probe: installRefusal and screenshot always clean up, writes not marked as reads
- `dcff900` R5: profile-meta: parental controls and the TCL G05 Quick Panel become opt-in
- `abb7f0c` R5: facts.tsv: correct names and descriptions from the data audit
