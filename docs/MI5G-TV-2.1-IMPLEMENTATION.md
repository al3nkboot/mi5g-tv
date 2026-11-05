# Mi5G TV 2.1 development build

Version: `2.1.0-dev` / 23. Bundled launcher: `com.gathin.tvhome`, `2.7-mi5g` / 9.

The interrupted changes were reviewed and retained where sound, then completed and corrected. New implementations are independent; no source from the prohibited v3/GPL/closed-source projects was copied. The TechPP/talkies MIT notice remains in THIRD-PARTY-NOTICES.

## Item status

“Implemented” describes code plus JVM verification, not successful real-TV testing.

| Item | Status | Scope |
|---|---|---|
| 1 | Implemented | HDMI device-on card, disable and CEC-only options |
| 2 | Implemented | TV and token QR input rename/hide including Live TV |
| 3 | Implemented | Two-line phone-entered screensaver message |
| 4 | Implemented | Quiet hours, default 23:00–07:00, presets/off and phone configuration |
| 5 | Implemented | Open-Meteo → MET Norway; weekly removable IP location fallback with explicit/prayer precedence |
| 6 | Implemented | Frame-timed Auto / Full / Lite effects |
| 7 | Implemented | Maker-extras hidden list; protected TCL art and core packages |
| 8 | Implemented | Update-install fallback, explicit launcher return and interface-prefix subnet restrictions |
| 9 | Implemented | Phone update subtitle and GitHub temporary-limit state |
| 10 | Implemented | Minute and screen-on clock redraw |
| 11 | Implemented | Explicit Live TV passthrough handling without chooser |
| 12 | Implemented | Strict shell package-name validation and injection regression tests |
| 13 | Implemented | Safely resolved Xiaomi picture settings with Android fallback |
| 14 | Implemented | Remote ignore action |
| 15 | Implemented | Arabic mirrored top-bar D-pad navigation regression |
| 16 | Partial packaging | Shafi-default / Hanafi standard Asr calculation settings |
| 17 | Implemented | Android 8 MemAvailable fallback |
| 18 | Implemented | Opt-in Watch Next / real-progress preview row and title dedupe |
| 19 | Implemented | Opt-in package-only accessibility Home fallback with Arabic disclosure |
| 20 | Implemented | Persisted 4–6 Home columns and icon sizes |
| 21 | Implemented | Bounded bidirectional token/subnet-gated QR file transfer, no auto-install |

### Item 16 packaging caveat

Phone settings send the Hanafi flag supported by the existing prayer engine. The TV-local Asr selector is implemented and tested in the separately built prayer companion v6 (`Mi5G-Prayer-2.1.apk`). The phone APK's existing signed module seed catalog still points to prayer v5: it was deliberately not re-signed or published in this work. Install the separately supplied prayer companion to use its new TV-local selector. Thus the module catalog integration is not complete.

### Platform permission correction

AOSP TvProvider declares READ_TV_LISTINGS **dangerous**, not normal:
https://android.googlesource.com/platform/packages/providers/TvProvider/+/refs/heads/main/AndroidManifest.xml
The opt-in Continue Watching setting requests it when denied and remains off if consent is refused. Providers/OEMs may expose different subsets or no rows. No signature-only permissions are requested for this feature.

## Verification and artifacts

- JDK 21; Android SDK `/home/box/android-sdk`; no emulator or KVM.
- Launcher: `testDebugUnitTest assembleRelease`, 178 tests, zero failures/errors/skips.
- Root: `test assembleFullDebug :tv:prayer:assembleRelease`, 2125 tests, zero failures/errors (final verification recorded in `/workspace/out/mi5g-tv-2.1/shipping-verified.log`). One real-TV test requires an actual TV IP and is deliberately skipped.
- Phone update screenshots additionally recorded with `:feature:manage:recordRoborazziDebug --tests '*UpdateItem202ScreenshotTest*'`.
- Launcher release is signed with the existing Mi5G release certificate, SHA-256 `c1f317965f154fe5e79fe7483abd5dd5dec7df57d87f3a645a5fd12f39a5480c`; asset hash/size/version and detached signing block are refreshed.
- Main output: `/workspace/out/mi5g-tv-2.1/Mi5G-TV-2.1-debug.apk`.
- Launcher output: `/workspace/out/mi5g-tv-2.1/cleanyourtv-launcher-2.7-mi5g.apk`.

## Arabic screenshots

Directory: `/workspace/out/mi5g-tv-2.1/screens/`

- `asr_option_ar.png`
- `continue_watching_ar.png`
- `device_on_card_ar.png`
- `edit_inputs_ar.png`
- `grid_settings_ar.png`
- `hidden_apps_ar.png`
- `phone_update_item_ar.png`
- `phone_update_rate_limited_ar.png`
- `qr_file_transfer_tv_ar.png`
- `screensaver_message_quiet_ar.png`
- `update_install_failed_ar.png`
- `weather_location_ar.png`

These are Robolectric captures of actual Android views/Compose with deterministic fixtures. Actual phone-browser QR **HTML** message and file-transfer screenshots are missing: Robolectric does not render Chromium/WebView content, and browser capture tools were unavailable. The actual HTML/API paths are implemented; HTTP token, message update, upload-size, legacy Downloads upload/download and partial-upload rollback tests exercise the server. The TV-side QR transfer screenshot is not a substitute for the missing phone HTML captures.

## Still untested

All real-hardware behavior: HDMI/CEC signals and port accuracy, Live TV intent routing, standby clock recovery, Xiaomi settings, frame timing on slow TVs, accessibility/OEM Home behavior, update return/install restrictions, real provider listings, real external-network weather responses, real Wi-Fi subnet interfaces, MediaStore on TV firmware, phone browser file transfer and physical remote focus. There is no independent reviewer/subagent available; review was local. Codex was unavailable because its saved OAuth token was revoked; no credential workaround was attempted.

No tag, release, merge to main, public catalog/index upload, APK auto-install, Play Protect change, or Mi5G Check change was performed. Private signing material remains outside the repository.
