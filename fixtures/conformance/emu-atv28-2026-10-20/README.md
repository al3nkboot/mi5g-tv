# Android TV emulator capture, 2026-10-20 (internal)

Read-only `adb shell` output from the forge emulator session's TV (`cytemu-tv`: the API 28
`android-tv;x86` system image, model `sdk_google_atv_x86`, brand `google`, 1 GB guest), taken on a
fresh boot with nothing disabled. It is the TV of the emulator runs (DECISIONS-W8 D-W8-18 to 22 and
round 3): an unknown model, so Simple finds nothing it is sure about and the only switch-off is the
TV's own home screen inside the home screen switch.

Each file is one of the probe's read-only lines (`ProbeScripts`), run on its own; the batched
scripts join them with their marker lines in `EmulatorCapture.recording()` (`:testing:fixtures`).
Identifiers replaced before the files were written:

- `ro.serialno`, `ro.boot.serialno` and `ro.kernel.androidboot.serialno` read `FIXTURESERIAL0001`;
- `android_id` reads sixteen zeros;
- no MAC address, private IPv4 address or Wi-Fi network name was in the output.

| File | Command it captured |
| :- | :- |
| `getprop.txt` | `getprop` |
| `meminfo.txt` | `cat /proc/meminfo` |
| `gms-path.txt` | `pm path com.google.android.gms` |
| `packages-all.txt` | `pm list packages -f` |
| `packages-3rdparty.txt` | `pm list packages -3` (empty) |
| `packages-disabled.txt` | `pm list packages -d` (empty) |
| `dumpsys-package-packages.txt` | `dumpsys package packages`, through the probe's own grep |
| `query-home.txt` | `cmd package query-activities --brief` for HOME |
| `home-activity.txt` | `cmd package resolve-activity --brief` for HOME |
| `query-leanback.txt`, `query-launcher.txt` | `cmd package query-activities --brief` for LEANBACK_LAUNCHER and LAUNCHER |
| `tv_input.txt` | `dumpsys tv_input` |
| `hdmi_control.txt` | `dumpsys hdmi_control` (empty: the emulator has no such service) |
| `settings-secure.txt`, `settings-global.txt` | `settings list secure`, `settings list global` |

`RedactionTest` in `:testing:fixtures` covers this folder like the Mi TV capture.
