# RF review: RFC-DOCK-1 (the Setup plan pushes the dock and the look)

Reviewer: Fable review gate, 2026-10-20. Scope: branch `setupcfg` in `cyt-wt/setupcfg`, four commits on
main `3bff55d` (41f2c04, 5a49ef0, 18c6b95, fa9c6f9; `git diff main..setupcfg`, 25 files), against
PRODUCT-SPEC 3.4, 5.8, D15, D21, D23, D36; SECURITY 3; UndoOrder and RestoreCommands; RFCS.md; and
the curtain plan (`app-design/CURTAIN-PLAN.md`, RFC-CURTAIN-1), which builds on this branch.

## Verdict: APPROVE WITH FOLLOW-UPS

The change does what the RFC says, keeps HOME safety untouched, keeps the plan honest, and undoes
exactly. No High or Critical finding. Two items must be done before merge; neither is a code change
to the branch: the test run (F1) and the RFCS.md merge (F2). The rest are follow-ups.

## Findings

| # | Sev | Where | Finding | Status |
| :- | :- | :- | :- | :- |
| F1 | Must do before merge | whole branch | No test run evidence is in the worktree (no `build/` dirs). The seven Roborazzi PNGs were re-recorded, which implies a record run, but the branch must pass in verify mode. Run on forge: `:core:ops:test :core:planner:test :core:backup:test :core:flows:test :feature:setup:test` (verify, not record) and `checkModuleGraph`, and attach the summary to the merge. | Open, process |
| F2 | Must do before merge | `RFCS.md` | `main` gained RFC-CURTAIN-1 appended after the RFC-DOCK-1 paragraph; this branch rewrites that paragraph's heading and appends an "Applied" paragraph in the same place. Expect a textual conflict at merge; keep both (DOCK-1 applied text first, CURTAIN-1 after). | Open, trivial |
| F3 | Low | `SetupMachine.kt:309`, `check_dock_sentence` | The check screen says "The new home screen, with {dock} along the bottom" from `plan.summary.dock`. When the config push was skipped (CONTINUE), the TV shows the launcher's built-in dock and the sentence is wrong (the builder's own doubt). `RunState.AwaitingConfirm` carries no skipped set, but the ledger does: at `AwaitingConfirm`, if no `Done` exists for the plan's config push, use the generic sentence and let Done say "the dock could not be sent; set it from Manage". | Follow-up |
| F4 | Low | `DefaultPlanner.setup`, `aLauncherAlreadyInChargeGetsOnlyTheLook` | A re-run on a TV already on our launcher plans the five look steps again, including `RestartHome` of the live HOME launcher (a visible restart) even when the probed overlay already equals the config. Skip the look when `snapshot.launcherOverlay`'s digest equals `config.sha256` and no wallpaper changes. | Follow-up |
| F5 | Low | `SetupMachine.replan`, `DockLayers.trim` | An empty dock still pushes a config.json (with `dock_apps` omitted) and restarts the launcher for no visible change. Skip the look when the doc equals the launcher's defaults and the theme is null. | Follow-up |
| F6 | Low | `PlannerFixtures.OverlayTransport` | The planner's test double re-states `FileOverlayTransport.writePlanSteps` by hand (the engine is a layer above, so it cannot be imported). `WhatWeFoundMatchesThePlanTest` uses the real transport, which limits the drift risk; add one `:core:flows` test asserting the double's ops equal the real transport's for the same blobs. | Follow-up |
| F7 | Low | `look()` step 1, `T_START_WAIT` | `StartActivity(launcher, wait = true)` has a 180 s timeout; a launcher that never draws its first frame stalls the run for three minutes before CONTINUE moves on. Pre-existing in ApplyTheme; now also in Setup. Consider `wait = false` for the look's first start (the section's own `-W` start follows) or a shorter timeout for that op. | Follow-up |
| F8 | Info | PRODUCT-SPEC 5.8 | The spec still shows the four-parameter `PlanRequest.Setup`; DECISIONS-W1 records the edit. Update 5.8 at merge. | Follow-up, docs |
| F9 | Info | curtain plan | The look group's first op is `StartActivity(launcher, wait = true)`, so after this merge the launcher first appears at the look group's first step, not at `RestartHome` or the takeover. `CURTAIN-PLAN.md` section 3 (items 3 and 4) is corrected in this same change: INSTALLED right after the install, FINALE plus `Settle(3)` before the look group's first step. | Done in app-design |

## What was checked and held

- **HOME safety.** Group `theme` holds `StartActivity`, `Mkdir`, `PushAsset`, `PushAsset` or
  `RemoveFile`, `RestartHome`: none touches HOME, `pm enable`, `set-home-activity` or the watchdog.
  The guarded section is unchanged op for op (`SetupLookTest.theLookComesAfterTheInstallAndBeforeTheSwitch`
  asserts `without.sections.single().copy(from, to) == section`), so the 150 s watchdog window holds
  the same steps as before. A failed push happens while the TV's own home screen is HOME. `RestartHome`
  names our component explicitly (`am force-stop` plus `am start -n`), so with two HOME claimants
  nothing resolves HOME; force-stopping a launcher that is not HOME is harmless, and on a re-run where
  ours is HOME it is the same pair ApplyTheme already sends. Placement before the section (the
  builder's doubt) is right: the check screen's thumbnail and sentence describe what the TV shows,
  and nothing cosmetic sits inside the window the watchdog measures.
- **Staleness.** `Staleness.diff` ignores `PushAsset` and `RemoveFile`, so the replaced-launcher case
  (priors `present = false` while the PRE_PLAN snapshot still holds the old overlay) cannot pause the
  run as stale.
- **Plan honesty.** The push is a `sync:` line with the blob's sha256 and the `am` lines are plain;
  both are in `commandsOf` (D21) and in `planHash` (asserted in two tests). S3's summary is unchanged
  (`assertEquals(without.summary, p.summary)`), and `summary.dock` now names a dock the plan really
  sends. Done remembers the digest only from a change record, and `RunLedger.changes` emits a change
  only for a `Done` record, which the engine appends only on `Outcome.Ok`: a skipped push leaves no
  digest and no drift warning.
- **Undo and restore exactness.** Traced by hand and by the tests: fresh launcher (both priors
  `present = false`, config removed, a never-present wallpaper removal yields no step); upgrade of our
  own launcher (`overlayGone = false`, probed overlay as prior); replaced signer (uninstall takes the
  folder, both priors `present = false`); launcher installed before us with and without a config.json;
  wallpaper removed by Setup and pushed later by Manage (phone undo and laptop script both delete it,
  `RestoreCommands` branch order verified: owner gone, written and deleted again, then the new
  branch); launcher removed (files go with the folder, no file steps). `UndoOrder` phases still put
  the home screen and the launcher before the files. The `undoEverything` file branch now also
  requires the last record to be a push, which is stricter and correct.
- **Failure policy.** Every Setup look step is CONTINUE, ApplyTheme keeps STOP_AND_ROLLBACK on its
  pushes (`applyThemeKeepsItsPriorsAndStopsOnAFailedPush`). Agreed: outside a section a STOP would
  pause the whole run over a cosmetic step.
- **Frozen interfaces.** `PlanRequest.Setup` gains two defaulted trailing fields, marked, with the
  RFC in RFCS.md and the DECISIONS-W1 entry; every caller compiles unchanged. `SetupDeps` (W8's own)
  gains `blobs` (required) and `encoder` (defaulted); `AppGraph`, `Harness` and
  `IntegrationBehavioursTest` were updated. `Look` is `internal` to `:core:flows`. No layer rule is
  crossed (`:core:flows` reads `:core:engine`'s `FileOverlayTransport` only in tests).
- **Tests.** `SetupLookTest` (order, priors, policies, hash, no switch, installed, replaced, in
  charge, ApplyTheme unchanged, undo), `WhatWeFoundMatchesThePlanTest` with the real transport on the
  Mi TV and emulator captures (step lists, S3 agreement, Show commands), `SetupMachineTest.replanSendsTheDockAndTheLookAsOneConfigJson`
  (bytes equal `DockLayers`, Done remembers the digest), `UndoOrderTest`, `RestoreCommandsTest`. The
  replan no-switch branch is covered at planner level only (`noLauncherNoConfigPush`), which is
  acceptable since `replan` passes `null` when `possible` is false by a one-line condition.
- **Curtain fit.** The curtain's cues and uninstall slot around this change without touching it:
  install and start first, OFF cues per disable, INSTALLED after the launcher install, FINALE and a
  3 s settle before the look group, uninstall last. The look's `StartActivity` is the hand-off the
  curtain relies on (F9).
