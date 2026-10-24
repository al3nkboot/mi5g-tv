# RFCs from the dockrows workstream

## RFC-DOCK-1: the Setup plan should push the dock and the look (applied; Fable APPROVE WITH FOLLOW-UPS, reviews/RF-DOCK-1.md)

What: `DefaultPlanner.setup` uses `Choices.dock` only for `PlanSummary.dock` (the names on S3 and the
check screen) and never uses `Choices.theme`; the Setup plan has no config.json step at all (the
emulator plan in `WhatWeFoundMatchesThePlanTest.emulatorCapture` is nine steps, none of them a
push). So the dock and look picked in the wizard, rows included, never reach the TV: the launcher
starts on its built-in dock (Netflix, Prime Video, YouTube, Hotstar), and only a later change on
Manage (M2, an ApplyTheme plan) writes config.json. S3 says "Home screen: Netflix, YouTube, ..."
and the check screen "with ... along the bottom", which is only true when the built-in dock
happens to match. `SetupMachine.showDone` already looks for the run's config.json push (D15, R5 F10)
and finds none. Why it is not fixed here: the planner is pure and takes blobs as `BlobRef`, and
`PlanRequest.Setup(resolution, choices, launcher, level)` is frozen (section 5.8), so the encoded
config cannot reach the Setup plan without an interface change. Smallest change: add
`val config: BlobRef? = null` (and `wallpaper: BlobRef? = null`) to `PlanRequest.Setup`, defaulted so
every caller compiles; `SetupMachine.replan` encodes `DockLayers.applyTo(theme?.doc ?: LauncherConfigDoc(), dock)`
into the BlobStore like `HomeScreenMachine.apply` does, and `DefaultPlanner.setup` appends
`configTransport.writePlanSteps(config, wallpaper)` after the takeover section (group "theme"), so
S3's commands list and the plan hash include it. Affected: W3 (planner), W8 (SetupMachine, Done
records the digest it already looks for), W1 (fakes). Alternative without an interface change: run
an ApplyTheme plan after Done, which the person would not see in S3's "Show commands"; not
recommended (plan honesty).

Applied (setupcfg, 2026-10-20, pending Fable review): `PlanRequest.Setup` gains `config: BlobRef? = null`
and `wallpaper: BlobRef? = null` after its four parameters (DECISIONS-W1 lists the edit).
`SetupMachine.replan` builds both with `Look.encode`, the helper `HomeScreenMachine.apply` now uses
too, whenever the switch is possible; `SetupDeps` gains `blobs` (and `encoder`, defaulted). In
`DefaultPlanner.setup` the transport's steps (group "theme") come after the launcher install and
before the guarded home screen switch, only when the switch is chosen and the launcher is on the TV
or installed by the plan; ApplyTheme and Setup share one private function for the steps, their
priors and narration. Two choices to review: (1) the steps sit before the section rather than after
it, so the check screen's "with ... along the bottom" is what the TV shows when the person is asked,
nothing is added to the watchdog's window, and a failed push happens while the TV's own home screen
is still in charge; (2) in Setup the pushes CONTINUE on failure (ApplyTheme keeps
STOP_AND_ROLLBACK): outside a section STOP_AND_ROLLBACK pauses the run, and the Setup stop card's
way back is undoing the whole run for a cosmetic step that Manage can send again. On a fresh or
replaced launcher both pushes record `present = false`, so Undo everything and the laptop restore
scripts delete them again; the laptop scripts also delete a wallpaper whose first record is that
removal when a later look pushed one.

## RFC-CURTAIN-1: the TV curtain (applied on curtain; APPROVE WITH FOLLOW-UPS, reviews/RF-CURTAIN-1.md)

What: a bundled, pinned TV app (`tv.cleanyour.curtain`, new module `:tv:curtain`, signed with the
launcher key K1) that the Setup plan installs after the engine's restore point, cues once per
narration stage, and uninstalls as its last step, so the TV shows the approved Lantern animation
while the phone works. Full plan: `app-design/CURTAIN-PLAN.md`. Interface changes, each binding
under section 5: (1) `:core:model` 5.2: `enum class CurtainStage { SCAN, RESTORE_SAVED, OFF,
GROUP_DONE, INSTALL, INSTALLED, FINALE }` and `data class Op.CurtainCue(receiver: Component,
token: RunToken, stage: CurtainStage, count: Int, total: Int)`, rendered by `ShellRenderer` as one
fixed `am broadcast` argv (component, constant action, token, stage name, count, total; no free
text), `Privilege.PROFILE_STEP`, no verify, `inverse` null, `UndoOrder.target` null; JSON tag
`curtain_cue` in `OpJson` and `ChangeJson`. (2) `:core:planner` 5.8: `PlanRequest.Setup` gains
`curtain: LauncherArtifact? = null`; `Choices` gains `curtain: Boolean = true`; group id `curtain`;
every curtain step `CONTINUE`; `Uninstall(curtain)` also planned first by Leftovers and DebugOff
when the package is present. (3) `:core:trust`: `PinnedCurtain`, `interface CurtainPin { fun
bundled(): LauncherArtifact }`, `BundledCurtainPin`, `verifyCurtainPin` before `:android:platform`
`preBuild`. (4) `:core:engine`: a step with a blank narration is silent (not listed on the Working
screen, not counted in the percent). (5) `build-logic` `ModuleRules`: layer entry `:tv:curtain` at
10 with no edges in or out and an empty external allowlist; a size gate entry at 256 KB. (6)
`WizardDraft.curtain: Boolean = true` (growable, listed for completeness). Why: the approved design
needs a TV-side renderer fed by typed cues; the existing verbs cover install, start and uninstall,
only the cue is new. Affected: W1 (model, fakes, build-logic), W3 (ops, planner, engine), W5
(trust, bundled asset), W6 (ChangeJson, RestoreCommands branches), W8 (flows, Customise switch),
W9 (forge build script, e2e). Smallest alternative: no new op, cues as `PushAsset` of a one-line
file polled by the curtain; rejected because a push per stage costs a sync round trip, shows as a
sync line with a hash in Show commands, and leaves files to clean. Ordering: applied on `main` after
RFC-DOCK-1 (`setupcfg`) merges; the FINALE cue goes before whichever step starts the launcher first.

Applied (curtain, 2026-10-20, owner answers to CURTAIN-PLAN section 8): (1) the TV says the phone's
words during cleaning ("Adverts: 2 of 4"), so `Op.CurtainCue` carries three more fields after the
five of the plan, all defaulted: `group: CurtainGroup = NONE`, `groupCount: Int = 0`,
`groupTotal: Int = 0`, with `enum class CurtainGroup { NONE, ADVERTS, TRACKING, NOBODY_ASKED,
BRAND_EXTRAS, SHOP_DEMO, OTHER }` in `:core:model`. The cue line gains three constant extras
(`group` as a string re-checked against `^[A-Z_]{1,16}$`, `gcount` and `gtotal` as ints 0 to 999);
`count` and `total` stay the switch-offs over the whole plan, for the percent. The words for each
group live in the curtain APK; the brand in "{brand} extras" is the TV's own `Build.BRAND`, read on
the TV. (2) The switch "Show the progress on the TV" is in Custom only, default on. (3) Silent. (4)
Signed with the launcher key K1. One tightening beyond the plan: the renderer refuses a cue whose
receiver package is not `tv.cleanyour.curtain`, so a cue can never be aimed at another app.

Two choices the plan left open, for review: (a) `DebugOff` does NOT gain the leftover uninstall of
section 4 (b). An `Uninstall` is a PROFILE_STEP, so it would make the Finish plan need a credential
pass; a cancelled prompt would then leave debugging on (D7). A leftover is still finished on the TV
by the curtain itself (four minutes, Back, or covered), removed by Leftovers, and replaced or reused
by the next Setup. (b) The probe reports no signer for any package (`PackageState.signer` is null),
so "the pin is already installed with our signer" is read as: the package is present, its
versionCode is the pin's, and no other signer is known; a package under our name with a known other
signer gets no curtain steps and is never uninstalled. The planner takes the pinned signer as
`curtainSigner` (AppGraph passes `PinnedCurtain.SIGNER_SHA256`). A Setup with no steps shows no
curtain.
(c) The Customise switch needs an intent: `SetupIntent` (frozen, 5.12) gains
`ShowOnTv(on: Boolean)`; `SetupDeps` gains `curtainPin: CurtainPin? = null`. The switch shows in
Custom only; replan hands the pin's artifact in when the level is Simple or the draft's `curtain` is
on, and plans without it when the asset does not match its pin.

Review (RF-CURTAIN-1, APPROVE WITH FOLLOW-UPS): F1 applied (a silent step never forces a
thumbnail; the engine compares a voiced step's group with the last voiced step's, test
`silentStepsForceNoThumbnail`). F2 applied: "ours" is the journal rule of `removeModule` (installed
by us, or an install of the curtain in this TV's change records), replacing (b) above; a curtain
package without such a record gets no Setup steps and no Leftovers step
(`onlyACurtainWeInstalledIsTouched`). F3 main merged. F5: SECURITY 3.2 has the `curtain_cue` row,
PRODUCT-SPEC 5.8 and DECISIONS-W1 list `PlanRequest.Setup.curtain` and `Choices.curtain`. F6 in part:
the cue line ends with `--receiver-foreground` (goldens 53 to 59); the lead reads the cue steps' ms
from the Mi TV journal. F7 applied: CueBus keeps early cues for any curtain that attaches, only
those of the newest run's token. F11 applied: the key frames go to build/curtain-frames, and to
src/test/frames only with -PcurtainFrames. Follow-ups, open:
- F6 rest: if Mi TV cue steps come near seconds, give cues a short timeout and count a cue's
  timeout as Failed (CONTINUE), not Lost.
- F8 (W8): after a rolled-back section the final uninstall is skipped; SetupMachine could run a
  one-step Leftovers with the run's own pass before Done or Undone.
- F9 (W4): read the curtain's signer the way the launcher's is read, so the signer test is real.
- F10 (W9): build-curtain-on-forge.sh builds the working tree but stamps the commit time; refuse a
  dirty tree or build from `git archive HEAD`.

## RFC-CARDS-1: module cards stay off until the person asks for a card (applied on cardsoptin; Fable APPROVE WITH FOLLOW-UPS, reviews/RF-CARDS-1.md)

What: the owner's first real Setup ended with the new home screen asking "New card from Retro ...
Keep hidden / Show card", because Retro was already on the TV and the config.json Setup pushes
(RFC-DOCK-1) leaves `module_cards` out, which the launcher reads as on (upstream main 1bbf0c9,
MainActivity: with it on, every installed app that declares a DOCK_CARD is discovered and asked
about; with it off, no discovery, no prompt, and the Settings long press does nothing). Cards are
optional and something to add after Setup, never forced. Change: (1) `Look.encode` always writes
`module_cards`, from the TV's cards choice (`Cards.of`, rule as revised by RF-CARDS-1 F1): the value
kept in HomePrefs (`tvs/<id>/home.txt`, new line `cards=true|false`, `HomeChoice.cards: Boolean? =
null`) wins; else the TV's current overlay, whoever wrote it, when it carries an explicit
`module_cards`; else the caller's default, Setup off and Manage on (what the launcher does on that
TV now, its built-in default, so a dock or theme change never takes a card away). A first Setup is therefore always off,
and so is a re-run on a TV whose earlier overlay left the key out; a TV whose cards were turned on
earlier keeps them. `SetupDeps` gains `homePrefs: HomePrefs? = null`;
Done keeps what the run pushed (dock, theme, cards) so Manage starts from it. M2 (HomeScreenMachine)
keeps the current value on every dock or theme change. (2) Cards come on only from Cards and
modules: `PlanRequest.AddModule` gains `config: BlobRef? = null, wallpaper: BlobRef? = null`
(frozen section 5.8, defaulted so every caller compiles); when the TV's home screen is ours and its
cards are off, the Modules add encodes the kept dock and theme with `module_cards` true and the
planner appends `configTransport.writePlanSteps(config, wallpaper)` after the install and before
Home, with ApplyTheme's priors, narration and failure policy (STOP_AND_ROLLBACK), so the plan, its
hash and Show commands carry the push and the launcher's restart is what makes the TV ask. A card
module already on the TV gets "Show its card" on M3 while cards are off: the same look as an
ApplyTheme plan, then the M3c mirror. Turning cards off again is not offered: with no card
module left nothing is discovered and nothing asks, and a later install of an app with a card is
the person's own choice. Affected: W3 (planner), W8 (flows, M3 screen), W1 (DECISIONS-W1 lists the
5.8 edit). Alternative without an interface change: a second ApplyTheme run after the add; not
chosen, because the person's one tap would be two plans and two journal entries, and a failed push
would leave a module installed with cards still off and no honest way to say so.

Review (RF-CARDS-1, Fable APPROVE WITH FOLLOW-UPS): F1 applied (the rule above, tests in
`aTvWithoutAKeptChoiceKeepsWhatItsOverlaySays` for both defaults), F2 applied (M3b says, while cards
are off, that adding the module turns them on), F5 applied (PRODUCT-SPEC 5.8 shows the new fields).
Follow-ups, open:
- F3: the add's look pushes STOP_AND_ROLLBACK, so a failed push pauses the run for the open-run
  card. "Undo what we did" there uninstalls the module but cannot put the earlier config.json back
  (its prior is the probed overlay, the 0.1 limit on files that existed), so cards stay on after
  that undo. Add a sentence to the card's undo question when the plan is an add with a look, or
  accept and document.
- F4: "our launcher is home" is `tv.launcher != null`, which misses a HOME changed by hand on the
  TV; the add's RestartHome then starts our launcher over the stock home for a moment (ApplyTheme
  has the same gap). A check in `cardsOnLook` that the latest snapshot's resolved HOME is the
  launcher would close it.


## RFC-W4-2: the keys of launcher v2.4.0 in the config codec (applied on launcher240; APPROVE WITH FOLLOW-UPS, reviews/RF-LAUNCHER-240.md)

What: `LauncherConfigDoc` and `EffectiveConfig` (frozen, PRODUCT-SPEC 5.7) gain one field per key
that upstream v2.4.0's `Config.java` reads beyond 1bbf0c9's fifteen, after the RFC-W4-1 fields and
in `Config.load`'s read order: `fillDock`, `animations`, `advancedEffects`, `greetingName`,
`reducedMotion`, `dockTitle`, `dockRow`, `reorderButton`, `showStats`, `appLibrary`,
`skyIntensity`, `settingsKeyKeycodes`, `settingsKeyPanel`, and the theme-only festival keys
`festival`, `festivalUntil`, `greeting`, `accentColor`: seventeen keys, since `Config.java` at
69c93b6 reads 32 and 15 of them were modelled already. In the doc they are
nullable like every other key (`Boolean?`, `Int?`, `String?`, `List<Int>?`). In `EffectiveConfig`
they are non-null and defaulted to the v2.4.0 `config.xml` values, so every existing constructor call
compiles; the festival is effective as the launcher keeps it (`festival` the lower-case name or "",
`festivalUntil` the ISO date or "", `greeting` cleaned by `Cards.cleanLabel`, `accentColorArgb` 0 for
the diya gold), read from the doc alone as the launcher reads it from config.json alone. Why: the
codec answers "what will the TV show for these bytes"; left in `unknown`, decode would warn "the
launcher ignores it" for keys the bundled launcher reads, and `effective()` could not preview them.
The codec reads them with `Config.java`'s per-key rules (an all-or-nothing int list for
`settings_key_keycodes`, 0 to 100 for `sky_intensity`, `Festival.parse` ported in
`LauncherFestival`), warns without rejecting when `settings_key_keycodes` takes Back, an arrow, OK,
Enter or Menu (4, 19 to 23, 66, 82; SECURITY V1), and encodes them in `KEY_ORDER`; the fifteen old
keys keep their relative order, so every config.json the app wrote before is byte for byte the
same. `ConfigDifferentialTest` now runs the real v2.4.0 `Config.java` and `Festival.java` (copied
from the submodule) as the primary target, 10,000 generated files plus the themes and hand cases,
and keeps the vendored 1bbf0c9 copy as launcher 2.0 (3,000 files, the fifteen old keys), with
stubs for `SharedPreferences`, `Resources.getIntArray` and `Cards.cleanLabel`. Not modelled, since
the phone cannot read them: the choices made in Launcher settings on the TV (4 to 7 tiles per row,
motion), which win until the next push changes config.json (proved in
`aChoiceMadeOnTheTvLastsUntilTheNextPush`), the TV-side reorder and apps taken off Home there.
Affected: W4 (codec, tests), W8 (Look writes `dockTitle` false for a three-row dock, owner decision
D1; nothing else of the new keys is written by the app), W1 (DECISIONS-W1 lists the 5.7 edit).
Smallest alternative: keep the keys in `unknown` and change only the warning text; rejected
because the preview and the drift check would then not know what the TV shows.

## RFC-LAUNCHER-1: what launcher v2.4.0 needs from the planner (applied on launcher240; APPROVE WITH FOLLOW-UPS, reviews/RF-LAUNCHER-240.md)

What, all in `:core:planner` 5.8 and defaulted so every caller compiles: (1) `Choices` gains
`wizardWifi: WizardWifi = WizardWifi.UNKNOWN`, with `enum class WizardWifi { NOT_OWNER, OWNER,
UNKNOWN }` (owner decision D3). The home screen switch switches the setup wizard off only when it
is `NOT_OWNER`: a network saved during the first-run wizard belongs to it, and Android deletes it
the moment the wizard is switched off, taking the TV and this connection off Wi-Fi. The answer comes
from a new read-only probe call outside the frozen `Prober`, `SavedWifiOwners` (`dumpsys wifi | grep
-o 'cname=[^ ]*'` on the TV, so only owners leave it, never a network name; v2.4.0's
tools/disable.sh check), which `SetupDeps.savedWifi` (defaulted to the prober, like `launchable`)
asks once per snapshot when a setup wizard is on the TV. No answer is UNKNOWN, so the wizard stays
on. When the switch then fails because the wizard holds Home, the HOME_SETUPWRAITH card says why in
the owner's words and its primary tries again instead of "Try with the wizard off". (2) `PlanRequest`
gains `UpdateLauncher(launcher: LauncherArtifact)` and `PlanKind` gains `UPDATE_LAUNCHER` (owner
decision D4): the bundled launcher installed over our older build in place (same package and signer,
newer versionCode), `pm install -r` or the streaming install, then, when it is the TV's home screen,
`am start -W` to bring it up; refused for another signer (Setup's replace, D36), an unknown signer
or a missing launcher, no steps when the TV already has this version or a newer one (never `-d`).
The update keeps the launcher's files, so the dock, the look and the card answers stay and nothing is
pushed again. Manage's Home screen offers "Update the home screen" with a preview of the plan.
Related changes that need no interface edit: the probe reads the installed launcher's and the TV
curtain's signer from a copy of the installed APK (`ApkSigners`, the APK Signing Block, v3 then v2;
RF-CURTAIN-1 F9), so `pinInstalled` and the replace-signer flow work; the takeover's first
`StartActivity` is STOP_AND_ROLLBACK and `am start -W` must print "Status: ok" and no Error line
(`ShellRenderer.check`), so a launcher that does not start never gets the stock home screen
disabled; a look push (or the undo's restart of Home) puts the launcher's accessibility services
back when the TV had one on (typed `SettingsPut` steps after `RestartHome`, as tools/theme.sh does).
Affected: W3 (planner, renderer check), W4 (probe), W8 (Setup and Manage flows, strings), W1
(fixtures `Harness` default the wizard's Wi-Fi to NOT_OWNER where the tests modelled that TV).
Smallest alternatives: for D3, put the wizard into `Choices.kept`, which would show it as a kept row
on S3 and say nothing true; for D4, ask the person to run Setup again, which reinstalls everything
and redoes the takeover for a version bump.

Review (RF-LAUNCHER-240, APPROVE WITH FOLLOW-UPS, both RFCs signed off): the nine must-fix items are
applied on launcher240. F1: S3 shows "Replaces the home screen app already on this TV. Its look, app
order, settings and card answers start fresh." above "Make it clean" when `summary.replacesLauncher`;
the credential gate is the confirmation (D36). F2: `ApkSigners` needs a v2 block, and a v3 block
when present must name the same first certificate, else the signer is unknown. F3: the takeover's
first start is CONTINUE again, and the engine opens the section only when that start step is done
(else the run ends with VERIFY_FAILED, as for a missing launcher); `ScriptedTv` fails `am start -W`
for a package it does not have. F4: the D4 flow test runs (it sat inside a helper's lambda). F5: a
`cmp` mismatch stops the launcher build; the double build's output is in
`reviews/LAUNCHER-240-build-output.txt`. F6 with F14: the probe line also prints WifiConfigManager's
list markers and one "ID: n SSID" per saved network (checked on the API 28 TV emulator: "Configured
networks Begin/End", "ID: 0 SSID", "cname=android.uid.system:1000"), so `SavedWifi.verdict` tells
apart the wizard owning a network (kept on, the owner's card), others owning them or nothing saved
(NOT_OWNER: the wizard may go off as before) and a TV that could not tell (kept on, its own card).
F7: the accessibility restore only when the probed `accessibility_enabled` is "1". F8: suites in
verify mode and S06, S09 on API 28. F9: PRODUCT-SPEC 5.8, D36 and D3/D4 rows; SECURITY 3.1 and 3.3.
Also done: F11 (a per-copy name, the size checked before the copy, no copy when the installed APK's
sha256 is a pin's), F12 (the writers check knows `mkdir` and `cp`), F16 (the TV-side note only for
launcher 2.4 and later). Follow-ups, open:
- F2 defence in depth: when the plan does not install the launcher, `openSection` could require the
  installed sha256 to be a known pin.
- F10: drop setting change records whose before equals now from `ChangeSummary` (the accessibility
  restore writes the same list back).
- F13: a failed "Starting the updated home screen" could offer "Use the TV's own home screen"; the
  "nothing to update" snackbar should name the TV's own newer version.
- F15: python3 in the forge unit test image, so `upstreamRestoreShReadsIt` runs recovery.py there too.
- F17: if a push is ever skipped when its digest matches (RF-DOCK-1 F4), write `theme_revision`.
- F18: consider NUMPAD_ENTER (160) and BUTTON_SELECT (109) in `NAVIGATION_KEYCODES`.
- F19: RF-CURTAIN-1 F8 stays open (W8).
- No API 34 Android TV emulator image on forge (only android-34 google_apis), so S06 and S09 ran on
  API 28 only.

## RFC-W4-3: the keys of launcher v2.5.0 in the config codec (applied on launcher250; reviewed and approved as written, reviews/RF-LAUNCHER-250.md)

What: `LauncherConfigDoc` and `EffectiveConfig` (frozen, PRODUCT-SPEC 5.7) gain one field per key
that upstream v2.5.0's `Config.java` (9071ec8) reads beyond v2.4.0's 32, after the RFC-W4-2 fields
and in `Config.load` read order: `launchActions`, `appLabels`, `appBanners`, `inputKeyKeycodes`,
`appKeys`, `cinematicHome`, `wallpaperRotationMinutes`, `wallpaperDim`, `raceCalendar`; and 5.7
gains two small types for the last one, `LauncherRace(round: Int, name: String, place: String,
sessions: List<LauncherRaceSession>)` and `LauncherRaceSession(name: String, start: String)` (an ISO
instant as `java.time.Instant.toString` prints it). In the doc they are nullable like every other
key: the three package maps and `app_keys` as the trimmed, non-blank strings `Config.stringList`
keeps (`List<String>?`), `inputKeyKeycodes: List<Int>?`, `cinematicHome: Boolean?`, the two
wallpaper numbers `Int?`, `raceCalendar: List<LauncherRace>?` holding only the races the launcher
keeps, as `RaceWeekend` keeps them (session names trimmed and upper-cased, sessions sorted by start).
In `EffectiveConfig` they are non-null and default to v2.5.0's `config.xml` (all empty, false or 0):
the three maps as `Config.packageMap` builds them (`Map<String, String>`, a later entry for a
package wins), `appKeys` as `Config` keeps the list, and `wallpaperRotationMinutes`,
`wallpaperDim` and `raceCalendar` read from the doc alone, as the launcher reads them from
config.json alone (config.xml has none). No key of v2.4.0 changed type, range or default, and none
was removed; `KEY_ORDER` puts the new keys at their read positions, so every config.json the app
wrote before is byte for byte the same (the app writes none of the new keys).
Why: the same as RFC-W4-2. Left in `unknown`, decode would warn "the launcher ignores it" for keys
the bundled launcher reads, and `effective()` could not say what the TV shows.
How the codec reads them: `stringList`, `packageMap` and `intArray` rules per key, 0 to 1440 and 0 to
90 for the two wallpaper numbers, `RemoteKeys.parseAppKeys` for the warnings on `app_keys`, and
`optString` and `optInt` as AOSP's org.json defines them for `race_calendar` (a number, null, a list
or an object as a name gives its Java or compact JSON text, as on the TV). Warnings, never a
rejection: an entry the launcher skips, a package half that is not a package name, an `app_banners`
name that is not a bundled `banner_*` drawable (only `banner_tv` at v2.5.0), a race or session the
launcher leaves out, and navigation buttons (Back, the arrows, OK, Enter, Menu) in
`input_key_keycodes` or `app_keys`. The last is SECURITY V1 again and wider than in 2.4: those keys
feed `InputKeyService` and `HomeKeyService`, accessibility services that filter keys in every app,
so with one of them on, a rogue overlay listing OK would take the button away everywhere, not only
on Home. The app never switches those services on and never writes these keys.
Tests: `ConfigDifferentialTest` runs v2.5.0's `Config.java` (and `Festival.java`) from the
submodule as the primary target; `WallpaperRotation.java` and `RaceWeekend.java`, which it now
calls, are vendored in the test sources and a test holds them equal to the submodule's files
(only the package line differs), so `core/spec/build.gradle.kts` keeps its copy task unchanged.
The generator, the hand cases and the encoder round trip cover the nine keys, and
`launcher25ChoicesOnTheTvLastUntilTheNextPush` covers launcher 2.5's two new choices on the TV
(rows below the dock 0 to 3, a wallpaper pick), which, like the columns, win until a push changes
config.json. Launcher 2.0 (1bbf0c9) stays the second target for the fifteen old keys.
Not modelled, since the phone cannot read them: the choices on the TV, the dock and tile order set
on the TV (Edit dock, #340, and Reorder, both `files/order.txt`, kept while `dock_apps` stays the
same list), apps taken off Home on the TV, and a theme's `wallpapers/` collection, which the app
never pushes. A new look and "Restore mine" push the saved `dock_apps` unchanged (RF-LAUNCHER-250
F4), so a dock edited on the TV survives a look change. The one cut: a dock of 13 to 18 apps saved
under Six columns (the dock and two rows of six) is cut to its first 12 by a four-tile look (the
dock and two rows of four), so `dock_apps` changes and the TV's own dock edit for the longer list
no longer applies; the Dock row then shows the shorter list.
Affected: W4 (codec, tests), W8 (nothing: the phone writes none of the keys; the Home screen note
says what happens to a dock changed on the TV), W1 (DECISIONS-W1 lists the 5.7 edit).
Smallest alternative: keep the nine keys in `unknown` and change only the warning text; rejected
for the reason RFC-W4-2 gave, and because `app_keys` and `input_key_keycodes` need the V1 warning.

Review (RF-LAUNCHER-250, changes required before merge, RFC-W4-3 signed off as written): the seven
must-do items are done on release020, after launcher250 and profiles250 were merged. F1: a switch-off
the live check refuses because the package asks for TV_INPUT_HARDWARE (or registers a TV input) is
kept and skipped whatever the step's policy, never a pause, so the run ends COMPLETED_WITH_SKIPS; the
journal's Failed text is `DefaultEngine.KEPT_TV_INPUT`, and Done (`SetupExtras.doneKept`) and Manage
(`SnackKind.KEPT_TV_INPUT`, Leftovers counts only what went off) say "{name}. Kept: it runs the TV's
HDMI and tuner inputs." instead of the generic harmless line. The tuner input drivers the lists name
are never-disable (sony-bravia-ae2: `com.mediatek.dtv.tvinput.atsctuner`,
`com.sony.dtv.tvinput.atsc3tuner`; prismplus-q65-ultra-r10g: `com.realtek.dtmb`, `com.realtek.tv.atsc`,
`com.realtek.tv.isdb`); plan-time marking (D4) waits until after 0.2.0. Tests:
`EngineTest.theLiveCheckRefusesToSwitchOffATvInputDriver`, `SetupMachineTest.aSwitchOffKeptForTheTvInputsEndsOnDoneWithItsOwnLine`,
`ManageMachinesTest.aSwitchOffKeptForTheTvInputsSaysSoAndLeavesNoOpenRun`, `ProfilesTest.knownTunerInputDriversAreNeverDisable`.
F2: the rule has no takeover exemption; a stock launcher or wizard that asks for the permission stays
enabled, its switch-off is skipped and the section goes on (`SectionTest.aStockLauncherThatRunsTheInputsStaysOnAndTheSwitchGoesOn`).
F3: "Stop here" on a run stopped inside the accessibility restore (from the clear of the list to
`accessibility_enabled 1`) first runs its remaining steps, and waits for the TV when it is away
(`EngineTest.stopHereFinishesAnAccessibilityRestoreFirst`). F4: SetTheme and RestoreMine push the
saved dock unchanged (`ManageMachinesTest.aNewLookPushesTheSavedDockUnchanged`, 18 apps;
`DockLayersTest.aDockOfMoreThanTwelveAppsIsCutByAFourTileLook`, 13 and 18). F5: the CHANGELOG no
longer claims a warning nobody sees, and its TV input bullet says what F1 and F2 do. F6: PRODUCT-SPEC
5.7 (the RFC-W4-3 paragraph, "built from v2.5.0"), SECURITY V1 (the key services, ThemeProvider) and
3.3 (the `0` step and F3), and LAUNCHER-250-PLAN sections 2 and 5. F7: the full check on the merged
tree in verify mode (111 suites, 1792 tests, 0 failures, 1 skipped; the v2.5.0 differential tally
"7313 applied (6287 with warnings), 2646 ignored, 41 would crash"), and S06 on API 28
(`:e2e:emulatorTestTheme`, new, runs it alone). S06 was reported PENDING on this tree, since
`E2eWiring` still returns null for every piece, so it now builds the real pieces itself as S09
does. It pushes Aurora, checks the overlay bytes, the launcher's restart and the service list and
`accessibility_enabled 1` after the new restore order, then pushes again, stops the run inside the
restore (paused at step 7 of the restore's 6 to 10: list empty, accessibility 0) and gives it up
with "Stop here", which put the list and `1` back. It passed in 9.6 s; of two earlier runs, one lost
the emulator's adb connection during the second push (the API 28 image's graphics stack, as S09
notes) and one could not reconnect for a second test method, hence the one method. Follow-ups,
open:
- F8: "Update the home screen" (`pm install -r`) replaces the running launcher without the
  accessibility restore; consider the same restore after an in-place update when the probed list
  names ours and accessibility is on.
- F9: one TV-side line for the clear, off, write back and on (a typed composite op, an RFC for 5.2),
  so a lost phone cannot split the restore at all.
- F10: no codec warning reaches a screen; when drift is found, decode the TV's overlay from the fresh
  snapshot and say in the banner when it takes a remote button.
- F11: a `KEYCODE_` name Android does not know resolves to 0 upstream and is skipped, while the codec
  keeps it silently; warn, or carry `KeyEvent`'s name table. KEYCODE_HOME (3) in `app_keys` is not in
  the navigation set; consider it with RF-LAUNCHER-240 F18's 109 and 160.
- F12: the Home screen note's "last until you change the home screen here": a card added in Cards and
  modules and a Setup re-run also push `config.json`; worth a word when the note is next touched.

## RF-PROFILES-63: the 60 profiles from upstream v2.4.0 (APPROVE WITH FOLLOW-UPS, reviews/RF-PROFILES-63.md)

Done on profiles63: P1 below Android 8 (SDK 26, `DefaultPlanner.TV_MIN_SDK`, the launcher's and the
curtain's minSdk) Setup is the clean-up alone: `SetupMachine.homeBlock` is OLD_ANDROID, so no install,
look, curtain or switch is asked for, S3 says "The new home screen needs Android 8 or newer...", and
the planner refuses an install or a switch asked for anyway and never weaves the curtain in. P2 an
exact profile with no known stock launcher (xiaomi-mitv-astp1, stellamore-t950s) is UNKNOWN_HOME, the
clean-up only, with its own S3 line. `SetupExtras.homeBlock` is new (a presentation field, RFC-W8-2).
P3 a numbered note ends a header keep paragraph. P4 to P7 in profile-meta.json with curation notes.
P8 tcl-43c645 parked in unconverted.txt, tcl-40s5400a matches device C06 alone, and ProfilesTest pins
every tie the resolver settles by id. P9 the facts pass and the brand-inferred notes. Follow-ups, open:
- P10 (ruling 4): the curated meta should win over a listed setupwraith (panasonic-4ktv-jup,
  philips-55oled707, tcl-40s5400a: their headers say the wizard does not claim Home); better, the
  takeover switches the wizard off only when the snapshot shows it among the HOME claimants.
- P11, before release: switch the wizard off before the stock launcher; when a
  HOME_TAKEOVER_SETUPWRAITH profile must keep the wizard on, skip the switch up front with the
  HOME_SETUPWRAITH card instead of attempting it.
- P12: confirm the PH1M_EA_9970A model strings with the contributors (adb prints underscores).
- P13: kalley-r10g and prismplus-q65-ultra-r10g may report R10G as the model; add it to models once
  getprop is known; kalley-r10g `rebootTested` true.
- P14, owner call: `com.google.android.tvrecommendations` stays default-off for now. Switching it off
  loses the system notification panel, so a Google sign-in notice has nowhere to show. Ask row, or a
  Manage help line that puts it back for a minute.
- P15: a per-profile note on S3's home switch row when the remote's Input key (kalley-r10g,
  hisense-hismarttv-a4-android11) or app keys (tcl-65c735) stop working after the switch.
- P16: Sony b2b vendorprotocol and the stock home customisers as ask rows.
- P17: the Google TV app (`com.google.android.videos`) as an ask row on GOOGLE_TV profiles and out of
  the sure sets.
- P18: tcl-55s470g `rebootTested` true; tcl-c755 WIRELESS_DEBUG_PAIRING_REQUIRED; the Hitachi CDH
  `com.tcl.guard` (persistent) as an ask row or a note.
- P19: the header "Kept" lists into the curated never-disable lists (skyworth, panasonic, TCL remote
  services, `com.tcl.system.server`).
- P20: the same rules on the earlier 30 profiles (`fusion.android.tv.demo`, `com.tcl.partnercustomizer`,
  the tcl-qm6k keep-list read).
- P21: facts wording (Hisense words on the Vu, the HG9 SmartSense rows, `com.mediatek.testpq`).
- P22: curation notes for the departures listed in the review (55PUS7503's credit is done).
- P23: define each quirk's meaning when its first consumer lands.
- P24: the S3 row for the wizard on xiaomi-mitv-afmu0 could say it re-pairs the remote.
- P25: the scanner turns commented-out package lines with a note into group titles (Fire TV Stick);
  correct but fragile.
- Ruling 1: a resolver policy instead of the id order (score, then the more specific match, then an
  explicit `supersedes`, then a test failure); tcl-g10-google-tv marked superseded, not removed.

## RF-PROFILES-250: the profiles from upstream v2.5.0 (branch profiles250)

Done on profiles250: the submodule and the profile build at v2.5.0 (9071ec8); 42 new profiles (131
in all, 46 brands) and 16 upstream folders refused or parked in unconverted.txt with reasons;
corrections to the shipped lists from v2.5.0 (onn 4K ambient mode, the 65PUS8102 FrameworkLogger,
the G07 and MSSP3 notes, the Chromecast casting note); the MediaTek and Realtek home customizers
ask rows on haier-h32s80efx, jmgo-n1s-holan and sanyo-atv-r2, the Xiaomi smart panel on
xiaomi-mitv-astp1. Ruling 1 in part: of two profiles with the same score the longer fingerprint
prefix wins (`BundledProfileSource.specificity`), then the id; ProfilesTest pins every resolution
and fails on any pair still level other than tcl-g10-google-tv. Open, for the owner or later work:
- P11 is live on more TVs: on Google TV sets whose setup wizard owns the saved Wi-Fi (tcl-43s5k,
  tcl-g03-google-tv, xgimi-mogo-3-pro, eko-2k-google-tv-tennoji, byintek-t5-max and others), D3
  keeps the wizard on while launcherx is switched off, and v2.5.0 documents the wizard then claiming
  Home and system_server crash-looping (upstream #506). Skip the switch up front in that case.
- Casting: on Google's own hardware the Cast receiver reads its credentials from the setup wizard
  (#361, #422), so the switch costs casting after every reboot. google-tv-streamer-4k-kirkwood is
  the clean-up only for that reason; google-chromecast-gtv-4k still offers the switch (owner call).
- MOKA Google TV sets: kalley-43g200's firmware brings the wizard back at every boot and the set
  boot-looped with launcherx off; it is the clean-up only. kalley-r10g and
  prismplus-q65-ultra-r10g are the same family with the switch; an unknown MOKA set takes the
  generic path. A planner guard for this family is worth considering.
- `com.tcl.channelplus` (TCL Live, a TV input) is default-off on tcl-g05-google-tv,
  tcl-g10-google-tv and tcl-qm6k-google-tv and so in the TCL sure set; the live TV input check
  protects it at run time, but the rows should be ask rows (P20).
- Ruling 1, the rest: an explicit `supersedes` for true duplicates; the parked AR14 C06 lists
  (tcl-32s5400a, tcl-l40s66e) and BeyondTV2 AR05 (tcl-beyondtv2-android-11) wait on it or on the
  owner preferring them.
- Parked for getprop from the contributors: sony-kd-49x8307c (fingerprint), tcl-32l5a, byintek-u4,
  vision-2024vision43stv.
- P15 candidates from v2.5.0 headers: remote app keys that only launcherx answered (onn 4K Plus,
  tcl-43s5k gear, tcl-55c725, tcl-g03-google-tv, motorola-swtv-24ae-fhd); XGIMI MoGo 3 Pro's
  xrmservice switches the stock home back on at its remote keys unless the launcher's app keys and
  stock home redirect are set.
- P19 on the v2.5.0 profiles: some header keep names are still not in never-disable (Fire TV
  casting and Silk names given in short form, the VIDAA and Xiaomi casting and NAS stacks, Gboard on
  oneplus, verizon, sharp and jvc, Assistant on the Streamer and MoGo 2); no plan changes today.
- mtc-uk-2k-android-tv: dumpsys is disabled on that firmware, and the app reads the TV with
  dumpsys; expect a read failure there until the probe has a fallback.
- tcl-55c725 is parked: its product prefix alone would take tcl-65c725's tvlauncher sets.
Taken up for 0.2.0 in RF-SAFETY-250 below: the P11 Wi-Fi item, casting, the MOKA family and
`com.tcl.channelplus`.

## RF-SAFETY-250: home screen switch safety for 0.2.0 (owner decisions, applied on release020)

The owner's calls on the RF-PROFILES-250 open items, each with tests. No frozen interface changes:
`HomeSwitchRisk` (in `:core:planner`, beside the planner) decides, `SetupMachine.homeBlock` uses it
before planning, so Setup asks for no launcher install, look, curtain or switch, and S3 shows the
reason in place of the home screen row, in the voice of the P1 and P2 lines; `DefaultPlanner.takeover`
refuses a switch asked for anyway with the same reason (`HomeSwitchRisk.Risk.reason`). New
`HomeBlock` values (a presentation field, RFC-W8-2): `WIZARD_WIFI`, `WIZARD_WIFI_UNKNOWN`, `CASTING`,
`BOOT_LOOP`.
- (a) The setup wizard and the saved Wi-Fi (upstream #506, #422). When a profile's switch would
  switch the wizard off (its `setupWraith` with HOME_TAKEOVER_SETUPWRAITH) and D3 keeps it on, because
  the TV says the wizard saved the Wi-Fi or cannot tell, the wizard would stay on with the stock
  home off, its RecoveryActivity would take Home and system_server can crash-loop. The switch is
  skipped up front. On the generic path the switch never touches the wizard; there only a TV that
  says outright, at probe time, that its wizard saved the Wi-Fi is held back (a failed read keeps
  0.1.0's behaviour). A wizard already off on the TV holds nothing back. S3 says how to get the new
  home screen later (forget and re-add the network, or a cable, then Setup again). Tests:
  `HomeSwitchRiskTest` and `SetupMachineTest.theSwitchIsSkippedUpFrontWhereItIsUnsafe` on the
  shipped tcl-43s5k, tcl-g03-google-tv, xgimi-mogo-3-pro, eko-2k-google-tv-tennoji, byintek-t5-max,
  aiwa-ai-pont-sa and polytron-2k-hayward and the generic case; `Launcher24PlanTest` and
  `SetupMachineTest.theSetupWizardStaysOnWhileItMayOwnTheWifi` updated (the Wi-Fi stop card is no
  longer reached from a profile's switch).
- (b) Casting on Google's own hardware (#361, #422): google-chromecast-gtv-4k is the clean-up only,
  like google-tv-streamer-4k-kirkwood; S3 now gives both the casting reason. By profile id, and on an
  unprofiled set by maker Google and device sabrina, boreal or kirkwood (an emulator image also says
  Google, so never the maker alone).
- (c) The MOKA family boot loop: kalley-43g200's firmware turns the wizard back on at every boot and
  a set boot-looped with launcherx off (a USB reflash was needed). kalley-r10g and
  prismplus-q65-ultra-r10g are the clean-up only, and so is any TV matching the exact device and maker
  pairs the three profiles match on (R3_GTV by MOKA, R10G by MOKA, R10G under Kalley) whatever its
  Android version or exact profile; `HomeSwitchRiskTest` holds the pairs equal to the profiles' match
  keys. The maker alone is never enough: MOKA also builds the Philips R8G (whitefield), which keeps
  its switch.
- (d) `com.tcl.channelplus` (TCL Live, a TV input) is an ask row on tcl-g05-google-tv,
  tcl-g10-google-tv and tcl-qm6k-google-tv too, so it is out of the TCL sure set
  (`ProfilesTest.tclLiveIsAlwaysAnAskRow`); curation notes added.
- (a, closed for 0.2.0 by the owner) The generic path: without an exact profile the switch never
  switches the setup wizard off, so whenever a setup wizard is on the TV the plan would switch the
  stock home off while the wizard stays on, the same RecoveryActivity risk. Such a TV now gets the
  clean-up alone, whatever the Wi-Fi answer (`HomeSwitchRisk.Risk.WIZARD_STAYS_ON`, HomeBlock
  `WIZARD_STAYS_ON`, its own S3 line; the Wi-Fi line when the TV says the wizard saved the Wi-Fi).
  A TV without a wizard, or with it off, keeps the switch, and a re-run on a TV whose home is
  already ours is never held back. Tests: `HomeSwitchRiskTest.anUnknownTvWithItsSetupWizardOnGetsNoSwitch`,
  `SetupMachineTest.theSwitchIsSkippedUpFrontWhereItIsUnsafe`. The emulator scenarios S05 and S09
  (no profile matches the image) switch the image's wizard off before they plan, and on again after.
- Safety rule 8 rows: TalkBack (google-chromecast-gtv-4k, tcl-g05-google-tv, tcl-g10-google-tv,
  xiaomi-mi-box-s) and `com.google.android.tv.remote.service` (tcl-g10-google-tv) were already ask
  rows through their accessibility and casting categories; the curated rows now say `ask: true`.
  The scan of every shipped list and sure set found one more: `com.google.android.tv.remotecontrol.logging`
  (google-chromecast-gtv-4k, google-tv-streamer-4k-kirkwood), off by default as telemetry, now an
  ask row. `ProfilesTest.noAccessibilityRemoteOrPairingPackageIsDefaultOff` fails on any package of
  the accessibility category or named like TalkBack, accessibility, the remote, pairing or the
  remote's key services in a default-off group or a sure set (one named exception,
  `com.amazon.device_remote_config_sync_service`, settings from Amazon's servers). The committed
  protected-in-disable-groups report loses the two logger rows.
Open:
- The shared e2e wiring (`E2eWiring`) still returns null for every piece, so S01 to S05, S07 and S08
  report PENDING in a full emulator session; only S06 and S09 build their real pieces themselves.
  Wire it once (the real constructors, as AppGraph builds them) so the whole suite runs, and set
  `CYT_E2E_REQUIRE_ALL=1` in the nightly job.

## RF-STORES-030: the store flavours and the 0.3.0 release set (approve with must-fixes, follow-ups)

Review of the full and play flavours, the release set (APK and AAB), publish-release.sh and the
F-Droid recipe before the 0.3.0 tag. Applied on release030:
- M1: the 0.3.0 store notes (`fastlane/.../changelogs/3.txt`) and `full_description.txt` say only
  what is true on every channel on release day, and link the privacy policy at its github.io
  address, which works now and redirects to the custom domain once that is set.
  `scripts/tests/test_store_text.py` checks the store limits, the links and the wording.
- M2: the Play build offers no "updates go off" anywhere. `StoreRules.updatesOff` (false in PLAY)
  reaches ManageDeps; at the Custom level Switched off leaves updater rows (by name or by the
  "Software updates" group, `ProfileCategory.isUpdater(pkg, groups)`) out of the ask groups, and
  putting one back offers no Undo. `StoreRulesTest.thePlayBuildOffersNoUpdatesOffInSwitchedOff`,
  golden `play_switchedoff_custom`.
- M3: app lint writes a text report per variant (no shared `textOutput`), so the full and play
  report copies no longer race.
- S1: Play Setup skips an updater that an earlier full build switched off instead of listing it
  under "We keep these" (Manage's Switched off lists it with Put back).
  `KeepUpdatesOnTest.thePlayBuildDoesNotListAnUpdaterThatIsAlreadyOffAsKept`.
- S2: `scripts/fdroid-check.sh` builds the `full` flavour by name; run on the merged tree, its
  unsigned APK equals the full APK of a test-key release build of the same commit
  (FDROID-S2-build-evidence.md, section 6).
- S3: MERGE-REQUEST.md names the libraries' licences and the 0.3.0 run.
- L6, L14: CONTRIBUTING has the one-time Play App Signing export (PEPK), the first upload in the
  Console and the first production release without a staged rollout.
- L7: publish-release.sh requires the tag on GitHub to be the same tag object as the local one
  (`scripts/tests/test_publish_release.py`).
- L8: a `--test-key` release run keeps its evidence in `dist/release-X.Y.Z-TESTKEY/`.
- L12: PRIVACY.md and the site's policy say that opening Cards and modules in the GitHub and
  F-Droid versions fetches the add-on list from GitHub.
- L13: the store description no longer says software updates are never touched.

Open (low, none blocks 0.3.0):
- L1: the play dex still holds the four `DownloadHosts.ALL` strings and the AAB still carries the
  seed module index (`assets/bundled/index.json`, a github.com APK URL); nothing reads either in the
  play build and the effective host list is empty. Give the host set to the flavour (the full
  Store owns it, `:core:trust` takes it as a parameter), move `bundled/index.json*` and `keys.json*`
  to `app/src/full/assets` (or null the seed in play), then add "no github string in the play dex
  or assets" to `scripts/check-apk.py`.
- L2: an open ADD_MODULE run left by a full build would resume in the play build after an app
  switch mid run. On resume, end (not run) an ADD_MODULE plan when the build has no add-ons.
- L3: add-ons installed by a full build cannot be removed from the play build (Cards and modules is
  hidden). Show installed add-ons for removal only in play, or document "remove add-ons first".
- L4: on Android 14 and newer, installing the GitHub APK over a Play install can show Android's
  "update from this source?" prompt (update ownership). One line in the README's install notes.
- L5: `play-upload.py publish()` (the live API sequence) has no test. Add one with a fake
  `urlopen`: the five calls in order, delete on a failed track update, no delete after a commit.
- L9: `check-installed.sh` with `CYT_FLAVOUR=play` runs the APK scheme checks (v2, v3, v1 off) on
  a Play-signed base APK; Play's signing may not add v3. For play, check the signer certificate
  against `docs/release-signer.sha256` instead of the schemes.
- L10: CI names the unsigned bundle `$NAME-play-unsigned.aab`, release-on-forge `$NAME-unsigned.aab`;
  align them.
- L11: the permission for the name and package IDs is linked from this repository's README; a short
  line by the upstream author himself (also confirming the aurora wallpaper licence) would settle
  both for F-Droid's reviewers.
- L15: code and docs cite the store plan's sections and decisions (D2, D3), which are not in the
  repository; add a short DECISIONS entry (store flavours, keepUpdatesOn, one signing key and why)
  so those references resolve.
