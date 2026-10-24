# RF review: launcher v2.4.0 (RFC-W4-2, RFC-LAUNCHER-1, owner decisions D1 to D4)

Reviewer: review gate (Opus standing in for Fable), 2026-10-20. Scope: branch `launcher240` in
`cyt-wt/launcher240`, nine commits on main `f553232` (2f4612c, afc49f7, 7ca22bb, fea8528, a9295f7,
b365829, 6f10a94, d20c797, 929f929; `git diff f553232..launcher240`, 134 files, most of them
re-recorded Roborazzi PNGs), against `app-design/LAUNCHER-240-PLAN.md`, the owner decisions D1 to
D4, RFCS.md (RFC-W4-2, RFC-LAUNCHER-1), SECURITY 3.1 to 3.3, 3.6, C3, F3 and V1, ARCHITECTURE 3,
PRODUCT-SPEC 5.7, 5.8 and D36, DECISIONS-W1 and RF-CURTAIN-1. The bundled APK was checked with
apksigner and aapt2; the upstream `Config.java`, `Festival.java` and `Cards.java` at 69c93b6 were
read against the codec and the test stubs; the e2e evidence in `build/e2e-forge` was read.

## Verdict: APPROVE WITH FOLLOW-UPS

The upgrade itself is sound: the pinned APK is what the docs say, the codec models v2.4.0 key for
key and is held to the real `Config.java` and `Festival.java`, the profile build skips exactly the
63 pinned folders, the setupwraith guard sits in the one place the wizard can be switched off, the
Update plan never downgrades and never touches another key's app, and the branch is a fast forward
of main that undoes nothing of the curtain. No Critical finding. But the signer read, which is new
trust, has a gap on Android 8 that lets a planted `com.gathin.tvhome` become Home (F2), the launcher
replacement is still invisible before the gate (F1, D36 says "never silently"), the D4 flow test
never runs (F4), and the double build's compare does not stop the script (F5). Nine items must be
done before merge (F1 to F9): six small code changes, one copy change and two process or docs items.

## Must do before merge

- **F1** Show the launcher replacement on S3 before the gate (D36), from `summary.replacesLauncher`.
- **F2** `ApkSigners`: require a v2 block, and a v3 block (when present) whose first certificate
  equals v2's; anything else is unknown.
- **F3** The takeover's first start: back to CONTINUE, with "the start step is Done" as a precondition
  of opening the section (finish with VERIFY_FAILED, never pause); `ScriptedTv` fails `am start -W`
  for a package that is not installed.
- **F4** Move the D4 flow test out of the `launchAccess` lambda into `ManageMachinesTest`.
- **F5** `build-launcher-on-forge.sh`: a `cmp` mismatch must stop the script; attach the run's output.
- **F6** The Wi-Fi stop card for UNKNOWN must not say the wizard saved the Wi-Fi (owner copy).
- **F7** `restoreAccessibility` only when the TV had `accessibility_enabled` 1.
- **F8** Run the suites on forge in verify mode and S06 on API 28 and 34; attach the summaries.
- **F9** Docs: PRODUCT-SPEC 5.8 and D36, SECURITY 3.1 and 3.3, D3 and D4 rows.

## Findings

| # | Sev | Where | Finding | Status |
| :- | :- | :- | :- | :- |
| F1 | Must fix (Medium) | `SetupMachine.replan` line 638, `DefaultPlanner.launcherInstall`, S3 | Now that the signer is read, a launcher under another key (every upstream user's own debug build, the common case) is replaced: `replaceLauncherSigner` is set to true whenever the signers differ, and the plan uninstalls it, which erases its `config.json`, `order.txt`, library choices, Launcher settings and card answers. The only places the person can learn this are the Working narration ("Replacing the home screen app"), after the gate, and the step's narration inside Show commands. `summary.replacesLauncher` is computed and stored but no screen reads it, and `homeSwitchRows` lists only Disable steps, so S3 shows nothing. D36 asks for "one explicit step ... Never silently". Fix: when `summary.replacesLauncher`, S3's "Needed for the new home screen" group gets a row (title "Replace the home screen app already on the TV", description the new narration's words: its look, order, app list, settings and card answers are reset), protected like the other switch rows; the credential gate that follows is the confirmation D36 names. No extra dialog. Tests: a flows test that the S3 summary carries the row when the signers differ and not otherwise, and one Roborazzi shot. | Open |
| F2 | Must fix (Medium) | `ApkSigners.certificate`, `DefaultPlanner.takeover` (`installedAndVerified`), `pinInstalled` | The signer is read without verification, which is safe only if the TV's package manager verified the same block. Android 8.0 and 8.1 (API 26 and 27, inside minSdk 26) do not know v3 and ignore its block, and the APK Signing Block is not covered by v2's signature, so an APK signed v2 with any key can carry an extra v3 block holding K1's certificate. `ApkSigners` reads v3 first and reports K1. With a versionCode of 5 or more, Setup then plans no install (`pinInstalled`), `takeover` sees `installedAndVerified`, and `openSection` has no install step to hash against, so the squatter becomes Home under "the new home screen". Before this branch the signer was null, the plan installed ours, and `openSection`'s hash check stopped the switch, so this is a regression. Fix in the parser: require a v2 block; when a v3 block is present, its first certificate must equal v2's; anything else returns null. Android 8 verifies v2 whenever a v2 block exists, and 9 and later verify both, so the reading then matches what the TV verified. K1 has no rotation, so our APKs read the same. Test: the bundled APK with a v3 block whose certificate is swapped (or a synthetic block) reads null. Defence in depth (follow-up): when the plan does not install the launcher, `openSection` could require the installed sha256 to be a known pin. | Open |
| F3 | Must fix (Medium) | `DefaultPlanner.takeover` line 899, `DefaultEngine.openSection`, `ScriptedTv` | Making the first start STOP_AND_ROLLBACK keeps the stock home screen safe, but it turns the most common launcher failure into a pause. A failed install leaves no launcher, `am start -W` then fails on a real TV, and the run pauses with STEP_FAILED; "Carry on" retries a start that cannot succeed. Before, the start was skipped and `openSection` (launcher not installed) ended the run with VERIFY_FAILED and a clear Done. `aFailedInstallSkipsTheSectionWithoutArmingAnything` still passes only because `ScriptedTv` answers "Status: ok" for a package it does not have. A slow TV that prints "Status: timeout" (the 10 s launch idle timeout) also pauses. Fix: keep the step CONTINUE with the new "Status: ok" check, and add to `openSection`'s preconditions that the takeover's `StartActivity` step is in `run.done` (not skipped); otherwise `finishWithSkips(VERIFY_FAILED)`. The stock home screen is then never touched without a started launcher, and the person gets the existing "home screen not switched" Done. `ScriptedTv` must fail `am start -W` for a package it does not have; then both SectionTest cases (failed install, failed start) assert Finished with VERIFY_FAILED, no watchdog, stock enabled. | Open |
| F4 | Must fix (Low, test gap) | `ManageMachinesTest.kt` end of file | `updateTheHomeScreenOffersPreviewsAndRunsInPlace` was appended inside the `launch { ... }` lambda of the top-level `launchAccess` helper (the class closed before it). It compiles as a local function and JUnit never runs it, so the D4 flow (offer, fresh preview, run, no look pushed again, versionCode recorded, not offered for the pin, another key or an unknown key) is untested. Move it into the class, restore `launchAccess`, run it. | Open |
| F5 | Must fix (Low) | `scripts/build-launcher-on-forge.sh`, sign stage | `cmp /src/signed-a.apk /src/signed-b.apk && echo "identical after signing: yes"` does not stop a `bash -e` script when `cmp` fails, because a failing command that is not the last in an `&&` list is exempt from errexit. A non-reproducible build would be renamed, pinned and copied, with only the "identical" line missing. Use `cmp ... \|\| { echo "builds differ" >&2; exit 1; }`. The run's output (the identical line, both sha256s, size 386,581, signer `57641815...5132`) is not on file; attach it to the merge, as RF-CURTAIN-1 F3 asked for the curtain. | Open |
| F6 | Must fix (Low, copy) | `stop_home_setupwraith_wifi_body`, `SetupMachine.homeStopCard` | The card says "Your TV's Wi-Fi was saved by its setup wizard" for UNKNOWN too: when `dumpsys` failed, or the TV is on a cable with no saved network (grep then prints nothing). The second case is common for HOME_TAKEOVER_SETUPWRAITH TVs set up on Ethernet, and "Use a cable" is then advice they already follow. The app must not state what it does not know. Add a second body for UNKNOWN (owner to approve), for example: "We couldn't tell whether this TV's setup wizard saved its Wi-Fi. Switching the wizard off could forget the network, so we left it on." with the same primary. D3's copy stays for OWNER. See F14 for telling "no saved network" apart. | Open, copy |
| F7 | Must fix (Medium) | `DefaultPlanner.restoreAccessibility` | The restore writes `accessibility_enabled 1` whenever the probed list names one of ours, whatever the TV had. A TV with the list set but accessibility off (the person switched it off, or a vendor build keeps it off) gets accessibility switched on, which binds every service in the list, someone else's included. An accessibility service reads the screen, so the app must never switch one on. Plan the restore only when the probed `accessibility_enabled` is "1" (then the last step restores exactly what was there). Test: list with ours and `accessibility_enabled` "0" or unset plans no restore steps. | Open |
| F8 | Must do before merge | whole branch | No unit test output is in the worktree. `build/e2e-forge` holds one S09 run (API 28, 27.5 s, passed at 19:44, after the last commit; the takeover's `am start -W` said "Status: ok" on the emulator) and an empty `emulatorTest` result, so S06 (the accessibility restore on a real adbd) has not run. Run on forge, in verify mode: `:core:spec:test :core:probe:test :core:planner:test :core:engine:test :core:ops:test :core:flows:test :core:backup:test :core:trust:check :feature:launcher:test :feature:setup:test :testing:fixtures:test checkModuleGraph`, then S06 and S09 on API 28 and 34. Attach the summaries, with the differential test's tally lines and whether `upstreamRestoreShReadsIt` ran recovery.py for real (python3) or fell back. | Open, process |
| F9 | Must do before merge (docs) | PRODUCT-SPEC, SECURITY (outside git) | 5.7 carries RFC-W4-2 and SECURITY V1 the keycode note (checked). Missing: PRODUCT-SPEC 5.8 (`Choices.wizardWifi`, `WizardWifi`, `PlanRequest.UpdateLauncher`, `PlanKind.UPDATE_LAUNCHER`, as RF-CARDS-1 F5 did); the D36 row's narration (now "its look, order, app list, settings and card answers on the TV are reset") and its S3 row (F1); a decisions row each for D3 (the guard, UNKNOWN keeps the wizard on) and D4 (Update the home screen: our signer only, never downgrade). SECURITY 3.1 `ProbeKind` gains the two new reads (`signer_of_installed`: `pm path`, `cp` into `tmp/signer.apk`, a capped pull, `rm`; `wifi_owners`: `dumpsys wifi \| grep -o 'cname=[^ ]*'`), and the probe's "reads only" exception list in `AdbProber`'s header names the copy. SECURITY 3.3 says `enabled_accessibility_services` and `accessibility_enabled` are for `enable_accessibility` only; add the profile step that writes the probed list back unchanged after `RestartHome` (F7's condition included). Mark both RFCs reviewed. | Open, trivial |
| F10 | Low | `ChangeSummary.of`, `RestoreCommands` | The three restore steps record `enabled_accessibility_services` and `accessibility_enabled` going from a value to the same value, and `ChangeSummary.settings` lists every key it saw, so a theme push on such a TV reports two setting changes that changed nothing. Drop pairs whose before equals now. | Follow-up |
| F11 | Low | `AdbProber.signerOf`, `ProbeScripts.copyApk` | (a) One fixed name, `tmp/signer.apk`, for every copy: a Manage preview's fresh snapshot and the engine's PRE_PLAN snapshot on the same session can delete each other's copy, which reads as "signer unknown" (refused Update, or an install over ours). Use a per-call name (the snapshot's own token). (b) The whole APK is copied before the 16 MiB cap is checked; read the size first (`stat -c %s`) and skip above the cap, so a large foreign launcher never fills a small TV's `/data`. (c) Every snapshot now copies and pulls about 390 KB per package; when `installedSha256` equals a pin's sha256 the signer is that pin's, so the copy can be skipped. | Follow-up |
| F12 | Low | `ParsersTest` writers check | `copyApk` writes (`mkdir`, `cp`) but is not in the writers set, and passes the "no write verb" regex only because `cp` and `mkdir` are not in it. Add both verbs to the regex and `copyApk` to the writers. | Follow-up |
| F13 | Low | `HomeScreenMachine.update`, D4 | When ours is Home, the update replaces the running Home app with no watchdog. The risk is small (pinned, e2e-tested build, same component, and a Setup re-run already did the same), but a failed "Starting the updated home screen" only shows the generic FAILED snack. On a skipped start, show a card that offers "Use the TV's own home screen". Also, "nothing to update after all" shows "up to date: version 2.4" when the TV has a newer build of ours; name the TV's version there. | Follow-up |
| F14 | Low | `WIFI_OWNERS`, `WifiOwnersParser` | An empty answer cannot tell "dumpsys failed" from "no saved network", so a wizard TV on Ethernet with nothing saved keeps the wizard on forever. A sentinel from the same read (for example also matching WifiConfigManager's "Configured networks" header line, after checking it on API 26, 28 and 34 dumps) would let "header present, no cname" count as NOT_OWNER. Owner call; until then F6's copy covers it. | Follow-up |
| F15 | Low | `TvRestoreFilesTest.upstreamRestoreShReadsIt` | Without python3 (the forge test image) the test no longer runs restore.sh at all, only recovery.py's package grammar, so D44 is checked for real only where python3 exists. Add python3 to the image the unit tests run in (the repro image has it), or run this test there. | Follow-up |
| F16 | Low | `HomeScreen.kt`, `home_tv_changes_note` | The note about Launcher settings on the TV shows for TVs still on launcher 2.0, which has no such settings. Show it when the installed versionCode is 5 or more. | Follow-up |
| F17 | Info | flag (4), RF-DOCK-1 F4 | Clearing TV-side choices relies on every push giving `config.json` a new mtime (`AdbTvSession` sends the phone clock in seconds; checked). If RF-DOCK-1 F4 (skip a push whose digest already matches) is ever done, a same-look push would stop clearing them and the sheet's note would be wrong; that change must then write `theme_revision`. | Info |
| F18 | Info | `NAVIGATION_KEYCODES` | Back, the arrows, centre, Enter and Menu are covered. Some remotes send NUMPAD_ENTER (160) or BUTTON_SELECT (109) for OK; consider them when upstream takes the refusal. | Info |
| F19 | Info | curtain, RF-CURTAIN-1 F8 | Still open as planned. If F3 kept a pause path, that pause would also leave the curtain installed until the next Leftovers; with F3's skip path the run ends normally. | Info |

## Rulings on the builder's flags

- **(1) Setup replaces a launcher under another key, with only step narration as warning:
  overruled in part, see F1.** Replacing it is right: without it the takeover keeps their old
  launcher and the bundled one never lands, and D36 already decided the replacement. What D36 does
  not allow is that the person first learns about it during the run. My recommendation: no separate
  confirmation dialog. Show the replacement as an explicit row on S3, with what it erases, before
  "Make it clean"; the credential gate is the confirmation, as D36 says. Later (W8 follow-up) a
  Custom choice "Keep the home screen app already on the TV" (no install, no takeover) would let an
  upstream user who likes their own launcher keep it.
- **(2) The takeover's first start stops the run on failure: overruled in form, upheld in intent,
  see F3.** The guarantee wanted (the stock home screen is never touched unless the new one started)
  is better placed as a section precondition, which ends the run cleanly instead of pausing.
- **(3) The Wi-Fi card wording is the same when the TV cannot tell: overruled, see F6.** The guard
  itself (UNKNOWN keeps the wizard on, fail closed like upstream) is upheld.
- **(4) `theme_revision` not written: upheld.** Every push sets a new mtime from the phone clock;
  identical bytes in the same second is not a real case, since a TV-side choice has to happen between
  the two pushes. F17 records the one future change that would break it.
- **(5) `tcl-g10-google-tv` names its setup wizard: upheld.** The quirk is derived by the parser from
  `setupWraith` (`theG10TakeoverIncludesTheWizard`), the wizard stays out of every disable group
  (`REASON_WIZARD_SECTION_ONLY`), and D3 applies. On Google TV the wizard often owns the network, so
  expect the Wi-Fi card there; that is D3 working.
- **(6) Docs edited outside git: upheld, incomplete.** The two edits are there and correct; the rest
  is F9. Since `app-design` has no history, DECISIONS-W1 should name each app-design edit made for
  this branch.

## What was checked and held

- **The new probe line `dumpsys wifi | grep -o 'cname=[^ ]*'`.** Allowed by the probe rules: a
  constant `ShellLine` with no argument, a fixed command like `installedSha256`, which already pipes
  `pm path` through `grep` and `head`. Read-only: `dumpsys` and `grep` only, no write verb; `PmGuard`
  has nothing to match (no `pm` write). Private data: the pipe runs on the TV, so the dump never
  leaves it; `-o` prints only the `cname=` words, which `WifiConfiguration` fills with the creator's
  package or shared uid name (`com.x:10031`, `android.uid.system:1000`), never an SSID, BSSID or key.
  `WifiOwnersParser` then drops anything that is not a package or shared uid with an optional uid
  (`cname="My home"` and `cname=;rm` are dropped in the test). The owners are neither stored in the
  snapshot nor logged; only the verdict is (`theSetupWizardStaysOnWhileItMayOwnTheWifi` checks that
  no uid reaches the log). Asked once per snapshot, only when a setup wizard is on the TV; an exception
  or empty answer is UNKNOWN.
- **The D3 guard.** The wizard can be switched off in one place only, the takeover, and only when
  `wizardWifi == NOT_OWNER`; body groups, extra-off and Manage's switch-off refuse it
  (`REASON_WIZARD_SECTION_ONLY`); a `Choices` built without the answer defaults to UNKNOWN; "Try
  again" on the Wi-Fi card asks the TV afresh and never adds the wizard to extra-off. Without the
  wizard the rest of the switch is planned unchanged (`theWizardStaysOnWhileItMayOwnTheWifi`).
  `AppGraph` passes the real `AdbProber`, so `prober as? SavedWifiOwners` resolves in the app.
- **The signer read.** `copyApk` is a fixed template: the package is validated by `PACKAGE_RE` and
  quoted, the source is `pm path`'s own `base.apk` line (quoted), the destination is the fixed
  `/data/local/tmp/cleanyourtv/signer.apk`. The only writes on the TV are that `mkdir -p` and the copy,
  both in the app's TMP folder. The pull goes through `RemotePath` in TMP (C3 holds), capped at
  16 MiB, into memory only. `SIGNER_CLEANUP` runs in a `finally` under `NonCancellable`, and the test
  checks it is the last line. Only installed packages in `SIGNED_PACKAGES` (the launcher and the
  curtain) are copied. Any failure is null, which the planner treats as unknown. The parser checks
  every length before use, catches every runtime exception, and survives 2,000 single-bit flips in the
  block. The bundled launcher and curtain both read K1 (`signerOfTheBundledApks`). F2 and F11 are the
  gaps.
- **The accessibility restore after a theme push or the undo's restart.** Only when the probed
  `enabled_accessibility_services` names a `com.gathin.tvhome/` service and matches the
  `ComponentList` shape. It writes four typed steps after `RestartHome`: `Settle 1`,
  `settings delete secure enabled_accessibility_services`, `settings put secure
  enabled_accessibility_services <the probed list, unchanged>`, `settings put secure
  accessibility_enabled 1`. All are CONTINUE, in the theme or undo group, named in Show commands
  and the plan hash, with the probed values as priors. Other services are written back exactly as
  probed, never dropped or added. They are unbound for the moment between the delete and the put, as
  upstream's theme.sh does. A list changed since the snapshot cannot be overwritten: `Staleness.diff`
  compares SettingsPut priors with the PRE_PLAN snapshot and pauses STALE. Undo puts the earliest
  prior back. F7 (switching accessibility on) and F10 (no-op change records) are the gaps; S06 asserts
  the real service and `accessibility_enabled` after a push (F8: not run yet).
- **The Update plan (D4).** Refused for another package, an absent launcher, an unreadable signer and
  another signer (with the D36 pointer). No steps when the TV has this version or newer. Never a
  downgrade: `InstallFlag` has only REPLACE, nothing renders `-d`, and `pm` refuses a lower
  versionCode anyway. The install has prior `present = true`, so this record never makes undo
  uninstall; UndoOrder's earliest record is still Setup's install. The start comes only when the
  launcher is Home. No section, nothing else touched (`updateTheHomeScreen`). Preview and run both plan
  from a fresh snapshot (`TvWriter.write/preview(fresh = true)`), so the signer is read again before
  anything is installed. Staleness checks the install's `present` prior. The preview lists the plan's
  narration, and Update is a guarded primary (`updateIsGuardedAndCancelIsNot`). An open run shows its
  card (O2).
- **Config codec, seventeen keys.** `KEY_ORDER` matches `Config.load`'s read order at 69c93b6 (checked
  line by line), with the festival keys last. The fifteen old keys keep their relative order, so every
  earlier `config.json` encodes byte for byte the same. `intList` is all or nothing like
  `Config.intArray` (Integer, Long low 32 bits, Double truncated, anything else or below 1 drops the
  list). `sky_intensity` uses 0 to 100. `LauncherFestival` follows `Festival.parse`, and `Cards.cleanLabel`
  in the test stub is byte-identical to upstream's (diffed). `BUILT_IN` equals v2.4.0's `config.xml`
  (`builtInDefaultsEqualTheLaunchersConfigXml`). Encode refuses out-of-range values and untrimmed
  strings. RFC-W4-2's fields are trailing and defaulted, so every caller compiles; DECISIONS-W1
  lists them.
- **Differential test.** The real v2.4.0 `Config.java` and `Festival.java` are copied from the
  submodule at build time. The 1bbf0c9 copy is kept as launcher 2.0 and compared on the keys it reads.
  All seventeen new fields are compared. The fuzz generates every new key with bad items. 10,000 files
  run against 2.4 and 3,000 against 2.0, with coverage floors. `aChoiceMadeOnTheTvLastsUntilTheNextPush`
  shows that the same bytes pushed again clear a TV-side choice through the mtime. The dock rows test
  models v2.4.0's skips (missing, not exported, same label twice, taken off Home, each over 100 times)
  and D1 (`dock_title` false exactly for three rows).
- **Keycode warning.** 4, 19 to 23, 66 and 82 warn once, naming the codes, without rejecting.
  SECURITY V1 records it.
- **Profiles (D2).** 93 upstream folders. `unconverted.txt` lists exactly the 63 without meta, sorted
  and pinned by a test. A new folder, a stale line, or a line that also has meta fails the build. The
  30 converted profiles are unchanged except tcl-g10. The unconverted folders still parse by header.
- **Reproducible signed double build and pin.** Two fresh copies, two throwaway containers, no build
  cache, the submodule pinned and clean (the dirty-tree check RF-CURTAIN-1 F10 asked for is here), both
  re-signed with K1 in `/dev/shm` with `umask 077`, the keystore never on forge's disk. The asset is
  sha256 `0e46460e...7936`, 386,581 bytes, equal to `PinnedLauncher` and DECISIONS-W5.
  apksigner: v2 and v3, no v1 and no v4, one signer, `O=cleanyourtv, CN=Clean Your TV launcher`,
  `57641815...5132` = `SIGNER_SHA256`. aapt2: `com.gathin.tvhome` 5 / 2.4, minSdk 26, targetSdk 34,
  three permissions (network state, Wi-Fi state, fine location, never granted by us), the two new
  activities not exported. F5 is the gap.
- **Takeover check.** `ShellRenderer.check` needs "Status: ok" and no Error line from `am start -W`
  (the step's own stdout), and the brought-to-front case passes (`startAndWaitNeedsStatusOk`). The
  look's start and the curtain's start stay CONTINUE.
- **Curtain F8 and F9, and merge cleanliness.** main is still `f553232` and the branch descends from
  it, so the merge is a fast forward. No line of the curtain was removed: the three removed lines
  that mention it are rewritten with the curtain argument kept. `tv/`, `:core:engine` main and the
  curtain asset are untouched. F9 is now done: `curtainIsOurs` compares a real signer when the probe
  reads one and still needs a journal record of our install (F2 of RF-CURTAIN-1 holds, so a forged
  signer alone is not enough for the curtain). F8 is still the open W8 follow-up (F19).
- **Single exec call site and argv safety.** The engine's one `session.exec` is unchanged. The new
  shell text is all in `ProbeScripts` (golden-scripts.txt updated) or typed ops through the renderer.
  No user string reaches a line.
- **Frozen interfaces.** RFC-W4-2 (5.7) and RFC-LAUNCHER-1 (5.8) are in RFCS.md with DECISIONS-W1
  entries, marked in code, trailing and defaulted. The non-frozen additions are listed for the merge.
  As the review gate I sign both RFCs off, subject to F9's 5.8 text.
- **Writing rules.** No em dash and no double hyphen in added copy, comments or commit messages
  (only shell long options and XML comment syntax). The commits carry no attribution lines.
