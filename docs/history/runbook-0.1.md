# Clean Your TV 0.1: release and real-device runbook (internal)

Owner: W9. Source of truth for the checks: PRODUCT-SPEC section 7.3 (the numbers in brackets,
like [7.3.9], point at its items). Every line is one tick. A line that cannot be ticked stops the
release until it is fixed or the R6 reviewer accepts it in writing as a residual risk.

Who does what: the user drives the phone and the TV; the W9 operator (or an agent beside the user)
reads the screens, runs the host commands and ticks. Nothing here is run against the user's TV or
phone except in the session itself, with the user present.

Fill in before the session:

| Field | Value |
| :- | :- |
| Date of the session | |
| Phone model and Android version | __________ (not known yet) |
| Phone has a screen lock (PIN, pattern, fingerprint) | yes / no |
| TV | Mi TV 4A, model AXSO0, Android 9, 1 GB |
| TV address on the LAN | from the router, not written into any file that leaves the maintainer's machine |
| APK file and its SHA-256 | from `dist/internal/<tag>/RELEASE-NOTES.md` |
| Signer certificate SHA-256 | from the same file |
| Tag and commit | |

## A. Before the session (build and provenance) [7.3.1 to 7.3.5]

- [ ] The tag exists on a commit of `main`; `pr.yml` is green on it [7.3.1].
- [ ] The nightly matrix is green on that commit: TV e2e API 28 and 34 with no PENDING scenario left, phone instrumented API 28 and 35 [7.3.1].
- [ ] `checkModuleGraph` green in that run [7.3.1].
- [ ] Dependency verification on: `gradle/verification-metadata.xml` is in the tree and the build used it (a build with a tampered cached jar fails, SECURITY 6.4 item 8; done once per release, see section E) [7.3.1].
- [ ] No prerelease artifact: `python3 scripts/dependency-report.py` lists only stable pins and `gradle/libs.versions.toml` has no alpha, beta or rc version [7.3.1].
- [ ] `scripts/build-internal-apk.sh <tag>` finished with exit 0 on forge [7.3.2].
- [ ] Its output says `identical: yes` (two clean container builds, byte identical) [7.3.2].
- [ ] Its output says `apksigcopier compare: match` [7.3.2].
- [ ] The SHA-256 it printed is copied into `RELEASE-NOTES.md` (the script writes it) and into the field above [7.3.2].
- [ ] `apksigner-verify.txt`: v2 true, v3 true, v1 false, one signer, the certificate SHA-256 is the app release key's [7.3.3].
- [ ] `check-apk.txt` ends with every check passed: package `tv.cleanyour.app`, `minSdkVersion 28`, `targetSdkVersion 36`, not debuggable, exactly the D16 permissions, one exported component, `allowBackup` false, no cleartext, a network security config [7.3.3, 7.3.5].
- [ ] The bundled launcher's sha256 and signer equal `LauncherPin` (the W5 CI assertion passed in `pr.yml` for the tag) [7.3.4].
- [ ] The seed index verifies against the shipped `keys.json` (W5 test green for the tag) [7.3.4].
- [ ] Key custody written down (section F below) and signed off by the user (Q1) [7.3.4].
- [ ] MobSF scan of the APK reviewed; findings attached to R6 [7.3.5].
- [ ] R8 mapping archived (`dist/internal/<tag>/mapping/`) [7.3.5].

## B. The user's phone (security checks before and during the session) [7.3.6]

Setup on the host (the maintainer's machine): `adb` connected to the phone over USB or wireless debugging, by the
user. The app is installed from the APK above: `adb install -r <apk>`.

- [ ] After install: `scripts/check-installed.sh` (or by hand: `adb shell dumpsys package tv.cleanyour.app`) shows the D16 permissions only, no `DEBUGGABLE`, no `ALLOW_BACKUP` flag.
- [ ] The installed APK equals the release: `adb shell pm path tv.cleanyour.app`, `adb pull` it, `shasum -a 256` equals the field above, and `python3 scripts/check-apk.py <pulled apk>` passes.
- [ ] Packet capture of the whole session (Welcome to Finish plus the Retro install), with PCAPdroid on the phone (no root, export pcap) or a capture on the Wi-Fi: the only peers are the TV and `github.com`, `api.github.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com` and the awesome repo's Pages host; no DNS lookup for anything else.
- [ ] mitmproxy with its CA installed as a user CA on the phone: every GitHub request of the app fails (index refresh shows the offline or error copy), nothing is downloaded through the proxy.
- [ ] `adb logcat -d > session.log` right after Finish, then `python3 scripts/check-redaction.py session.log --secret <TV serial> --secret <TV address> --secret <phone serial>` exits 0 (no tokens, keys, codes, serials or full IPs).
- [ ] The "Share diagnostics" zip and the "Save a copy" restore zip pass the same command.
- [ ] `adb backup -f app.ab tv.cleanyour.app` yields an empty or refused backup (no app data).
- [ ] A screenshot of the Allow screen and of "Reset my key" comes out black.
- [ ] An overlay PoC drawn over "Make it clean" does not trigger it (the tap is refused while obscured).

## C. The Mi TV session (the user drives, the operator ticks) [7.3.7 to 7.3.18]

Start state: the app freshly installed, no TV known, the TV on, on the same Wi-Fi, network
debugging on in Developer options. Keep a clock visible.

### C1. Find and connect [7.3.7]

- [ ] Welcome finds the Mi TV within 8 s (time from opening the app).
- [ ] "Start" leads to screen 2 with the Android TV 9 copy.
- [ ] The TV raises the Allow dialog; its fingerprint tail equals the one the phone shows ("ends in ..").
- [ ] "Always allow" ticked on the TV, then OK.
- [ ] The phone slides to S3 within 10 s of OK.

### C2. The plan [7.3.8]

- [ ] S3 shows the Mi TV profile's groups with their counts.
- [ ] "See the list" opens the sheet; the rows read well (friendly names, one-line descriptions).
- [ ] Flipping one pill updates the count on S3.
- [ ] "Change" on the Home screen row reorders the dock and the row updates.
- [ ] "Show commands" lists one line per op (spot-check three lines against the plan).

### C3. Make it clean [7.3.9]

- [ ] "Make it clean" raises the credential prompt exactly once ("Confirm it's you", "Before we change anything on Mi TV 4A").
- [ ] Narration runs; a screenshot (thumbnail) appears between groups.
- [ ] The Done count equals the number of completed steps in the journal ("Show details").
- [ ] A refused op, if any, appears as one line in the list, not as a stop card.

### C4. Takeover [7.3.10]

- [ ] The launcher installs through the guarded reinstall (D36: "Replace the home screen app ..." step shown, once).
- [ ] S4b shows the live thumbnail with the new home screen.
- [ ] Pressing Home on the remote flips the live check.
- [ ] "Yes, I see it": afterwards `ps -A | grep cleanyourtv-rollback` on the TV (from the operator's host, through the app's "Show details" or a laptop) shows nothing, and `/data/local/tmp/cleanyourtv/home-ok-<token>` exists.
- [ ] Manage, "Use the TV's own home screen": the stock home is back.
- [ ] "Clean it up again", then on the check screen "No, put it back": the stock home is back within 10 s.
- [ ] "Clean it up again", then let the timer run out: the stock home is back within 10 s of the timeout.
- [ ] "Clean it up again", then airplane mode on the phone right after the home switch: the stock home is back within 120 s (the TV watchdog alone).

### C5. Theme [7.3.11]

- [ ] Aurora: the wallpaper and the navy background show on the TV.
- [ ] Default: resets the look.
- [ ] A hand-edited `config.json` on the TV (operator edits one key) makes the drift banner appear on the next connect.
- [ ] "Restore mine" puts the app's config back and the banner goes away.

### C6. Retro module [7.3.12]

- [ ] Index refresh works; Retro v0.1.0 detail shows.
- [ ] Install; the TV prompt's 12 hex characters equal the phone's ("6966 ed15 1f52" style, D1).
- [ ] "Show card" on the TV is detected by the phone; the card is on the home screen.
- [ ] "Remove from TV" removes it.
- [ ] With the phone offline, the stale-index copy appears.

### C7. Restore [7.3.13]

- [ ] "Put back" one package; the snackbar's Undo works.
- [ ] Operator runs `pm enable` for one switched-off package on the TV; "Check for leftovers" finds and fixes it.
- [ ] "Save a copy" writes the zip; its `restore.sh` is readable and names the right packages.
- [ ] "Save a restore note" opens the share sheet with the right names.
- [ ] `/sdcard/cleanyourtv/restore.txt` exists on the TV.

### C8. Resilience [7.3.14]

- [ ] Wi-Fi off on the phone mid-run, then on: "Reconnecting", then "Carry on" finishes the run.
- [ ] App swiped away mid-run, reopened: the resume prompt shows the right counts.
- [ ] Phone screen off during a push: the push completes.
- [ ] TV powered off mid-run: a stop card; power on, resume works.
- [ ] DHCP address change of the TV (router reservation changed by the user): the app reconnects by name.

### C9. Finish [7.3.15]

- [ ] "Switching debugging off... done".
- [ ] Port 5555 refuses: from the host, `adb connect <TV address>:5555` fails.
- [ ] Manage chip reads "Debugging off".
- [ ] Turning debugging on again on the TV: the returning step M1a continues on its own.

### C10. Undo everything [7.3.16]

- [ ] Preview shows the full list.
- [ ] The outlined red button, then the credential prompt.
- [ ] Narration; the stock home is back.
- [ ] Every package re-enabled (`pm list packages -d` on the TV matches the baseline's disabled list).
- [ ] The Manage card reads "Original".

### C11. Accessibility [7.3.17]

- [ ] TalkBack over screens 1 to 5: headings announced, the live region reads progress, the countdown announces at 60, 30 and 10.
- [ ] 200% font scale: nothing clipped on screens 1 to 5.
- [ ] Light theme pass.

### C12. Forget and reset [7.3.18]

- [ ] "Forget this TV" and "Delete everything" leave no files (`adb shell run-as` is not available on a release build, so check with "Delete everything" then the app's storage size in Settings > Apps: 0 data).
- [ ] "Reset my key" makes the TV ask Allow again on the next connect.

## D. Sign-off

- [ ] R6 (`SECURITY-REVIEW-0.1.md`) written, with the evidence above attached.
- [ ] The user's go on Q1, Q3, Q4 recorded.
- [ ] The APK and its SHA-256 in `dist/` with the release notes (internal).

## E. Rehearsal record (W9, before the user's session)

The same flow, run by W9 against the emulator and the fake adbd, so the session above meets no
surprise from the harness side.

| Date | What ran | Result |
| :- | :- | :- |
| 2026-10-19 | `scripts/emulator-on-forge.sh run 28`: API 28 Android TV emulator in a 3 GB container on forge under the build lock | S00 harness smoke 4 of 4 passed, 17 scenarios PENDING on W2 to W5 (DECISIONS-W9.md, Evidence) |
| 2026-10-19 | `scripts/build-internal-apk.sh <throwaway tag> --test-key` | two clean builds identical, apksigcopier match, sha256 printed; check-apk 12 of 15 (W8 manifest pending) |
| 2026-10-19 | Dependency verification tamper test | build failed on the tampered hash, as it must |
| (at I1 to I3) | The full suite with W2 to W5 merged (no PENDING), then the app on a phone emulator against the fake adbd persona | |

Dependency verification tamper test (section A): change one byte of a cached jar in a scratch
Gradle home, run `./gradlew help --offline`: the build must fail with a verification error.

## F. Key custody (Q1)

| Key | Where it lives | Who can use it | Backup |
| :- | :- | :- | :- |
| App release key (`cleanyourtv-release`) | the maintainer's offline key store only; never in CI (superseded 2026-10-20: see the note below) | the maintainer; `scripts/release-on-forge.sh` signs on the maintainer's machine, the key never reaches the build host | offline copy chosen by the user |
| Launcher signing key (Q1) | W5 records it | | |
| Index root and signing keys (minisign) | W5 records them | | |
| `SHA256SUMS` minisign key | GitHub `release` environment secret | release.yml | offline copy |

The app release key does not exist yet. The user generates it offline (W9 never does):

```
keytool -genkeypair -keystore release.p12 -storetype PKCS12 -alias cleanyourtv \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Clean Your TV"
```

and keeps the password in a one line file next to it, both in the maintainer's key store with
mode 600.

Note (2026-10-20, release prep for 0.1.0): the key was generated as above; CI never signs.
`scripts/release-on-forge.sh` replaced `scripts/build-internal-apk.sh` and `sign-and-compare.sh`:
it builds twice on the build host, signs on the maintainer's machine and checks the result with
`scripts/verify-release.sh`; the expected signer lives in `docs/release-signer.sha256`. The
`SHA256SUMS` minisign key and the `release` environment secrets below are not used.

- [ ] The certificate digest (`keytool -list -v -keystore release.p12` prompts for the password;
  take the SHA256 line without colons) is exported as `CYT_RELEASE_CERT_SHA256` for
  `scripts/build-internal-apk.sh` and set as the `release` environment variable
  `RELEASE_CERT_SHA256`, so `check-apk.py` compares the signer with it.
- [ ] Before the first CI release: the `release` environment has a required reviewer with
  "prevent self-review" and deployment tags limited to `v*`, and a tag ruleset lets only the
  maintainer create, move or delete `v*` tags.
