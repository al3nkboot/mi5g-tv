# RF review: two watchdog safety fixes from the first Mi TV runs

Reviewer: Fable post-merge review gate, 2026-10-20. Scope: main `51b9743`, commits `a12a39f` (the
run's screenshot cleanup removes the file only) and `e338ad6` (the home watchdog sleeps in the
background and waits), against PRODUCT-SPEC 3.4, D14 and SECURITY 3.6 (the TV-side dead-man timer),
RFC-W3-1 (phone deadline before the watchdog's), PmGuard (INTEGRATION-PLAN T4), the W3 and session
goldens 16 and 17, and the lead's device facts (Mi TV 4A, Android 9, mksh with toybox 0.7.6).

## Verdict: APPROVE WITH FOLLOW-UPS

Both fixes are correct, minimal and pinned by tests. Every safety property of the watchdog holds
with `sleep N & wait`, and the run 2 record shows the second layer (the token file) did its job even
while the kill failed. Nothing must be fixed before this stays on main; the follow-ups add
regression coverage and defence in depth.

## Findings

| # | Sev | Where | Finding | Status |
| :- | :- | :- | :- | :- |
| F1 | Low | `ScriptedTv.kt:128-135`, engine tests | The engine's scripted TV treats `mkdir -p` as a no-op, has no notion of the run directory, and `pkill` always ends a matched watchdog, so neither device behaviour can regress a test today. Model the directory: `mkdir -p` creates it, a watchdog line whose log redirect targets a missing directory fails (exit 1, no process), `touch` of the token file fails without the directory, and `rmdir` removes an empty directory. Then one engine test runs the real `AdbProber` scripts (the screenshot and its cleanup) through `ScriptedTv` across `Mkdir(TMP)`, the section's thumbnail and `ArmHomeWatchdog`, and asserts the watchdog is armed. That would have caught run 1. | Follow-up, W3 |
| F2 | Low | `fixtures/conformance/` | The SIGTERM deferral cannot be modelled usefully in a fake (it is mksh's foreground-wait rule); it belongs on file as a conformance fixture: the Mi TV session's exact lines and exits (arm 0, `pgrep` finds the pid, plain `pkill` 0, `pgrep` finds nothing, `pkill -9` returns 137 and kills its own shell, `SigIgn` shows only SIGHUP), next to the emulator capture, with a runbook line that the lead repeats the four commands on any new TV model. The template test (`templatesArePinned`) is the regression guard for the body text. | Follow-up, W9 |
| F3 | Low | `ShellRenderer` `ArmHomeWatchdog`, `ConfirmHome` | Defence in depth: the arm op already emits a preparatory `rm -f` of the token file; a `mkdir -p /data/local/tmp/cleanyourtv` as its first line (and before `ConfirmHome`'s `touch`) would make both ops self-sufficient should anything ever remove the directory again. Two constant lines, in the plan hash, goldens 16 and 17 re-recorded. | Follow-up, W3 (RFC: op lines change) |
| F4 | Low | `docs/runbook-0.1.md` I1 step 10, privacy text | An identity check now leaves an empty `/data/local/tmp/cleanyourtv` behind (the probe's `mkdir -p` stays, the png is removed). Acceptable: it is a 4 KB entry in the shell-writable tmp, holds nothing, and the run needs the directory anyway. The runbook's "no trace" step and any "writes nothing before you confirm" wording must say "an empty folder may remain". Moving the probe png out of the run directory would need a new `RemoteDir` (grammar is fixed prefixes only) and is not worth an RFC. | Follow-up, docs |
| F5 | Info | `Watchdog.kt`, `pgrep`/`pkill` pattern | After a successful kill a stray `sleep N` lives on until it times out (up to 300 s), orphaned, holding the log file open. It cannot run any of the body, does not match `[c]leanyourtv-rollback-T` (its command line is `sleep N`), so `KillWatchdog`'s verify passes and nothing else sees it. Acceptable. Add one runbook sentence so a lead checking `ps` does not mistake it for a live watchdog. | Info |
| F6 | Info | `Watchdog.kt`, planner | `watchdog-T.log` is never removed (pre-existing, not from these commits). A `RemoveFile` of the log after `KillWatchdog` would tidy it; tiny file, low value. | Info |
| F7 | Info | SECURITY 3.6 | The doc still shows the first template (no nohup, no token file, no `& wait`). Update the quoted line to the current `Watchdog.TEMPLATE_NOHUP_SETSID` shape. | Follow-up, docs |
| F8 | Info | `adb/session/src/test/resources/watchdog/16,17` | The session's goldens are hand copies of W3's and no test asserts they are equal; both were updated here. A one-line test comparing the two resource files would stop them drifting. | Follow-up |

## What was checked and held

- **Fires after N seconds when not defused.** `sleep N & wait` returns when the one background child
  ends, after N seconds; then the token check and the rollback run exactly as before. The body after
  `wait;` is byte for byte the old body. `templatesArePinned` pins both templates;
  `WatchdogTest` still asserts the seconds, package, component and token land where they must.
- **Survives the adb socket closing.** Variant A is unchanged in signal terms: `nohup` sets SIGHUP to
  ignore, which the `sh` and its `sleep` child inherit, and `setsid` puts both in their own session
  and process group, out of reach of adbd's cleanup of the exec's group. `wait` reads nothing from
  stdin; the background `sleep` has stdin on `/dev/null` by the POSIX rule for asynchronous lists in
  a non-interactive shell. Variant B (subshell, no nohup) never promised SIGHUP survival; before this
  change a SIGHUP during the foreground sleep killed the sh when the sleep ended without running the
  body, now it kills it at once, which is the same outcome, so B is no weaker. S12 still picks the
  variant on the device.
- **Kill lands at once.** A `sh` blocked in `wait` takes SIGTERM with the default action and exits;
  the lead verified arm 0, pgrep pid, pkill 0, pgrep empty on the TV. `pkill -9` was rightly left
  alone (toybox 0.7.6 kills its own shell with it, exit 137).
- **No widening of PmGuard.** `WATCHDOG_RE` gained the literal ` & wait` inside the quoted body (`&`
  is not a regex metacharacter); the seconds, the token (three places, back-referenced) and the
  package (component in the same package, back-referenced) remain the only variable parts, the
  whole line is anchored, and `refusal` still admits the line only on a full match. The mutation
  test's "extra ; after sleep" case was updated and the other mutations (an extra `;`, a quote, a
  changed path, a changed name) still refuse. The `&` sits inside the single quotes, so the sh sees
  it as part of the body and the outer `&` of the template is still the one that backgrounds.
- **Safe alphabet.** The line stays printable ASCII (`ShellTokens.LINE_RE`), `matches` compares
  against the filled template byte for byte and counts exactly two quotes, and `fill` is one pass.
- **Two layers, as designed.** Run 2's record is the design working: `ConfirmHome` wrote the token
  file before `KillWatchdog`, so when the kill failed the watchdog still exited 0 at the end of its
  sleep without touching HOME, and the run finished `COMPLETED_WITH_SKIPS` with the kill step
  skipped (`CONTINUE`). RFC-W3-1's ordering (the phone's deadline before the watchdog's) is
  untouched: the section's steps and the 150 s window are the same.
- **Run 1's cause and fix.** `openSection` forces a thumbnail right before `ArmHomeWatchdog`, and the
  plan's `Mkdir(TMP)` sits just before the section, so the old cleanup's `rmdir` ran on an empty,
  freshly made directory every time: the redirect `>$DIR/watchdog-T.log` then failed inside `nohup
  setsid sh`, no process, `pgrep` empty, section rolled back, and `ConfirmHome`'s `touch` would have
  failed too. The new cleanup is `rm -f` of the png only; `AdbProberTest` pins the text and asserts
  no probe script contains `rmdir` or `rm -r`; a grep over main sources finds no other directory
  removal; no `Op` can remove a directory (`Mkdir` and `RemoveFile` only). The golden script file
  was updated.
- **Fake adbd.** `testing/fake-adbd` returns exit 1 for `pgrep` and `pkill` and does not model
  processes, so the e2e harness never exercises the watchdog beyond its line shape; the engine-level
  model of F1 is the right place for the directory regression, the conformance fixture of F2 for
  the signal fact.
