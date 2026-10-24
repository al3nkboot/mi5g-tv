# RF review: RFC-CARDS-1 (module cards stay off until the person asks for a card)

Reviewer: Fable review gate, 2026-10-20. Scope: branch `cardsoptin` in `cyt-wt/cardsoptin`, three
commits on main `9087cb1` (c46d5de, 5a5d87b, 8c56153) plus the builder's merge of main `797c704`
into the branch (f96dee2, made during this review), against PRODUCT-SPEC 5.8, D15, D21, M2, M3,
UX 5, RFC-DOCK-1, the owner's ask ("retro show as a module, but it is optional, they can add after
the completion, don't force"), and the upstream launcher's `module_cards` behaviour.

## Verdict: APPROVE WITH FOLLOW-UPS

The owner's intent is met: a first Setup writes `module_cards` false, the Done copy says cards are
optional, nothing on the TV asks about a card until the person adds one or taps "Show its card",
and no path turns cards off behind the person's back except the one in F1. One item must be fixed
before merge (F1, a small rule change with its tests); the rest are follow-ups.

## Findings

| # | Sev | Where | Finding | Status |
| :- | :- | :- | :- | :- |
| F1 | Must fix | `Look.kt` `Cards.of` | For a TV with no kept choice, the rule reads the TV's overlay only when its digest equals `ourOverlayDigest`, returns on when this phone pushed something it cannot read back, and off when this phone never pushed a look. Two wrong outcomes: (a) a TV set up by a build before RFC-DOCK-1, or from another phone, that never had a look pushed from this phone (`ours == null`) runs the launcher's default, cards on, and may already show a card the person accepted; the first dock or theme change from Manage now writes `module_cards` false and the card vanishes, with nothing in the preview saying so. (b) A TV whose overlay was last written by another phone with this build (`module_cards` false, digest differs from ours) reads as on, and the next look change turns cards on, which is the forcing the owner asked us not to do. Rule to use instead: a kept choice wins; else decode the TV's overlay whatever its owner and take the raw `LauncherConfigDoc.moduleCards` when it is explicit; else (no overlay, unreadable, or the key absent) the caller's default: Manage passes true (what the launcher is doing on that TV right now), Setup passes false (the owner's ask; this also keeps the owner's own TV off on a re-run, since the overlay Setup pushed there carries no key). Tests: the six cases above plus the owner's TV (overlay without the key, ours matching) for both callers. | Open |
| F2 | Low | `ModulesMachine.install`, M3b copy | The add's plan now also pushes config.json with `module_cards` true, but nothing on M3b or the Add step says so; the only sentence ("Cards are off on this TV's home screen. Show one and the TV asks you to confirm.") sits on the installed list. One line on M3b while `cardsOff`: "Adding it turns cards on for this TV's home screen; the TV asks before the card shows." The plan, its hash and the gate are honest already. | Follow-up |
| F3 | Low | `DefaultPlanner.addModule`, STOP_AND_ROLLBACK on the look | Judged acceptable, with one consequence worth knowing. Outside a guarded section STOP_AND_ROLLBACK pauses the run; nothing is rolled back by itself. The paused add goes to the open-run card, where "Undo what we did" (which now asks first) would uninstall the module and "Carry on" retries the push. So the install is never rolled back unexpectedly. CONTINUE was not chosen, rightly: `keepCardsOn` runs on `Finished`, so a skipped push would have recorded cards on that the TV does not have. Two notes: a skipped `RestartHome` (CONTINUE) still ends `Finished`, so the prefs say on while the launcher asks only after its next restart (fine); and because the config push's prior is the probed overlay (`present = true`), "Undo what we did" cannot put the earlier config.json back (the 0.1 limit on files that existed), so cards stay on after an undo. Add a sentence to the open-run card's undo question when the plan is an add with a look, or accept and document. | Follow-up |
| F4 | Low | `ModulesMachine.load`, `cardsOnLook` | "Our launcher is home" is read as `tv.launcher != null`. That is set at Done only when the switch was confirmed, cleared by "Use the TV's own home screen" and by Undo everything with the launcher removed, so it is the right flag for the app's own actions. It does not see a HOME changed by hand on the TV (the TV's settings, a factory update): then the add's `RestartHome` still starts our launcher over the stock home and `PressHome` returns, a brief wrong screen. The same holds for ApplyTheme today; the drift banner covers the overlay, not HOME. A `homeNow` check in `cardsOnLook` (the latest snapshot's resolved HOME equals the launcher) would close it. | Follow-up |
| F5 | Info | `PlanRequest.AddModule` | Two defaulted trailing fields, marked `RFC-CARDS-1`, RFC text in RFCS.md, DECISIONS-W1 entry, every caller compiles; `SetupDeps.homePrefs` is defaulted and `AppGraph` passes it. PRODUCT-SPEC 5.8 still shows the three-parameter form (as for RFC-DOCK-1): update at merge. | Follow-up, docs |
| F6 | Info | merge f96dee2 | The builder merged main into the branch during this review. Checked: no conflict markers, the post-merge patch against main is byte for byte the three commits' patch (58 files, 344 insertions, 36 deletions, images aside), `SetupMachine` carries both the Confirmations work and `setupHome`, and main did not touch the 27 Done and s06 screenshots the branch re-recorded, so no golden needs a second recording. Nothing to do. | OK |
| F7 | Info | `HomePrefs` | Older `home.txt` files have no `cards=` line and load as `cards = null`; the file is rewritten with the line on the next save. No migration step is needed beyond F1's rule for the null case. | OK |
| F8 | Info | `ModulesMachine.showCard` | Uses the latest stored snapshot's overlay (possibly stale) to decide; harmless, since a kept choice or a fresh push follows. After the push, the M3c mirror needs a live session; without one the TV still asks and the phone simply does not mirror. Acceptable. | OK |

## What was checked and held

- **Owner intent.** `Look.encode` always writes `module_cards`; `SetupMachine.replan` passes false on
  a first Setup (`setupKeepsModuleCardsOffUnlessTheyWereTurnedOnBefore`, and the reference plans in
  `WhatWeFoundMatchesThePlanTest` assert the pushed bytes decode to false); Done copy "Optional. You
  can add cards later in Manage." replaces the old nudge; Setup never plans a card. Cards come on
  only from a person's tap: the Modules add (one plan, one gate) or "Show its card" (an ApplyTheme
  plan, guarded against taps through an overlay, `showItsCardIsGuarded`). No "turn cards off"
  action exists, which is right: with no card module nothing is discovered and nothing asks.
- **Plan honesty.** The add's look steps are the transport's own (`addModuleWithTheLookTurnsCardsOnInTheSamePlan`:
  `InstallStream`, then `StartActivity`, `Mkdir`, `PushAsset`, `RemoveFile` or `PushAsset`,
  `RestartHome`, then `PressHome`), in group `theme`, with ApplyTheme's priors and narration, so the
  plan hash the gate confirms and Show commands carry the push. Without the launcher installed the
  add goes without the look. `keepCardsOn` writes HomePrefs and the D15 digest only on `Finished`,
  and Done saves HomePrefs only when the run's pushed digest is the one it planned
  (`setupHome` matched against the ledger's push), so what the phone remembers is what the TV got.
- **M2 keeps the value.** `HomeScreenMachine.apply` passes `Cards.current(...)` so a dock or theme
  change never flips cards (`addingACardTurnsModuleCardsOnInTheSamePlanAndALaterLookKeepsThem`
  asserts a later theme change keeps true). `rememberPushedLook` is one helper now, shared by M2
  and M3.
- **Upstream semantics.** `module_cards` off means no DOCK_CARD discovery, no prompt, and the
  Settings long press does nothing; on means the launcher asks once per declaring app with its
  12 hex, which the phone mirrors (M3c, D1). The branch's reading matches the launcher at 1bbf0c9.
- **Tests.** Planner (add with and without the launcher), flows (Setup first run and kept choice,
  Done saving HomePrefs, add turning cards on and the next add sending no look, Show its card and
  its mirror, `Cards.of` cases), feature (guarded button, new `modules_cards_off` shot), 27 Done
  and s06 shots re-recorded for the copy change. F1's rule change needs its own cases added to
  `aTvWithoutAKeptChoiceKeepsWhatItsOverlaySays`.
