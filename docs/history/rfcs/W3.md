# RFCs from W3 (DSL, executor, journal)

For the coordinator and the Fable reviewer. None changes a section 5 signature beyond the accepted
RFC-W8-5; both below amend spec prose so it says what the code does.

## RFC-W3-1 The TV watchdog sleeps 150 s (R1 R-1, D-W3-1)

What: D14 ("sleeping N = 120 s"), D23 ("The watchdog on the TV is always 120 s"), section 3.4 ("the
watchdog does step R's core at 120 s") and the I3 acceptance line ("within 120 s with the phone in
airplane mode after `set_home`") become 150 s. Why: the watchdog's time runs from arming, before
the stock disable, `set-home-activity` and `resolve-activity`; the phone's window runs from the
check screen and is 120 s without a thumbnail (D23). At 120 s the watchdog could fire while the
phone still accepted "Yes", leaving the journal saying HOME is ours while the TV had put its own
back. 150 s is the 120 s window plus 30 s for those steps; the engine also caps the phone's
deadline at the watchdog's minus 10 s. The user-facing countdown (90 s, or 120 s per D23) does not
change. Affected: PRODUCT-SPEC D14, D23, 3.4, the I3 list; the R1 review's I3 re-verify item
("back and home within 120 s"); S12 timings on the emulator. Alternative not taken: keep 120 s and
cap the phone's deadline only, which shortens the D23 120 s countdown to about 95 s.

## RFC-W3-2 `RestartHome` runs as one step of two lines (R1 R-4)

What: PRODUCT-SPEC 5.2 says of `RestartHome` "the executor runs them as two steps". Proposed text:
"renders two lines (`am force-stop`, then `am start -n`); the executor runs a step's lines in
order inside that one step, and the step's `Begin` record carries both before the first is sent".
Why: that is the general rule the engine follows, and `ArmHomeWatchdog` (now `rm -f` of the token
file, then the template, R1 F2) relies on it too. Two steps would give one op two indices and two
`Done` records that the plan, the plan hash and `ChangeRecords` do not model. Affected: spec prose
only; golden 27 already shows the two lines.
