# W8 decisions: flows, screens, Android glue

Stream W8, 2026-10-19. Built against section 5 as fixed by R0 and the W1 fakes. RFCs:
`docs/rfcs/W8.md`; R5 review: `reviews/R5-W8.md`.

## Shape

- D-W8-1 Machines live in `:core:flows`, pure Kotlin, one per screen family: `SetupMachine`
  (S1 to S5), `ManageMachine` (M1), `SwitchedOffMachine` (M4), `LeftoversMachine` (M5),
  `HomeScreenMachine` (M2), `ModulesMachine` (M3 family), `RestorePointMachine` (M6),
  `TvSettingsMachine` (M7), `AppSettingsMachine` (M8). All follow the frozen `Machine` contract.
- D-W8-2 Shared flow services in `:core:flows`: `GateKeeper` (D17, one prompt per TV for 10
  minutes, NONE pass on phones without a lock), `TvAccess` (returning writes: live session reuse,
  port probe, the M1a debugging step via `reach`, D19 subnet check, D37 expected id), `PlanRunner`
  (plan, gate, start, follow), `TvWriter` (the Manage-family write), `RunLedger` (which plans ran
  on which TV, `ChangeRecord`s derived from the journals, the resume counts), `SessionEnd` (D7:
  debug_off at Finish and after 10 minutes in the background, confirmed by a failed probe),
  `RestorePointWriter` (D44 after every plan), `DocumentTvRepository` (tv.json, schema 1),
  `SecureIdGen` (ULID and run token for the planner), `resolveStart` (ARCHITECTURE 2.2).
- D-W8-3 Copy: every machine-produced sentence goes through `FlowCopy` and `CopyKey`; the app maps
  each key to the resource in the module that shows it (`ResourceFlowCopy`, an exhaustive `when`).
  Strings are ICU MessageFormat with named arguments, formatted with `android.icu.text.MessageFormat`
  (`msg()` in every feature module); no `%s` and no Android plurals.
- D-W8-4 Screens are stateless composables over a machine's state; `:app` hosts each machine in a
  `MachineHost` ViewModel (it only owns the scope) behind a Navigation 3 entry. Keys carry ids only.
- D-W8-5 Pre-W7 components: each feature module has `PendingDesign.kt`, thin stand-ins named
  `Pending<Component>` with W7's parameter lists (ui/design/API.md). Merge swap: delete the file,
  drop the prefix, import `tv.cleanyour.ui.design.component.*`, and `PendingAppTheme` becomes
  `CytTheme`. `Modifier.rejectObscuredTouches()` (SECURITY 4.3) has no W7 equivalent and should
  move to `:ui:design`.
- D-W8-6 Other streams' implementations enter through `Seams` (app). `Seams.unwired()` are inert
  stand-ins until W2 to W6 merge; the checklist is in `Seams.kt`.

## Security and Android

- D-W8-7 Manifest: one exported component (MainActivity, `taskAffinity=""`, singleTask, reads no
  extras); permissions exactly D16; androidx.core's DYNAMIC_RECEIVER permission and profileinstaller's
  exported receiver are removed with `tools:node="remove"`; `allowBackup=false`,
  `dataExtractionRules` and `fullBackupContent` exclude every domain; cleartext off; network
  security config with the D41 hosts and system anchors only (matches W5's `DownloadHosts.ALL`).
  `ManifestTest` checks the merged manifest under Robolectric.
- D-W8-8 D35: `FLAG_SECURE` plus `accessibilityDataSensitive` (API 34) only on the Allow phase of
  S2c, the "Reset my key" confirmation and while the restore zip is written. Confirming buttons
  drop touches that arrive through an overlay (`FLAG_WINDOW_IS_OBSCURED` and, API 29+,
  `FLAG_WINDOW_IS_PARTIALLY_OBSCURED`).
- D-W8-9 `AndroidCredentialGate` uses the platform `BiometricPrompt` (no androidx.biometric): API
  30+ strong biometrics or device credential, API 29 credential fallback, API 28 the keyguard
  confirm screen.
- D-W8-10 `OperationService` (connectedDevice FGS) holds nothing: it mirrors the engine's run as a
  notice published by `CytApplication`; a plan with a transfer, an install, a guarded section or
  more than 5 steps starts it; the end of the run stops it.
- D-W8-11 `NsdDiscovery`: five service types, resolves one at a time, a 5555 probe of IPv4 literals
  only (never a name lookup), no multicast lock; network state by `ConnectivityManager`, never the
  SSID.
- D-W8-12 Logging: `CytLog` redacts before logcat and before the diagnostics ring (blobs, MD5
  fingerprints, IPv4 but the last octet, serials, 16 hex ids, 6 digit codes, Wi-Fi names); release
  builds strip debug logs with R8.

## Tests

- Coroutine tests run with `runTest(timeout = 30.seconds)`; the 5 minute Gradle test timeout comes
  from build-logic (T1), no module sets its own. Machines run endless poll and tick loops in `backgroundScope`, so tests
  never wait on Turbine (`drain()` reads what is there) and any retry loop must contain a delay.

## Merge of main (M5)

- D-W8-13 RFC-W8-1 as ruled: `SetupIntent.StopAction(action)` and `SetupIntent.RejectIdentity` are
  in the frozen sealed set (`StopAction` moved to Flows.kt beside it); `SetupActions` keeps only
  `intent`, `back(): Boolean` and `dismissCard`.
- D-W8-14 RFC-W8-4: `GateKeeper` never caches an `APP_GATE_ID` pass, prompted or handed in.
- D-W8-15 RFC-W8-5: the D24 restart test plans `PlanRequest.Reboot` and runs it through
  `PlanRunner` (journaled, no prompt: `GateKeeper.needsPass` mirrors the engine's REBOOT rule). The
  3 minute note is a timer beside the run; a run that pauses (the boot wait of 5 minutes ran out)
  is abandoned, since the reboot needs no undo and an open run would refuse every later write, and
  the not-back card shows. "It's on again" is only a boot wait.
- D-W8-16 W3 notes: `Finished(ABANDONED, home = CONNECTION_LOST)` of a plan with no steps (step R
  rebuilt from the journal, D-W3-2) and `Finished(home = CONNECTION_LOST)` show the new growable
  case `HOME_PUT_BACK` ("We put the old home screen back", Try the switch again, Leave it as is).
  R-11: "Stop here" or "Leave it as is" with a HOME section open while the TV is away keeps the
  stop card with "Reconnect to the TV to put the old home screen back", waits for the TV through
  `TvAccess` (the debugging step shows), resumes the run if the engine has not loaded it, then
  abandons. R-12: the only typed name (M7 rename) is trimmed; no 0.1 plan writes `device_name`.
- D-W8-17 R5-W8 O2: `TvWriter` publishes a paused or already open run to `OpenWrites` (one per
  graph, in `ManageDeps`) instead of a failed snackbar, and does not plan or prompt while one is
  open; a RUN_OPEN refusal after process death loads the run with `engine.resume` first. The app
  lays `OpenWriteSheet` over any screen: Carry on, Undo what we did, Stop here
  (`TvWriter.resolveOpen`: reconnect, the D17 pass when the plan is gated, then follow the run; an
  action the engine does not take within 5 s shows the reconnect line). A SETUP run opens the
  wizard's resume card instead. The S4e card for a Manage kind says "We were in the middle of a
  change on {tv}", and such a run ends in Manage, never on Done.

## Emulator run fixes (emufix)

- D-W8-18 Bug 2: a door whose address changes (the placeholder door to a typed address, a found TV
  adopted, a second address typed while waiting) is a new door: its port poll and connect stop
  and the new address's start. Before, only a change of state class or door phase restarted them,
  so an address typed on "No TV found yet" was never probed.
- D-W8-19 Finding 1: discovery drops every IPv4 address of the phone's own interfaces (and
  loopback). Welcome always offers "Enter the TV's address" (the S1b and UX 4.1 copy) under Start.
  "No" on the identity line keeps that host out of the run's list and opens the address sheet; a
  typed address is always tried, even one answered No before.
- D-W8-20 Finding 3: the dock sheet offers only apps with a LEANBACK_LAUNCHER or LAUNCHER activity
  (`LaunchableApps`, one read-only probe call, not in the frozen `Prober` or `Snapshot`), never a
  HOME claimant, stock or our launcher, the setup wizard, a switched-off package or a provider.
  When the TV cannot say (and in Manage, which has only the stored snapshot), user-installed and
  known streaming apps stand in. An empty dock reads "Your new home screen" (UX 2 dock card title)
  on S3 and "Dock" in Customise.
- D-W8-21 Diagnostics: `SetupDeps.log` (`FlowLog`, CytLog.i in the app) gets one line at connect,
  connected or failed, probe start and end, plan ready or refused, identity rejected, and each run
  state change: outcomes, counts and type names only, never an address, serial, id or package.
- D-W8-22 "I've set up a TV before" pushes Manage over Welcome, so Back returns to Welcome; every
  other route to Manage still replaces the stack (`applyRoute`, unit tested).

## Emulator run fixes, round 3 (emufix2)

- D-W8-23 S3 agrees with the plan. On the emulator TV (unknown model, Simple) S3 said nothing was
  found while the plan had 9 steps with 1 switch-off: the streamed launcher install, then the eight
  steps of the home screen switch, whose `Disable` is the TV's own home screen
  (`com.google.android.tvlauncher`). Only "Show commands" named it. The planner now lists every
  `Disable` of the switch (the stock home screen, the wizard on the quirk) in the group "Needed for
  the new home screen" (UX 2 preview sheet copy), as rows that are off and protected: the list shows
  them Off with nothing to flip, and a row for the same package elsewhere (a profile's `home`
  group, a debloat group naming it) moves there instead of reading "We keep these". A package that
  is already off is no row at all, so a re-run on a tidied TV counts nothing it does not do.
  `PlanSummary.switchOffCount` and `switchOffPackages` (core:planner, not frozen) are what S3 and
  the list count; `WhatWeFoundMatchesThePlanTest` holds the count, the Off rows, every group's
  numbers and "Show commands" against the plan for the reference Mi TV (before and after its hand
  debloat), an unknown model, an unknown Xiaomi with the brand sure set, and the emulator capture
  (`fixtures/conformance/emu-atv28-2026-10-20`, read-only, redacted). Not changed: the D44 restore
  point push to /sdcard/cleanyourtv after the run and the D7 debugging switch-off on Finish stay
  outside the Setup plan; S3's safety line and S5's checklist are where the person is told.
- D-W8-24 App names without labels. The probe reads no labels and no read-only line on Android 9
  prints them, so no probe line was added. `AppNames.of` (core:spec): the facts table, then the
  TV's label, then a name read from the package (`com.lego.android.tvleanback` reads "Lego"). The
  planner rows, the dock sheet (wizard and Manage) and every Manage name use it.
- D-W8-25 An empty dock shows no "In the dock" heading (both dock sheets); Manage's sheet also drops
  "Other apps on the TV" when there is none.
- D-W8-26 Welcome is mockup 01 again: "Start" and "I've set up a TV before" only. The address
  button D-W8-19 added is gone; address entry stays on S1b ("Enter the TV's address"), in the "No TV
  found yet" card, behind "No" on the identity line, and, new, as a quiet "Not this TV? Enter the
  TV's address" on the door (steps, connecting, Allow) of a TV that Start reached through discovery
  (not for a typed address). The live discovery line is pinned with Start (14 dp above it, like
  S3's safety line, R4-W8 H1), and when the phone leaves less than 680 dp between its bars the hero
  narrows to 248 dp and the gaps tighten, so the title, the body and "Found ..." or "No TV yet" all
  show before the first tap on the 360 x 720 dp emulator. At full height the gaps and the 300 dp hero
  are the mockup's (28, 32, 40). Goldens re-recorded: Welcome and the doors; new: the unknown
  model's home screen switch on S3 and in the list, the empty dock sheets.
- D-W8-27 0.1 is always dark (main-loop decision): `CytTheme(darkTheme = true)`, light bar icons on
  transparent bars (`SystemBarStyle.dark`), and the activity theme is black from the first frame:
  window, status and navigation bars, no light bar icons, no force dark (API 29+), a black splash
  (API 31+). The light tokens stay in `:ui:design`, unused by the app. `ManifestTest` checks the
  theme in light mode. D-W7-1's "follow the system" is withdrawn for 0.1.
- D-W8-28 Fidelity fixes from the design vs emulator comparison: a tall modal sheet (Customise, the
  list) stops 48 dp below the status bar so the scrim and the top bar show as in mockup 08, instead
  of running under the status bar icons; a typed TV's chip reads the model from its identity line,
  not its address. Left as is and reported: the emulator's own tall status bar, Welcome's line
  pinned above Start (D-W8-26) where mockup 01 has it under the body, the D8 copy, and the identity
  line standing in for mockup 03's Allow dialog on a TV without one (D38).
- D-W8-29 The dock sheet has rows (owner, first device test: "add apps in different layers", "drag
  to change order"). Model: one ordered list cut into rows of the look's columns (DockLayers in
  core:spec), the dock first, then Row 2 and Row 3, three rows at most. It is what the launcher
  does (AppAdapter sorts the grid by dock_apps, GridLayoutManager cuts it, rows_below_dock rows stay
  in view), so the sheet cannot show a layout the TV would not: a row holds apps only once the rows
  above it are full, and taking an app out pulls the next one up. "Add a row under the dock" shows
  only when the rows on screen are full. config.json gets dock_apps as the list and rows_below_dock
  as the rows under the dock that hold an app (0 to 2); an empty dock still leaves dock_apps out.
  This sets rows_below_dock over a theme's own value (Six columns has 1), so the TV shows exactly
  the rows the sheet showed. Manage reads the stored choice back the TV's way: an app that is gone
  or switched off is skipped and the later ones move up. One shared `DockEditor` in :ui:design
  (long press and drag across rows, the glass highlight on the lifted app, springs for the others,
  a light tick on pick up and drop, nothing moves under reduced motion; the up, down and take out
  buttons stay; TalkBack actions Move up, Move down, Move to the dock or row n, Take out) replaces
  the two copies in :feature:setup and :feature:launcher; its words are `cyt_dock_*` in :ui:design.
- D-W8-30 Device test fixes (owner: "not easy to understand to hold", "remove arrows", "remove
  created row 2 or 3", "stop & undo should have confirmation"). The dock sheet drops the up and down
  arrows: each app has a small take out button and a six dot grip (48 dp) that lifts the app at
  once on touch and move, so a swipe elsewhere still scrolls the sheet (a long press on the row
  still lifts it too); the sheet scrolls by itself near its edges while an app is lifted. TalkBack
  keeps Move up, Move down, Move to the dock or row n and Take out. Row 2 and Row 3 have "Remove
  row" (never the dock): the row's apps go back to the other apps, a lower row moves up, and the
  sheet's undo snackbar puts it back. Stop and Undo ask first (`Confirmations` in core:flows says
  which buttons: the Working screen's Stop and undo, a stop card's Stop here and Undo what we did,
  Manage's open run card's two): a sheet with a question, one calm sentence, the verb as a guarded
  primary and Cancel or Keep going unguarded; the intent is sent only on yes. "Undo everything"
  already had its own preview and confirmation (UX 4.13) and is unchanged; the check screen's "No,
  put it back" answers a countdown and stays one tap; Manage's per app "Put back" keeps its undo
  snackbar.
