# RF review: launcher v2.5.0 (RFC-W4-3, upstream #340, #350, #377, #421, #424, #435, owner decisions D1 to D4)

Reviewer: review gate, 2026-10-21. Scope: branch `launcher250` in `cyt-wt/launcher250`, eleven
commits on main `e232b01` (b222f7f, c0883ad, 0af23fb, 4e9a5c0, 51d938c, 488ac7f, 2e8cb68, 1fc7f2f,
30b78cd, e74a700, 0b937c4; `git diff e232b01..launcher250`, 83 files, 48 of them re-recorded
Roborazzi PNGs), against `app-design/LAUNCHER-250-PLAN.md`, RF-LAUNCHER-240 and RFC-LAUNCHER-1 as
precedent, RFC-W4-3 in RFCS.md, the DECISIONS-W1 entry, PRODUCT-SPEC 5.7 and section 5 rules, and
SECURITY 3.3 and V1. Upstream was read at v2.4.0 (69c93b6) and v2.5.0 (9071ec8): `Order.java`,
`MainActivity` (dock edit, reorder, `endReorder`), `AppAdapter` (saved order, `launch_actions`),
`Config.java` (`load`, `syncThemeChoices`, `packageMap`, `stringList`), `RemoteKeys`,
`InputKeyService`, `HomeKeyService`, `tools/disable.sh` and `tools/theme.sh`. The bundled APK's
sha256, size, aapt2 badging and manifest were checked locally; apksigner could not run locally (no
JDK), so the signer rests on the committed build output and `LauncherPinTest`. No build was run for
this review: every finding below is certain from the code.

## Verdict: CHANGES REQUIRED BEFORE MERGE

The upgrade itself is sound. The pinned APK is what the docs say, the build is reproducible and now
refuses another application ID, the codec models the nine new keys as `Config.java` reads them and
is held to the real v2.5.0 code, and the TV-side dock claim (#340) is right about the launcher:
`order.txt` is keyed by the exact `dock_apps` list in `config.json`, so a push with the same list
keeps the TV's dock and order. No Critical finding.

But the new #421 guard, the one safety addition of this branch, has two gaps. Outside the takeover
it turns an expected refusal into a stop card whose "Carry on" retries the same refused step
forever (F1). Inside the takeover it does not apply at all, so a stock launcher that asks for
TV_INPUT_HARDWARE is still switched off, which upstream's own `disable.sh` never does (F2). The
#424 accessibility step widens a window in which "Stop here" leaves the TV with no accessibility
services (F3). The phone itself changes `dock_apps` on a look change when a dock app has gone, which
breaks the note's promise (F4). The CHANGELOG claims a warning nobody sees (F5). The docs and the
test evidence are not complete (F6, F7). Seven items must be done before merge.

## Must do before merge

- **F1** A run-time TV input refusal must be a skip, not a pause: the run ends with skips, and Done
  and Manage say the package was kept because it runs the TV's inputs (owner copy). Flows tests for
  Setup and Manage switch-off.
- **F2** No takeover exemption for the TV input rule: a stock launcher or wizard that asks for
  TV_INPUT_HARDWARE is kept enabled (preferred) or the takeover is refused and rolled back. Test.
- **F3** "Stop here" on a run paused inside the accessibility restore must not leave the list empty
  and accessibility off: finish the restore first, or withhold Stop here for that pause. Test.
- **F4** SetTheme and RestoreMine push the stored dock list unchanged (as ModulesMachine already
  does), so a new look keeps `dock_apps` when a dock app has gone. Flows test; DockLayersTest gains
  the 13 to 18 app case and the plan and RFC name the six-columns to four-tiles limit.
- **F5** CHANGELOG: drop or reword "warns when a settings file would take Back ..." (the warnings
  reach no screen); recheck the TV input bullet once F1 and F2 are in.
- **F6** Docs: PRODUCT-SPEC 5.7 (RFC-W4-3, "built from v2.5.0"), SECURITY V1 (key services,
  ThemeProvider) and SECURITY 3.3 (`accessibility_enabled 0` in the restore); mark RFC-W4-3 reviewed.
- **F7** Merge with or after `profiles250` (this branch alone does not build at 9071ec8), run the full
  suite on the combined tree in verify mode, run S06 on API 28 for the new restore sequence, attach the
  summaries with test counts and the differential tally lines.

## Findings

| # | Sev | Where | Finding | Status |
| :- | :- | :- | :- | :- |
| F1 | Must fix (Medium) | `DefaultEngine.runStep`, `tvInputHardwareReason`, `execute` outcome handling; `SetupMachine.onPaused`; `ManageCommon.resolveOpen` | The guard returns `Outcome.Failed(Policy.ProtectedPackage)`. Debloat and switch-off steps are `SKIP_IF_REFUSED`, which skips only `Outcome.Refused`, so a Failed outcome pauses the run with STEP_FAILED. Setup then shows "We stopped. One step didn't work ..." with "Carry on with the rest", and `continueRun` restarts at `run.at`, the refused step, which is refused again: the person can only undo everything or stop with the rest of Setup (and the home screen switch, when it comes later) not done. The live Denylist and home screen checks share this path, but they only fire on a TV that changed after planning. This one fires on every run whose profile lists an input driver that registers no TIF input, because the planner cannot see requested permissions (D4). So the plan's "the S3 list can still show such a package as switched off, and the run then reports it protected" is not what happens. `theLiveCheckRefusesToSwitchOffATvInputDriver` asserts the pause, which hid it. Fix: for a step whose policy is SKIP_IF_REFUSED or CONTINUE, a live protected refusal is journaled as now and then skipped (a Refused outcome or a dedicated skip), so the run goes on and ends COMPLETED_WITH_SKIPS. The generic Done line "{name} wouldn't switch off; it's harmless." is wrong for this case; add a kept line for it, for example "We kept {name}: it runs the TV's HDMI and tuner inputs." (owner copy), and the same in Manage's switch-off result. Tests: the engine test asserts a skip and a finished run; a SetupMachine test with a profile group holding a driver ends on Done with the kept line; a Manage switch-off of a driver ends without an open-run card. | Open |
| F2 | Must fix (Medium) | `DefaultEngine.tvInputHardwareReason` (the `inSection` exemption), `DefaultPlanner.takeover` (`keepStockEnabled`) | The rule is skipped for steps inside the open takeover section, "as for the home screen rule". That exemption exists for the home rule because the stock launcher is a HOME claimant by definition; nothing requires it for the TV input rule. The section's steps are the stock launcher's and the wizard's Disable, both switched off by default in Setup. `keepStockEnabled` keeps a stock launcher enabled only when `Denylist.reason` names it, which covers a registered TIF input but not a package that only asks for TV_INPUT_HARDWARE. Upstream's `disable.sh` applies the check to every package in the list, the old launcher included (only `TVINPUT_ALLOW=1` overrides). So on a TV whose stock launcher hosts input hardware, 0.2.0 would black the HDMI ports by default, against safety rule 8, and the plan's "it covers every flow" is not true. Fix, preferred: inside the section a Disable refused by this rule is skipped and the section goes on (SetHome makes ours the default; step R's Enable of a package that was never switched off is harmless), so the stock launcher stays enabled as D25 does for a protected one. Minimal: drop the exemption, so the STOP_AND_ROLLBACK step rolls the takeover back with the existing card. Test: a takeover on a TV whose stock launcher's dump requests TV_INPUT_HARDWARE never sends `pm disable-user` for it. | Open |
| F3 | Must fix (Low) | `DefaultPlanner.restoreAccessibility`, `DefaultEngine.abandon`, the RESUME_AFTER_DEATH and Manage open-run cards | The restore is now six separate journaled execs: Settle 1, clear the list, `accessibility_enabled 0`, Settle 1, write the list back, `accessibility_enabled 1`. Upstream runs the same sequence as one shell line on the TV. Between the clear and the write back the TV has no accessibility services at all (TalkBack or a remote helper included) and, after step 3, accessibility off. 2.4 had the same window with no sleep in it; 2.5 adds a second of it plus the global switch. If the phone app dies or the connection drops there, the journal holds the run, and "Carry on" or "Undo" both put the list and `1` back (priors are the probed values; checked). Nothing on the TV restores it by itself: the home watchdog is armed only for the takeover. And "Stop here" (`abandon`, step R only) gives the run up with the list empty and accessibility off, and says nothing. Fix: when the paused run has done a step of the restore group but not its last, Stop here first runs the remaining restore steps (they are typed, CONTINUE and idempotent), or that card offers only Carry on and Undo. Test: abandon after step 3 leaves the probed list and `1` on the TV. Follow-up (F9): a single TV-side line for the four writes, like upstream's. | Open |
| F4 | Must fix (Low) | `HomeScreenMachine.handle` (SetTheme, RestoreMine), `HomeScreenMachine.load`, `DockLayersTest.aNewLookWritesTheSameDockApps`, `home_tv_changes_note` | The launcher side of #340 holds (see "What was checked"), but the phone can change `dock_apps` on a look change by itself. SetTheme and RestoreMine push `after.dock`, which `load()` builds with `DockLayers.decode(choice.dock, shown, columns)`: an app since uninstalled or switched off is dropped, so the pushed list differs and the TV's own dock and order stop applying, although the note says "a new look keeps it". On the TV the launcher keeps `cfg.dockApps` unfiltered (an uninstalled tile just drops out), so nothing there forces the change. `ModulesMachine` already pushes the stored `choice.dock` unchanged. Fix: SetTheme and RestoreMine push the stored list too (the sheet keeps showing the decoded one; SetDock is the only way the list changes). Second edge, for the plan and RFC only: `applyTo` trims to the new look's three rows, so a dock of 13 to 18 apps saved under Six columns is cut to 12 by a four-tile look and the TV's dock edit is dropped; the Dock row then visibly changes, which is acceptable if said. `DockLayersTest` stops at 12 apps, below where the looks differ; add 13 and 18 and assert the cut, and a HomeScreenMachine test that SetTheme with a gone dock app pushes the stored list. | Open |
| F5 | Must fix (Low, docs) | `CHANGELOG.md` 0.2.0 | "The phone reads the settings keys launcher 2.5 added exactly as the launcher does, and warns when a settings file would take Back, the arrows, OK, Enter or Menu": the codec's warnings are read by no code outside `core/spec` (no `.warnings` use in flows, features or app), and drift compares digests only, so no person ever sees this warning. Say what is true for a reader of a changelog (for example drop the bullet, or "reads the new settings keys exactly as launcher 2.5 does"). The bullet "Never switches off an app that asks for the TV input hardware permission ... even when a profile lists it" is true only once F2 is done; with F1 the line could add "and says so". | Open |
| F6 | Must do before merge (docs) | PRODUCT-SPEC, SECURITY (outside git), RFCS.md | Not edited yet (checked): PRODUCT-SPEC 5.7 needs the RFC-W4-3 paragraph (the nine fields, `LauncherRace`, `LauncherRaceSession`) and "built from v2.5.0"; SECURITY V1 needs the plan's key services sentence and the ThemeProvider note (exported, guarded by WRITE_SECURE_SETTINGS, so a development-granted app can write `config.json`); SECURITY 3.3's `accessibility_enabled` row says the restore "writes `1` only when the TV had `1`" and must now also name the `0` step (upstream #424) and F3's rule. DECISIONS-W1 should name each app-design edit, as RF-LAUNCHER-240 asked. Mark RFC-W4-3 reviewed: as the review gate I sign it off as written (the fields are trailing, defaulted and marked; no v2.4.0 key changed; `KEY_ORDER` matches `Config.load`). | Open, trivial |
| F7 | Must do before merge (process) | whole branch | The plan says the branch builds only with `profiles250`: at 9071ec8 `convertProfiles` fails without meta for the 54 new folders, and the checks ran with an uncommitted stand-in. Merged alone, main would not build. Merge after or together with `profiles250`, then run on the combined tree, in verify mode, the same task list as RF-LAUNCHER-240 F8 plus `:core:trust:check`, and S06 on API 28 (S06 exercises the accessibility restore on a real adbd; the sequence changed, so "S06's end state is unaffected" needs a run, not an argument). No test result is in the worktree; attach the summaries with counts, the 10,000-file tally line for v2.5.0 and the 2.0 tally. | Open, process |
| F8 | Low | `HomeScreenMachine.update`, `PlanRequest.UpdateLauncher` | "Update the home screen" (`pm install -r`) replaces the running launcher and its accessibility services without the restore. Android normally rebinds an updated package's enabled services, but #424 exists because TCL G03 sets keep the service in their crashed list after a force-stop; an update may do the same. Upstream's build-and-install re-enables after installing. Consider the same restore after an in-place update when the probed list names ours and accessibility is on. | Follow-up |
| F9 | Low | `restoreAccessibility` | As F3: render the clear, off, write back and on as one TV-side line (one typed composite op, RFC for 5.2), so a lost phone cannot split it. | Follow-up |
| F10 | Low | codec warnings, drift banner | V1 widens with 2.5 (input_key_keycodes and app_keys are filtered in every app while a key service is on), yet no warning reaches a screen. When drift is found, decode the TV's overlay from the fresh snapshot and say in the banner when it takes a remote button. | Follow-up |
| F11 | Low | `ConfigCodecImpl.appKeyEntry` | A `KEYCODE_` name that Android does not know resolves to 0 upstream and the entry is skipped; the codec keeps it silently (code -1). Warn "the launcher skips it if Android has no such key", or carry `KeyEvent`'s name table. KEYCODE_HOME (3) in `app_keys` is not in the navigation set; consider it with RF-LAUNCHER-240 F18's 109 and 160. | Follow-up |
| F12 | Low | `home_tv_changes_note` | "last until you change the home screen here": a card added in Cards and modules and a Setup re-run also push `config.json` and clear the TV-side choices. Pre-existing wording; fine for 0.2.0, worth a word when the note is next touched. | Follow-up |
| F13 | Info | `PackageDump.requestsTvInputHardware` | Stricter than upstream's `grep TV_INPUT_HARDWARE` substring: only `android.permission.TV_INPUT_HARDWARE` exactly, or with `:` flags, counts. A vendor-prefixed look-alike does not count. Deliberate and fine; a false positive would only keep a package. | Info |
| F14 | Info | `LAUNCHER-250-build-output.txt` | The header names the build script, whose file name carries the build host's name. That name is already public in CONTRIBUTING.md and the scripts folder, so no change is needed; keep further prose to "the build host". | Info |

## Rulings on the owner decisions and the builder's claims

- **D1, the honest note: upheld, with F4.** The copy is plain and true for the launcher: "A dock
  changed on the TV is not shown above: a new look keeps it, a different dock saved here replaces
  it." With F4 the phone keeps that promise too. A confirmation in the Dock sheet is not needed: the
  note sits directly under the Dock row, and a confirmation on every save would teach people to
  tap through it.
- **TV-side choices cleared by every push: acceptable and honestly said.** This is upstream's
  design (`syncThemeChoices` clears columns, motion, effects, rows below the dock and the wallpaper
  pick whenever `config.json`'s length or mtime or `theme_revision` changes), unchanged from 2.4 in
  kind; the two new choices are cleared the same way, and
  `launcher25ChoicesOnTheTvLastUntilTheNextPush` proves it on the real code. "such as tiles per row
  or motion" covers rows and the wallpaper pick without listing them. RF-LAUNCHER-240 F17 still
  applies: a future "skip a push with the same digest" must write `theme_revision`.
- **D2, keep #377 off and the bundled ID `com.gathin.tvhome`: upheld.** The build passes the
  property explicitly (equal to upstream's default, so the notices' "without changes" holds) and
  the sign stage stops unless the badging names `com.gathin.tvhome`; the pin checks the bytes anyway.
- **D3, ThemeProvider as a write path after 0.2.0 with an RFC: upheld.** It needs a new op in 5.2;
  a TV whose shell cannot write `Android/data` fails safely today.
- **D4, plan-time marking of TV_INPUT_HARDWARE packages after 0.2.0: upheld only with F1 and F2.**
  The run-time guard is the right safety net, but as built it either stops the run (F1) or does not
  run (F2). With F1, S3 still lists such a package under "switched off" and Done then says it was
  kept and why; that is honest enough for 0.2.0. Profiles need not turn input drivers into ask rows
  (an ask row the person ticks hits the same guard and explains nothing). Where `profiles250` or
  `facts.tsv` already knows a package is an input driver, `neverDisable` is better: S3 then shows it
  under "We keep these" with its reason. For the plan-time read, upstream's one round trip (a loop
  on the TV over the profile's packages, printing only the names that request the permission) fits
  the probe rules as a fixed script with validated package names.
- **"#350 is already covered": upheld.** `DefaultPlanner.protectedReason` refuses the resolved HOME,
  every HOME claimant and every profile stock launcher outside the takeover, for Setup groups,
  extra-off, Manage switch-off and leftovers; `DefaultEngine.homeScreenReason` repeats it at run time
  on a fresh snapshot. A vendor FallbackHome is a claimant, so it is protected too, which is more
  cautious than upstream, and there is no FORCE_HOME.
- **The #435 fix: upheld.** `otherLauncherName` now drops claimants whose class ends in
  `FallbackHome` (upstream's `/[^ ]*FallbackHome$`) and `com.android.provision`; the settings apps
  were already excluded by name. `aFallbackHomeIsNotAnotherLauncher` covers the TCL placeholder,
  provision and a real other launcher. The card then is HOME_UNKNOWN, which offers diagnostics or the
  old home screen, never "switch to the other launcher".

## What was checked and held

- **#340 in upstream code.** `Order` stores `tvhome-order 1`, `dock <dock_apps joined by commas>`
  and the tile keys. `Order.ranks` returns the saved ranks only when that dock line equals
  `dockKey(dockApps)`, and `MainActivity` passes `cfg.dockApps`, the list `Config.stringList` read
  from `config.json` (trimmed, non-blank, not filtered by what is installed). Edit dock
  (`startDockEdit`, `swapIntoDock`) and Reorder both save in `endReorder` under the current
  `dockApps`. So: a push with the same list keeps the TV's dock and order; a different list makes the
  file not apply (it is kept, ignored, never deleted); an app uninstalled on the TV changes nothing
  there (its tile drops out of the order). A push that leaves `dock_apps` out falls back to
  `config.xml`'s list, the same each time. The phone's encoder writes `PackageName` values, which
  are already trimmed, so the keys compare equal. The only phone-side changes of the list are F4's.
- **What the phone can read.** `order.txt` and `launcher_settings` are private; ThemeProvider
  exposes only `config.json`, `wallpaper.jpg`, `theme_revision` and `wallpapers/`. So the note's
  "is not shown above" is right, and a TV-side dock edit never shows as drift (it does not touch
  `config.json`).
- **The #421 parser.** Only lines between `requested permissions:` and the next line ending in
  `permissions:` (install, runtime), a `User N:` line or the next `Package [` count; lines are
  trimmed, so CRLF is harmless; a shared user's grants print under its own install or runtime block
  and do not count; `TV_INPUT_HARDWARE_EXTRA` does not count. The test covers each. The dump is the
  engine's existing typed read (`ProbeKind.PACKAGE_DUMP` with a validated `PackageName`), so there
  is no new shell text and no injection path. A failed read fails the step closed ("dumpsys package
  failed"); an empty dump is "package not on the TV". Putting a package back is never refused:
  `Enable` is not a write target, and the rule returns null for anything but `Disable`.
- **#424 sequence.** Matches upstream's theme.sh order and its one-second sleeps; planned only when
  the probed list names ours and `accessibility_enabled` is `1` (RF-LAUNCHER-240 F7 holds); all
  CONTINUE in the theme or undo group, typed and shown in Show commands; the `0` step's prior is `1`,
  so undo turns it back on; the renderer allows `0` or `1` for that key (SECURITY C8 and 3.3).
  `Launcher24PlanTest` pins the six ops and both priors. F3 and F9 are the gaps.
- **Config codec, nine keys.** `KEY_ORDER` follows `Config.load` at 9071ec8 line by line (the
  package maps after `live_tv_action`, the key services and `cinematic_home` after
  `settings_key_panel`, the wallpaper numbers after `wallpaper`, `race_calendar` last). The old keys
  keep their relative order, so every `config.json` the app wrote before is byte for byte the same
  (`{"columns": 6}` is asserted). `packageMapEntry` is `Config.packageMap` (first `=` after one
  character, both halves trimmed and non-empty, later entry wins); `input_key_keycodes` uses the
  all-or-nothing `intArray` rule; the wallpaper numbers read from `config.json` alone, 0 to 1440 and
  0 to 90; `race_calendar` ports `optString` and `optInt` and RaceWeekend's normalisation. Encode
  refuses untrimmed strings, out-of-range numbers and a calendar not in the launcher's own form.
  Navigation keys (4, 19 to 23, 66, 82, by number or `KEYCODE_` name) in `input_key_keycodes` or
  `app_keys` warn without rejecting.
- **`launch_actions` safety.** `AppAdapter` builds `new Intent(action).setPackage(e.pkg)` and uses
  it only when it resolves, so a pushed file can only make a tile open another exported activity of
  the same app; it cannot start another package or an arbitrary component. The app never writes any
  of the nine keys: outside `ConfigCodec.kt` and `LauncherConfig.kt` no main source names them;
  every push is `Look.encode` of a bundled theme doc plus the dock and `module_cards`; the three
  bundled themes are unchanged at v2.5.0 and hold none of the keys; nothing decodes the TV's overlay
  into a pushed doc (Cards reads only `module_cards` from it). Profile data never reaches a theme doc.
- **Differential test.** `WallpaperRotation.java` and `RaceWeekend.java` in the test sources equal
  the submodule's files except the package line (diffed here, and
  `vendoredLauncherClassesAreTheSubmodulesOwn` holds it). The generator, the hand cases (including
  wrong types, skipped entries and a mangled calendar) and the round trip cover the nine keys; launcher
  2.0 stays the second target for the old keys.
- **Pins and the APK.** The asset is sha256 `42692e10...6bdd`, 421,299 bytes (computed here), equal
  to `PinnedLauncher`, the build output and the plan. aapt2 (run here): `com.gathin.tvhome`, 6 / 2.5,
  targetSdk 34, the same three permissions as 2.4; one HOME activity (`MainActivity`); InputKeyService
  not exported; HomeKeyService behind BIND_ACCESSIBILITY_SERVICE; `ThemeProvider` exported, authority
  `com.gathin.tvhome.theme`, guarded by WRITE_SECURE_SETTINGS; settings activities not exported.
  Signer `57641815...5132` per the build output and `LauncherPinTest`. `PIN`, `upstreamCommit`,
  `UPSTREAM_COMMIT`, the submodule gitlink and the notices all name 9071ec8.
- **Build script.** Two fresh builds, both re-signed in memory, `cmp ... \|\| exit 1` (RF-LAUNCHER-240
  F5 is in), then the badging guard. The sign stage runs `bash -euc` without pipefail, so
  `printf | grep -q` cannot fail open or falsely; a wrong package stops the script before the copy.
  The filtered output is committed.
- **Frozen interface discipline.** RFC-W4-3 is in RFCS.md with what, why, how, tests, the smallest
  alternative and the affected streams; DECISIONS-W1 lists the 5.7 edit and the pending app-design
  edits; the new fields are trailing, nullable or defaulted and marked `// RFC-W4-3`, so every
  caller compiles. `PackageDump.requestsTvInputHardware` and the SetupMachine change are not frozen.
- **Goldens.** All 48 changed PNGs are `home_*` shots; the commit that re-records them changes no
  other file, and the copy commit changes only the string and a comment. Two pairs were compared by
  eye: only the note's text, and the layout below it, moved.
- **Fixtures.** 147 `examples/*/debloat.txt` at 9071ec8 (93 plus the 54 new folders).
- **Writing and commits.** No em dash in any added line; the only double hyphens are XML comment
  syntax and Gradle flags. All eleven commits are by the noreply address with plain subjects and no
  trailers, co-author lines or session links. No roadmap talk.
