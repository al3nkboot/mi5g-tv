# Probe fixtures

`mitv/` stands for the reference TV (Xiaomi MiTV-AXSO0, Android TV 9). The outputs W1 captured
(`getprop`, `pm list packages`, `settings list`, the HOME resolver) come from
`fixtures/conformance/mitv-axso0-2026-10-18/` and are loaded from there. The outputs that capture
did not record are synthesized here in the AOSP dump formats of Android 9, consistent with that
capture's package list (one package per line of `packages-all.txt`, disabled flags from
`packages-disabled-after.txt`, `/data/app` system apps marked `UPDATED_SYSTEM_APP`):

| File | Command | Source of the shape |
| :- | :- | :- |
| `dumpsys-package-packages.txt` | `dumpsys package packages`, before the probe's `grep` | `Settings.dumpPackageLPr` |
| `query-home.txt` | `cmd package query-activities` (brief) for HOME | `PackageManagerShellCommand.printResolveInfo` |
| `tv_input.txt` | `dumpsys tv_input` | `TvInputManagerService.dump`, `TvInputHardwareManager.dump` |
| `hdmi_control.txt` | `dumpsys hdmi_control` | `HdmiControlService.dump` (spike S7 shape) |
| `meminfo.txt`, `gms-path.txt`, `activities.txt`, `df.txt` | as named | toybox and `dumpsys activity` output |

`gtv34/` is synthesized as a whole: no file in it is a capture. It holds the same set in the
Android 12 to 14 shapes, written from the AOSP sources for a Google TV emulator image (API 34):
`HdmiPortInfo{...}` port lines, `topResumedActivity=`, `query-activities` with setupwraith.

SYNTHESIZED, not captured: every file under `mitv/` and `gtv34/` in this folder. Only the W1
capture files loaded from `fixtures/conformance/` are real TV output.

Replace the synthesized files with real captures at the I1 device session (runbook, W9).
