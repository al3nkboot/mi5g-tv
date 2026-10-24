# Mi TV AXSO0 capture, 2026-10-18 (internal)

Raw `adb shell` output from the reference TV (Xiaomi MiTV-AXSO0, codename dangal, Android TV 9,
SDK 28, 1 GB) taken after the hand debloat of 2026-10-18. Imported with
`scripts/import-mitv-capture.py`, which replaced the identifiers:

- `ro.serialno` and `ro.boot.serialno` read `FIXTURESERIAL0001` (so `serialLast4` is `0001`);
- `ro.oem.key1` and `bluetooth_name` read `REDACTED`;
- `android_id` reads sixteen zeros and `bluetooth_address` reads `00:00:00:00:00:00`;
- any MAC address or private IPv4 address would read `00:00:00:00:00:00` or `192.0.2.1` (none were present);
- the capture's `restore.sh` was not copied.

| File | Command it captured |
| :- | :- |
| `getprop.txt` | `getprop` |
| `packages-all.txt` | `pm list packages -f` |
| `packages-3rdparty.txt` | `pm list packages -3` |
| `packages-disabled.txt` | `pm list packages -d` before the debloat (empty) |
| `packages-disabled-after.txt` | `pm list packages -d` after the debloat |
| `settings-global.txt`, `settings-secure.txt`, `settings-system.txt` | `settings list global`, `secure`, `system` |
| `home-activity.txt` | `cmd package resolve-activity` with the brief flag, HOME category |
| `debloat.txt` | the hand-written list that was applied (upstream `debloat.txt` shape) |

`RedactionTest` in `:testing:fixtures` fails if a MAC, a private IPv4 address or a serial other
than the fixture serial appears anywhere under `fixtures/conformance/`.
