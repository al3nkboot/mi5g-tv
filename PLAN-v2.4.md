# Mi5G TV — update plan (v2.4.x)

Started 2026-11-17 on branch `feature/v2.4` (from `main` = 2.3.0, versionCode 27).
The user's go-ahead: «ابدا العمل» (Nov 17, 2026). Test builds only: no push to main, no tag, no release.

Rules for every milestone
- UrTV, CleanYourTV 3.3–3.5 ideas, SUL, Teevy, bdran, action-center-tv (GPL-3.0): **ideas only, never code**.
- aerial-tv and talkies (MIT), tvOS-Launcher (Apache-2.0): code may be reused **with credit** in About and THIRD-PARTY-NOTICES.
- No Apple branding (no tvOS / Liquid Glass / AirPlay logos), no illegal streams or IPTV, sports data from legal/official sources only.
- Arabic first, RTL, same Material/TV design as the current app. No secrets in the repo.
- Forced adhan in the prayer module is never removed or hidden.
- Code: Antigravity (agy) writes it, OpenCode is the backup; Grok Bot reviews, builds and tests.

Backlog numbering follows the Mi5G TV backlog (37 items, Nov 17, 2026) plus the older approved user reports.
Items 7–14 are tracked in the bot's backlog record; where their exact text was not recoverable in this
run they are listed under the milestone their topic fits and must be confirmed before that milestone starts.

---

## M1 — 2.4.0: stability fixes and quick wins  ← this run
Bug fixes from user reports and audits
- Launcher `MainActivity` leak (receivers, handlers, static Activity/View references released in onPause/onDestroy).
- Modules «add» always says why it stopped (1.6.1, commit 6f49000) — already in main since 2.0; re-verified.
- Sony Bravia (KD-55X9000F, @Tawaq007): onboarding Start blocked by tap-jacking protection when an overlay app is on the phone → Start and other non-destructive buttons no longer guarded; the obscured hint names the usual culprits; destructive steps stay guarded.
- Xiaomi TVs / Mi Box S 3rd Gen (@thedarknight56, @mohammedkhalilk): Home reverting to PatchWall/stock → Keep Alive (idea from tvOS-Launcher, Apache-2.0): re-assert the home activity and the Home-key helper after Home presses, boot, standby and package updates; one-tap «Re-apply home» from the phone; safe one-tap restore kept.
- Clear error when the TV can't be reached on every path (connect, modules, remote, tools) — CleanYourTV 3.3 idea.
- Core package protection: never-touch list for Chromecast built-in, voice assistant, AirPlay-type receivers, Settings, Play Store, package installer, SDK sandbox (already protected) — v1.7 item 4 + CleanYourTV 3.3 idea.
- Revoke `WRITE_SECURE_SETTINGS` from the launcher when no feature needs it and on undo/restore — CleanYourTV 3.3 idea.
- Chromecast «paired but nothing installs» (@ibRa4SA): install failures show the real `pm` reason (storage, wrong user, blocked installer) instead of a generic error.
- Quick-start guide (@Q8_850): short in-app steps on the first screen + a link for the tutorial video (video recorded by the user later).
Quick wins
- 35) Home lock (aerial-tv, MIT): extend the 2.3 lock to the dock, library, hide and arrange-from-phone; shows a short «locked» note.
- 36) Caffeine mode (tvOS-Launcher, Apache-2.0): keep the screen awake for 1 h / 2 h / 4 h / until turned off, from Mi5G settings and the Control Center.

## M2 — 2.5.0: home screen and launcher polish
- 1) Crash safe mode · Arabic app names (v1.7 #3) · 6) clean/minimal mode · 21) hide an app without disabling it (Settings/Play Store always visible) · 22) Play Store button + Play Store always on home · 23) restore Mi5G TV as home after system updates (Philips) and keep Home-button mode after standby · 24) dock with 1–3 rows and row navigation + name tooltips · 25) All-apps popup grid with search · 28) optional navigation/click sounds (our own) · icon/card size small/medium/large (@atalzahmi) · 5-minute safety switch back to stock home (v1.7 item 1) · stop Play Store running in the background (v1.7 item 5).

## M3 — 2.6.0: phone companion and remote
- 15) QR smart setup wizard · 17) APK installer by Downloader code or URL + mini store · 18) file manager with QR send/receive on LAN · 19) upload up to 20 screensaver/wallpaper images or videos with "saved on TV" confirmation · 20) multiple TVs in the remote · 32) live phone message or drawing on the TV (UrTV idea) · Wireless-debugging pairing code inside the app (Android 11–14) · 4) one search box across installed apps.

## M4 — 2.7.0: screensaver, schedules and overlays
- 5) Islamic info cards on the screensaver (off by default) · 16) countdowns to Ramadan, Eid, Hajj and Gulf national days · 30) timed task reminders that wait for the adhan (UrTV idea) · 34) weekly wake/sleep schedule (CleanYourTV 3.5 idea) · 26) hold OK to move/hide home widgets + news, movies and sports widgets · 27) HDMI inputs manager overlay · 11) control panel overlay + recent apps switcher (tvOS-Launcher) · 12) debloat profiles with auto model detection linked to App Manager.

## M5 — 2.8.0: prayer module 2 (Mawaqiti engine)
- Rebuild the TV prayer app on the مواقيتي engine, settings synced from the phone over ADB, full adhan over any app, forced adhan kept, pre-adhan banner, auto-start + Keep Alive, optional floating overlay (next prayer + countdown, position/size/opacity; overlay permission over ADB) — mockups first, per-device testing. Per-prayer minute offsets and a separate Fajr adhan.

## M6 — 2.9.0: football
- 33) football home: today's matches, kick-off reminders, prayer-clash notes, big tournaments — legal/official data only, no streams (UrTV idea) · 31) pin a live score corner card over any app (talkies, MIT) · favourite clubs.

## M7 — 3.0.0: family and profiles
- 37) family games + a quiz answered from the phone (UrTV idea, own content) · 3) kids profile with allowed apps and a simple lock · 2) apps ordered by time-of-day usage · 29) advanced settings from tvOS-Launcher 2.2.0 (ideas only).

Each milestone ends with: debug APK + screenshots to the user, no publishing until the user says so.
