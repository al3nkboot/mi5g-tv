# RF review: RFC-CURTAIN-1 (the TV curtain)

Reviewer: review gate (Opus standing in for Fable, whose budget ran out), 2026-10-20. Scope: branch
`curtain` in `cyt-wt/curtain`, nine commits on main `f2e3898` (4328224, bba7ab7, 5cd3885, 074a5a2,
c65dfc1, 7dce387, 802f99c, 41112a6, b062158; `git diff f2e3898..curtain`, 91 files), against
`app-design/CURTAIN-PLAN.md`, RFC-CURTAIN-1 in RFCS.md, SECURITY 3 (3.1 to 3.6) and threat C6,
ARCHITECTURE 3, PRODUCT-SPEC D7 and 5.8, DECISIONS-W1, RF-DOCK-1 and RF-WATCHDOG-1. The bundled APK
was inspected with aapt2, apksigner and dexdump; the e2e evidence in `build/e2e-curtain` was read;
the merge into today's main (`9674800`, cardsoptin) was dry run in a scratch clone.

## Verdict: APPROVE WITH FOLLOW-UPS

The curtain is built the way the plan asks and keeps every safety property that matters: one new
typed op with a fixed argv, nothing of it in the guarded section, every step CONTINUE, nothing in
the restore point, undo never reinstalls it, the receiver and the activity reachable from the shell
only, the single exec call site untouched, a pinned and K1-signed 66 KB APK with no Kotlin in it.
No Critical or High finding. Five items must be done before merge (F1 to F5): two small code
changes (screenshot cost, the "ours" rule for the uninstall), the merge conflicts with the tests run
on the merged tree, one full-motion e2e run without screen capture, and the SECURITY verb row. The
rest are follow-ups.

## Must do before merge

- **F1** Silent steps must not force a thumbnail (engine, two lines plus a test).
- **F2** Uninstall the curtain only when the journals show we installed it (SECURITY 3.2 and C6),
  replacing decision (b).
- **F3** Resolve the six merge conflicts (all keep both), then run the suites on the merged tree in
  verify mode and attach the summary, with the forge build script's output.
- **F4** One full-motion S09 run with burst and recording off, after F1.
- **F5** SECURITY 3.2 gains the `curtain_cue` row; DECISIONS-W1 lists the two 5.8 fields.

## Findings

| # | Sev | Where | Finding | Status |
| :- | :- | :- | :- | :- |
| F1 | Must fix (Medium) | `DefaultEngine.loop` line 240 | The engine forces a screenshot (screencap of the whole screen, a pull of up to 2 MiB, an `rm`) whenever a step's group differs from the step before it. The weave puts a `curtain` step after every switch-off, so every Disable and every cue starts a new group: about two forced screenshots per switch-off (about 40 on an 18 switch-off Mi TV plan) where today's plan takes one per group plus one every 5 s. On a Mi TV 4A each costs a second or more of TV CPU, works against the curtain's frames, and is not in the RFC's "about 0.2 s per cue" budget. The emulator hides it (16 steps in about 2.3 s). Fix in the spirit of the silent-step change: force at `i == 0`, or when a voiced step's group differs from the last voiced step's group; a silent step takes the unforced 5 s path. Test: disables interleaved with silent steps take as many forced screenshots as the same plan without them. | Open |
| F2 | Must fix (Medium) | `DefaultPlanner.curtainIsOurs`, `curtainLeftover`, `weaveCurtain` line 290 | Decision (b) reads "ours" as "present and no other signer known". The probe never reports a signer, so any package called `tv.cleanyour.curtain` counts as ours: Leftovers uninstalls it, and Setup starts it, sends it the cues and uninstalls it as its last step, with no undo (Uninstall's inverse is null). SECURITY 3.2 (`uninstall`: "package installed by us (transaction log)") and threat C6 forbid that, and `removeModule` already applies the rule: `current.installedByUs \|\| journals.any { Install or InstallStream of pkg }`. Use the same test in `curtainIsOurs`: a present curtain with no install record of ours gets no Leftovers step, and Setup plans no curtain at all (like a known other signer). Cost: after the phone app is reinstalled (journals gone) a leftover stays until removed by hand. It is inert, so that is acceptable. This also stops a case where the laptop restore file would carry "cannot be put back" for the curtain (an uninstall record without an earlier install record). Tests: Leftovers and Setup with a present curtain, with and without an install record in `journals`. | Open |
| F3 | Must do before merge | merge into `9674800` | `git merge-tree` is not clean: six textual conflicts, all two additions in one place, all keep both. `DECISIONS-W1.md`: keep main's text for the RFC-DOCK-1 bullet ("PRODUCT-SPEC 5.8 shows the two fields since cardsoptin") and the RFC-CARDS-1 bullet, then add the three curtain bullets. `RFCS.md`: RFC-CARDS-1 first, then the curtain's "Applied" text. `AppGraph.kt`, `Harness.kt`, `SetupDeps`: keep `homePrefs` and `curtainPin`. `SetupMachine.replan`: keep main's `setupHome = ...` line, then the curtain's `showOnTv` and `curtain` and the seven-argument `PlanRequest.Setup`. `WhatWeFoundMatchesThePlanTest`: main's `Look.encode(..., cards = false)` with the curtain argument. No build output is in the worktree, so run on forge, in verify mode, on the merged tree: `:core:ops:test :core:engine:test :core:planner:test :core:backup:test :core:flows:test :core:trust:check :feature:setup:test :tv:curtain:test checkModuleGraph :tv:curtain:checkApkSizeRelease`. Attach the summary and the output of `build-curtain-on-forge.sh` from 802f99c ("identical", sha256, signer digest), which is the only proof of the double build. | Open, process |
| F4 | Must do before merge | e2e S09, full motion | The reduced-motion run passed (28 s, every curtain step exit 0, installed then gone, CurtainActivity resumed for 20.3 s between its start and the launcher's, screen 31 percent different from the stock home, no crash log). The full-motion run ended `Paused(at=20, CONNECTION_LOST)`: CurtainActivity was resumed until the launcher took over, and the emulator stopped answering 0.4 s later, while the burst's `exec-out screencap` loop was running. The builder blames screen capture on the software GL emulator, which is plausible but not shown, and full motion is what every real TV gets by default. Run S09 once more with `CYT_E2E_CURTAIN_MOTION=1` and burst and recording off (after F1, which removes most of the engine's own captures). If it passes, record that and leave the rest to the Mi TV check (runbook C). If it fails, stop and find out why before merge. | Open, process |
| F5 | Must do before merge (docs) | SECURITY 3.2, DECISIONS-W1, PRODUCT-SPEC 5.8 | SECURITY 3.2 says "Verbs with no row above do not exist", and `curtain_cue` has no row yet: add it (args `Component` receiver, package fixed to `tv.cleanyour.curtain`; token; stage and group enums; four `Int` 0 to 999; profile step; no precondition; argv `am broadcast -n RECEIVER -a tv.cleanyour.curtain.CUE ::es token T ::es stage S ::es group G ::ei count N ::ei total N ::ei gcount N ::ei gtotal N`; no verify; no undo). DECISIONS-W1's curtain entry leaves out the two frozen 5.8 fields the RFC names (`PlanRequest.Setup.curtain`, `Choices.curtain`); add them and show them in PRODUCT-SPEC 5.8, as RF-CARDS-1 F5 did. Mark the RFC heading applied. | Open, trivial |
| F6 | Low | `ShellRenderer.cue`, engine `isLost` | A cue that takes longer than its 30 s timeout is `Transport.Timeout`, which the engine treats as Lost and pauses on with CONNECTION_LOST, whatever the step's CONTINUE says. `am broadcast` waits until the receiver has finished, and a manifest receiver sits in the background queue behind every `PACKAGE_CHANGED` the switch-offs just sent. So a slow TV could pause the setup over a cue. A pause is resumable, not a rollback, and every other step behaves the same way, so this is not a blocker. Add `--receiver-foreground` (a constant word, goldens 53 to 59 re-recorded) and have the lead read the cue steps' `ms` from the Mi TV journal. If they come near seconds, give cues a short timeout and let a cue's timeout count as Failed (CONTINUE), not Lost. | Follow-up, W3 |
| F7 | Low | `CueBus.ended` | Once a curtain has detached, `ended` stays true for the life of the process, so the cues sent right after the next `am start` (SCAN, RESTORE_SAVED) are dropped until `onCreate` attaches. That only happens when a cached process outlives a leftover (the reuse path) or Back was pressed and the run restarted. The 10 s `KEEP_MS` window already makes keeping them harmless: drop `ended`. | Follow-up |
| F8 | Low | weave after a rolled-back section | `finishWithSkips` skips the final `Uninstall`, so after "No, put it back" or a lost connection in the section the curtain stays installed (inert, finished on the TV) until the next Leftovers or Setup. Since decision (a) keeps it out of DebugOff, consider having SetupMachine run a one-step Leftovers with the run's own pass when a Setup ends with the curtain still present, before Done or Undone. | Follow-up, W8 |
| F9 | Low | probe, `PackageState.signer` | Once F2 is in, a squatter would also need a record of our install. Read the curtain's signer the way the launcher's is read (`LauncherState.signer`), so `curtainIsOurs` checks a real signer. | Follow-up, W4 |
| F10 | Low | `scripts/build-curtain-on-forge.sh` | The script rsyncs the working tree but stamps `SOURCE_DATE_EPOCH` from HEAD, so an uncommitted edit would be built, pinned and look reproducible. Refuse a dirty tree, or build from `git archive HEAD`. Both builds share one host, image and Gradle cache, which proves the build is free of path and time effects, not free of toolchain drift. That is enough for 0.1. | Follow-up, W9 |
| F11 | Low | `tv/curtain/build.gradle.kts` `curtain.frames` | `CurtainFramesTest` writes its PNGs into `src/test/frames` on every run, so a run on another machine (Robolectric native graphics can differ by a byte) dirties the checkout. Write to `build/` by default and copy to `src/test/frames` only on request. | Follow-up |
| F12 | Info | e2e budgets | Emulator, 1080p: PSS 17.3 MB reduced and 23.7 MB in motion (plan budget 25 MB, tight); frames p90 5 to 6 ms, p99 27 ms and 77 ms (the motion p99 is probably the capture loop). The lead's Mi TV run (runbook C) is the real check: `dumpsys meminfo` and `gfxinfo`, the hand-off with no stock home flash, Back. | Info |
| F13 | Info | `TvAppConventionPlugin` | Taking Kotlin's stdlib back out in `afterEvaluate` depends on how AGP adds it, which could change. Rule 7 in `checkModuleGraph` is the safety net, and dexdump of the bundled APK shows only `tv.cleanyour.curtain` classes. Keep both. | Info |

## Rulings on the builder's decisions

- **(a) DebugOff does not remove a leftover: upheld.** D7 promises debugging off when the person
  taps Finish. An `Uninstall` would put a profile step into the `debug_off` plan, so the credential
  prompt would come back, and cancelling it would leave debugging on. The leftover is inert: no
  launcher category, the receiver and the activity need WRITE_SECURE_SETTINGS, it finishes itself
  within four minutes, and it is gone at the next Leftovers or Setup. F8 is the cheaper way to close
  the gap. Record the change in CURTAIN-PLAN 4(b).
- **(b) "Ours" without a signer: overruled, see F2.** Use the journal rule `removeModule` already
  uses. The rest of (b) stands: a package with a known other signer gets no curtain steps and is
  never touched (`anotherSignerUnderOurNameIsLeftAlone`).
- **(c) Group fields on `CurtainCue`: upheld.** The owner asked for "Adverts: 2 of 4". The fields are
  enums and bounded ints, the extra names are constants, and the words live in the APK. The renderer
  also refuses any receiver package other than `tv.cleanyour.curtain` (property test, which includes
  the `tv.cleanyour.curtainx` and launcher cases), which is tighter than the plan. `Build.BRAND` is
  read on the TV and filtered to `[A-Za-z0-9 .&-]{1,24}`.
- **(d) At least four ghost cards: upheld.** They are generic shapes, not app banners, and the words
  under the picture carry the real count. Cards not pulled in leave when the install starts
  (`cardsOutAt`), so nothing suggests switch-offs still to come.
- **(e) "All clean." until covered or 6 s: upheld.** A black frame before the launcher draws would
  look worse. The finish at 6 s is tested (`theFinaleEndsTheCurtainAfterSixSeconds`), and onStop
  finishes it when the launcher covers it.
- **(f) S09 wires the real pieces itself: upheld for now.** It builds the planner, engine, prober and
  session exactly as AppGraph does. Move it onto `E2eWiring` when that is wired.
- **(g) Empty prior on the uninstall when the run installs it: upheld, and right.** `Staleness.diff`
  checks `Uninstall` priors against the TV before step 0, so `present = true` there would pause every
  first run. On resume the null prior is skipped. With a leftover the prior is `present = true`,
  which correctly pauses if the leftover vanishes before the run. `RestoreCommands` and
  `ChangeSummary` key off the install record, not this prior.

## What was checked and held

- **Op and renderer.** `CurtainCue` renders one argv through `line(...)`: constants `am broadcast -n`,
  the `Component` (grammar re-checked by `comp`), `-a tv.cleanyour.curtain.CUE`, `--es token`
  (re-checked by `token`, `RUN_TOKEN_RE` 16 hex, the same grammar the TV's `Cue.TOKEN` accepts),
  `--es stage` and `--es group` (`^[A-Z_]{1,16}$`), four `--ei` ints 0 to 999. `require` failures turn
  into `RenderRefused` in `render`. `everyCueIsOneFixedArgv` tokenizes 2000 random cues back to the
  exact argv and refuses out-of-range numbers and foreign receivers. `anyOp` now includes cues, so
  the general fuzz and inverse properties cover them. Goldens 53 to 59, one per stage.
  `cueLinesAreInThePlanHash`. PROFILE_STEP, no verify, `check` Ok, `inverse` null, `UndoOrder.target`
  null, transient in `RestoreCommands`, not a change in `RunLedger`. The codecs round trip
  (`OpJson`, `ChangeJson`).
- **Single exec call site.** `ChangeRecordsAndTransportTest` is untouched, the engine still has one
  `session.exec` (`DefaultEngine.send`), the probe is unchanged, and no file outside the renderer
  builds a shell line. `ScriptedTv` only gained an `am broadcast` answer.
- **Placement and TV safety.** The weave runs after the body is planned and changes only order:
  `assertCurtainInvariants` and `theCurtainNeverChangesTheSetupItShows` (150 random TVs, more than 80
  woven) assert that removing the curtain steps leaves the plan's ops, priors, groups, narration,
  policies and rendered lines identical; that sections map to the same first and last ops with the
  same token and rollback; that every curtain step is CONTINUE and none is inside a section; that
  the uninstall comes last; and that apply, re-plan and undo come back to the baseline. The curtain
  token is its own, not the watchdog's. FINALE plus `Settle(3)` sits before the first
  `StartActivity(launcher, wait = true)` (the look's when it applies, else the takeover's), so the
  curtain is covered and finished before `ArmHomeWatchdog`, and the section's thumbnail and focus
  check never see it. The curtain touches no HOME, `pm enable`, `set-home-activity` or watchdog op.
- **A curtain failure cannot stop or roll back the setup.** Every curtain step is CONTINUE outside
  any section. A refused or failed step is skipped, and Done counts only Disable skips, so a skipped
  cue does not change what the person reads. The one exception is the pre-existing timeout-is-Lost
  rule (F6): a pause, never a rollback.
- **Restore point, undo, restore files.** The install is the plan's first step, after the engine's
  PRE_PLAN snapshot, with `Prior(present = false)`. `undoEverything` takes only `present = false`
  installs as ours and skips apps no longer installed, so a finished run gives nothing and a cut run
  removes it (`undoAfterAFinishedAndAnInterruptedRun`). Uninstall has no inverse, so nothing ever
  reinstalls it. `RestoreCommands` drops it when installed and removed again
  (`theCurtainLeavesNoStepWhenFinishedAndItsRemovalWhenInterrupted`). `ChangeSummary` drops it on
  the uninstall.
- **The TV app.** Checked in the APK itself (aapt2 xmltree): `CurtainActivity` and `CueReceiver` are
  exported with `android:permission="android.permission.WRITE_SECURE_SETTINGS"`, so only the shell
  or the system can start or cue it. No intent-filter on the activity (no HOME, no LEANBACK_LAUNCHER,
  no MAIN), `noHistory`, `singleTask`, `excludeFromRecents`, `stateNotNeeded`, `allowBackup false`, no
  permissions requested, no icon. Back takes the default `finish`, onStop finishes, the dead-man
  timer finishes after 240 s without an accepted cue, and FINALE finishes after 6 s. The first cue
  binds the queue's token, so another token or anything after FINALE is dropped (`CueQueueTest`).
  `Cue.parse` drops any token, stage, group or number outside the grammar (`CueTest`). The receiver
  never starts an activity. Reduced motion is read from `ANIMATOR_DURATION_SCALE`. Paints, shaders
  and paths are built once, with no per-frame allocation on the frame path.
- **Engine silent steps.** `publishRunning` lists and counts only voiced steps. The journal, resume
  and Show commands still see every step (`silentStepsAreNeitherListedNorCounted`, which also shows
  narration is not in the plan hash). `aSetupWithTheCurtainRunsAndRemovesIt` runs a whole Setup
  through `ScriptedTv`.
- **Plan honesty.** Every cue, the start and the uninstall are lines in Show commands and in
  `planHash` (Roborazzi `customise_sheet_curtain` in four variants shows them). S3's summary and the
  body step lists are unchanged (`WhatWeFoundMatchesThePlanTest` compares `body(plan)`). The install
  and the 3 s settle are the only voiced curtain steps.
- **Flows and screen.** `ShowOnTv` edits the draft. Simple always shows the curtain and Custom
  follows the switch. An asset that fails its pin plans without the curtain, never refusing the
  Setup (`theTvCurtainIsHandedInAndTheCustomSwitchTurnsItOff`). A draft written before the switch
  existed reads as on.
- **Frozen interfaces.** Marked `// RFC-CURTAIN-1`: `Op.CurtainCue` and the two enums,
  `CurtainPin`, `SetupIntent.ShowOnTv`, `WizardDraft.curtain` (all listed in DECISIONS-W1), and
  `PlanRequest.Setup.curtain` and `Choices.curtain`, both defaulted and trailing, named in the RFC
  but missing from DECISIONS-W1 (F5). `SetupDeps.curtainPin`, `Seams.curtainPin` and the
  `DefaultPlanner` parameters are defaulted and not frozen.
- **ModuleRules rule 7.** `:tv:curtain` sits at layer 10 with an empty external allowlist. No main
  edge may reach `:tv:*`, a `:tv:*` module has no main edge out and no third-party main dependency
  (Kotlin included), and tests stay free (`tvAppIsAnIsland`). `settings.gradle.kts` includes it;
  nothing depends on it.
- **Pin and K1.** Asset sha256 `bd1e075c...6da1`, 65,955 bytes, equal to `PinnedCurtain`. apksigner
  shows v2 and v3, no v1 and no v4, one signer `57641815...5132` = `PinnedLauncher.SIGNER_SHA256`.
  aapt2 shows `tv.cleanyour.curtain` 1 / 0.1, minSdk 26, targetSdk 36. dexdump lists only
  `tv.cleanyour.curtain.*` classes. `PinCheck` is generic, `verifyCurtainPin` runs in `:core:trust`
  `check` and before `:android:platform` `preBuild`, and `BundledCurtainPin` refuses swapped bytes,
  including the launcher's (`CurtainPinTest`). Signing: the keystore and password go over ssh stdin
  into the container's `/dev/shm` with `umask 077` and are removed on exit, never on forge's disk,
  never echoed. No key, password or keystore file is in the diff. The size gate is 256 KB and the
  APK is 66 KB. `tv/` and the asset are unchanged after 802f99c.
- **Reproducible build.** The design is sound (two fresh checkouts in different paths, two
  throwaway containers, `SOURCE_DATE_EPOCH`, no build cache, no R8, `vcsInfo` and dependency info
  off, `cmp` of the unsigned APKs before signing). The run's own output is not on file (F3), and the
  dirty-tree gap is F10.
- **Writing rules.** No em dash in the diff. The only double hyphen is inside the verbatim OFL
  licence text. Commit messages carry no attribution lines.
