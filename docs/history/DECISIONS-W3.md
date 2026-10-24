# W3 decisions: DSL, executor, journal

Stream W3 (`:core:ops`, `:core:planner`, `:core:engine`), 2026-10-19. This file starts with the
owner's fixes for the Fable R1 gate review (`app-design/reviews/R1-W3.md`, verdict PASS WITH
FIXES). R1's own commits (3db9946, ea5ffdb, eca86e0) carry F1 to F7. Spec amendments the
coordinator must carry into PRODUCT-SPEC are in `RFCS.md`.

## Decisions taken inside W3

- D-W3-1 (R-1) The two takeover layers are ordered by time. The planner arms the watchdog for
  150 s (`DefaultPlanner.WATCHDOG_SECONDS`): the phone's longest confirm window (120 s without a
  thumbnail, D23) plus 30 s for the section's steps between arming and the check screen (stock
  disable, the wizard disable, `set-home-activity`, `resolve-activity`, the check screen's
  thumbnail). This keeps every user-facing number of the spec: the countdown on S4b is still 90 s,
  or 120 s per D23. Only the TV-side timer moves, from 120 s to 150 s (inside the 30 to 300 grammar,
  so goldens 16 and 17, which render a 120 s case, are unchanged). As a guard that holds whatever
  the steps cost, the engine also caps the phone's deadline at the watchdog's deadline minus 10 s
  (`DefaultEngine.WATCHDOG_MARGIN_MS`, measured from the monotonic time of the `Section ARMED`
  record, which is written before the arm line is sent). With 150 s the cap does not bite on any
  normal run; it does on a slow TV or with a planner configured for a shorter watchdog, and it
  shortens the countdown rather than letting a "Yes" land after the watchdog fired. Spec text to
  amend: RFC-W3-1. Tests: `SectionTest.withoutAThumbnailThePhoneDeadlineIsEarlierThanTheWatchdogs`,
  `SectionTest.aShortWatchdogCapsThePhoneDeadline`, the deadline assertion in
  `SectionTest.yesISeeItConfirmsTheSection`, `PlannerTest.rebootIsItsOwnPlanAndTheWatchdogOutlastsThePhonesWindow`.
- D-W3-2 (R-2) Resume with a plan file that is missing, tampered or from another TV builds step R
  from the journal when the journal shows an open section (`JournalStepR`): the token from the
  `Section ARMED` record; the arm step's `Begin` (its `stock` is the home screen the watchdog would
  restore; no arm `Begin` means nothing on the TV changed, and the run is abandoned as before); every
  `Disable` `Begin` inside the section, inverted to `Enable`; the `SetHome` `Begin`'s prior home
  (else the arm's stock). It runs `ConfirmHome`, `KillWatchdog`, the enables, `SetHome`,
  `VerifyHome`, `PressHome` through the same step R code (journaled, step R vocabulary only,
  re-rendered), then ends the run `ABANDONED` with `home = CONNECTION_LOST`. While the TV is not
  connected nothing runs and the journal stays open, so the next resume runs it. A failure pauses
  with the error and the section open; continue, undo and abandon all retry step R and then
  abandon. Without an open section the old behaviour stays (journal `End(ABANDONED)`, `Idle`).
  Tests: `ResumeTest.anUntrustedPlanFileWithAnOpenSectionStillGetsStepRFromTheJournal` (missing,
  tampered, with the wizard quirk; also disconnected first), `ResumeTest.aFailingJournalStepRPausesAndContinueRetriesIt`.
- D-W3-3 (R-3) `DefaultPlanner` takes `profiles: ProfileSource? = null` as its last, defaulted
  constructor parameter, so the R0: 15 constructor still compiles (no RFC needed). SwitchOff and
  Leftovers now use the planner's full rule (`protectedReason`: the denylist with the profile, so
  `never_disable`; the profile's stock launchers and every HOME claimant; the wizard only inside the
  switch). PutBack refuses the profile's wizard under the takeover quirk while our launcher is HOME
  (issue #49: the wizard would take the Home button back). Setup keeps using the request's
  resolution. `ProfileSource.resolve` is `suspend`; the planner is a plain function (frozen), so it
  calls it once per plan through `runBlocking`, lazily, only for those requests. The bundled source
  (`BundledProfileSource.resolve`) never suspends, so this returns at once. W8 wires
  `DefaultPlanner(..., profiles = <the app's ProfileSource>)`. Test: `PlannerTest.manageRequestsApplyTheProfile`.
- D-W3-4 (R-13) The executor's live check adds the home screen rule: a `Disable` of the run
  snapshot's resolved HOME or any HOME claimant is refused (`ProtectedPackage`, "protected: it is
  the TV's own home screen ...", the line never sent) unless the step is inside the open guarded
  section. Only `Disable`: an `Uninstall` of our launcher after "use the TV's own home screen" must
  still pass. Test: `EngineTest.theLiveCheckRefusesToSwitchOffAHomeScreenOutsideTheSection`.
- D-W3-5 (RFC-W8-5) `PlanRequest.Reboot` and `PlanKind.REBOOT` are added to the frozen
  `Planner.kt` (accepted on main). The plan is one `Op.Reboot` step (`CONTINUE`, narration
  "Restarting the TV"), journaled like any write. `Op.Reboot` keeps its rendered privilege
  `TAKEOVER` (golden 51 unchanged); the gate skips a plan of kind `REBOOT` whose steps are all
  `Reboot`, so a write plan relabelled `REBOOT` (the kind is not under the hash) is still gated.
  Tests: `EngineTest.aRebootPlanIsJournaledAndNotGated`, the planner test above.
- D-W3-6 (RFC-W2-1) The single-call-site lint admits `:adb:session`'s one opt-in: in
  `adb/session/src/main/` exactly one `@OptIn(RawShell::class)` in all, directly on
  `internal object SessionLines`, no file-wide opt-in, no propagating `@RawShell`, and at most five
  `ShellLine(` constructions in that file (comments ignored). W2's `Support.kt` meets it as it
  stands on w2. Tests: `SingleCallSiteLintTest.shellLinesAreBuiltOnlyByTheRendererTheProbeAndThePlanCodec`,
  `SingleCallSiteLintTest.theSessionLinesRuleAcceptsOneOptInAndRefusesAnyOther`.

## The Low findings

| # | Outcome | What and why | Test |
| :- | :- | :- | :- |
| R-4 | Spec amended (RFC-W3-2), code unchanged | `RestartHome` stays one step with two lines, as `ArmHomeWatchdog` (now `rm -f` then the template, F2) already is: the executor runs a step's lines in order and journals all of them in the one `Begin` before the first is sent, so the journal is no weaker. Splitting would give one op two step indices and two `Done` records, which the plan, the hash and `ChangeRecords` do not model. Milestone: the coordinator's spec pass at the W3 merge | existing golden 27 |
| R-5 | Fixed | After an `Install` or `InstallStream` verifies by `versionCode`, the engine reads `Prober.installedSha256` and fails the step on a mismatch or an unreadable hash ("installed apk sha256 X, got Y"). Applies to every install (module and launcher); the in-doubt re-verify on resume uses the same check | `EngineTest.anInstallIsCheckedAgainstTheInstalledApksHash` |
| R-6 | Fixed | An undo removes each step it undid from `done`; a run whose journal holds a `Rollback` written while no section was open (`Replay.undoStarted`) resumes as an undo, and `continueRun` on such a run carries on the undo (with the undo's gate) instead of running forward. Also fixed on the way: the undo loop iterated `done.reversed()`, which on JDK 21 is a live view of the set; it now iterates a copy. A non-ADB failure in any background job now lands in `Paused(STEP_FAILED)` instead of escaping the coroutine | `ResumeTest.aCutOffUndoCarriesOnUndoingAfterResume` |
| R-7 | Fixed | A `SESSION_END` step whose line or verify read hits the closed socket is journaled `Done(verified = false)` | `EngineTest.aSessionEndStepThatLosesTheSocketIsNotVerified` |
| R-9 | Fixed | When `FileJournal` creates a journal file it forces the file's directory once, and the parent of every directory it had to create (`FileJournal.syncDir`, a read channel on the directory; false where the platform does not allow it, which Android's Linux does) | assertion in `FileJournalTest.appendsForcesAndReadsBack` (on Linux) |
| R-10 | Fixed | `useStockHome` skips its `Enable` when the undo already plans `Enable` or `DefaultState` for the package, so a stock launcher (or wizard) whose original state was `DEFAULT` ends `DEFAULT` | `PlannerTest.undoEverythingLeavesAnOriginallyDefaultStockLauncherDefault` |
| R-12 | Fixed in the verify; W8 trims | The settings verify compares the read value with the written one without trailing spaces (the read's lines are trimmed; leading spaces survive), so an untrimmed name applies and verifies. The renderer does not refuse an untrimmed value: a prior value read from the TV with a trailing space would then make `UndoEverything` refuse the whole plan. W8 still trims the typed name (milestone: W8's device name screen) | cases in `CheckTest` |

R-8 (Low, the resolver focus) is not in this round: it needs main's `:core:model`, which admits
`android`. At the merge the `runCatching` in `DefaultPlanner.takeover` (rollbackIfFocus) becomes
plain code and the resolver joins the `SectionTest` focus cases; the tests' reflection fallback
for `android` keeps working because it tries the constructor first. R-11 (Info) is W8's: after
"abandon" while disconnected with an open section, show "Reconnect to the TV to put the old home
screen back" and retry after the reconnect.

## Goldens

No rendered line changed in this round: no golden file was re-recorded. Goldens 16 and 17 (which
W2's PmGuard copies) still render the 120 s case they always did; the planner's 150 s reaches a
plan, not a golden.
