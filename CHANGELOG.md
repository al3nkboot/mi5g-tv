# Changelog

All notable changes to the app. Versions follow [semantic versioning](https://semver.org/).

## 0.3.3 (2026-10-24)

Fixes from the first public day of issues. Thank you to everyone who reported.

### Setup
- A switch-off the TV answers "yes" to but keeps on is now skipped, and the rest of the clean-up
  runs. Before, Carry on met the same step again every time (issue #10, PatchWall on a Xiaomi TV
  Box S with Android 14). Done names what wouldn't switch off.
- The app and the TV now check the same rule before anything is switched off, so the plan can no
  longer include a step the app itself would then refuse.
- A launcher you installed yourself is never switched off. If the TV's home screen was already
  changed, by another app or an earlier change, Setup leaves it as it is and does the clean-up.
  Before, such a TV got "plan refused" on every try (issue #8).
- On a TV without an exact list, the TV's own home screen now stays switched on during the new
  home screen switch when it is a protected kind of app (one the TV keeps running, its keyboard or
  its screensaver), instead of being switched off.
- When there was nothing to change, Done now says "Nothing was changed" instead of "Your TV is
  ready" (issue #15).
- On a TV the app has no list for, the setup wizard note no longer suggests re-adding the Wi-Fi,
  which would not help there.

### Manage
- Home screen on a TV without the new home screen says why Setup left the TV's own, and what
  would change it, instead of offering a dock and a look that ended in "We stopped" (issue #3).

### Reports
- Help add this TV and Report a problem now fill the GitHub form in for you: the report is part of
  the link when it fits (all the real reports so far do), and is still copied as a fallback. The
  form's report box can no longer be posted empty (issues #12, #14, #16).
- The TV report lists the apps in short groups, so it fits the form.
- Build numbers such as "V816.0.11.0" are no longer mistaken for network addresses and masked.
- Privacy policy: says that when the report is part of the form's link, your browser sends it to
  GitHub as it opens the form. Nothing is posted until you post it.

## 0.3.2 (2026-10-23)

Fixes from the first two issues people reported, and an easy way to report a problem or a new TV.

### Fire TV and undo
- Fire TV sets (and Fire TV Edition TVs from Insignia, Toshiba and others) are now recognised
  from the TV's own details, even when the app has no list for the exact model. They get the
  clean-up only: Fire OS does not let another home screen take over, so the app no longer tries.
  China-market TCL and FFalcon sets without Google services are treated the same way.
- Undo now checks the TV itself. If the TV's own home screen is back, undo finishes, even when
  the TV refuses a step whose result is already in place. Before, a Fire TV could refuse to
  "switch Amazon's home screen back on" although it was never off, and every button (Carry on,
  Undo, Stop here) ran into the same refusal again.
- If you were stuck on "The setup didn't finish" with 0.3.1, it clears the next time the app
  connects to the TV after updating.
- Stop here shows Done only once the run has really stopped, and "Forget this TV" ends an
  unfinished setup for that TV straight away. While setup is still working on a TV, Forget this
  TV and Delete everything ask you to wait or stop it first.
- A TV whose home screen the app does not recognise gets the clean-up only, instead of an error.

### Reporting
- **Help add this TV**: when the app has no list for your TV, one tap makes a short report of
  what the TV is and which apps it came with (apps you installed yourself are only counted, never
  named). You see the whole text first, then copy it into a pre-filled GitHub form or share it
  another way. The app sends nothing itself.
- **Report a problem**, in Settings and on the screens where something went wrong, does the same
  with the redacted log.
- The log is readable: plain error names, how the TV was matched, the home screen decision, and
  the step that failed with the TV's answer.
- "Share diagnostics" said it made a zip; it shares plain text, and the screens now say so.
- The TV report and the problem report never name a home screen app you installed; only the
  TV's own and the app's are named.
- Delete everything now deletes the app's diagnostics log too.
- Privacy policy: the diagnostics section is now "Reports you choose to share", and "What stays
  on your phone" lists the diagnostics log. Nothing new is collected and nothing is sent by the
  app.
- Friendlier issue forms that the app can fill in, and a "Reporting a problem or a new TV"
  section in CONTRIBUTING.

## 0.3.1 (2026-10-23)

A small update for F-Droid's inclusion rules. Nothing changes in how the app cleans a TV.

- Where add-ons are offered (Cards and modules, and Done's "Add a card"), each add-on's page now
  says, before you add it, that add-ons are downloaded from their authors' GitHub releases,
  outside any app store, and are not built or checked by F-Droid. Back leaves without
  downloading anything, as before.
- The store description names the app's internet connections: the version from GitHub and
  F-Droid contacts GitHub only when you tap "Check for updates" or choose to browse and add
  add-ons; the Google Play version makes none of its own. The short summary follows F-Droid's
  style.
- The README says how the app is made: with AI coding agents, under the maintainer's review.
- The app's page and privacy policy moved to https://cleanyour.tv/app/android and
  https://cleanyour.tv/app/android/privacy.

## 0.3.0 (2026-10-24)

A Google Play version of the app, F-Droid readiness, and a website with a privacy policy. Every
channel gets the same app, built from the same code and signed with the same key. On the GitHub
build nothing changes in use.

### Google Play
- A Google Play build of the app, for people who would rather install from the Play Store than
  download an APK (Google Play lists it once its review is done). It has the same app ID, version
  and signing key as the GitHub download.
- The Google Play build is updated by Google Play, so it has no "Check for updates". It downloads
  no add-ons (Cards and modules and Done's "Add a card" are not shown), makes no internet request
  of its own, and always keeps the TV's software updaters on: they are listed under "We keep
  these" instead of being a choice in Customise, and Switched off has no Off choice for them. One
  that an earlier GitHub build switched off is listed in Switched off, where it can be put back.
  Everything else is the same.

### F-Droid
- F-Droid can build the app from source and get exactly the APK published on GitHub, so it can
  offer the same signed APK. The home screen launcher and the TV progress screen that the app
  bundles are rebuilt from source too, and still match the versions the app checks before it
  installs them on a TV.

### Website and privacy policy
- The app has a website, https://udhaykumarbala.github.io/cleanyourtv-app/: the download, the three
  steps, the supported TVs and how to install an APK.
- The privacy policy is at https://udhaykumarbala.github.io/cleanyourtv-app/privacy/ (and in
  PRIVACY.md): the app collects no data and has no accounts, analytics, advertising or crash
  reporting; everything it learns about a TV stays on the phone.

### One app, one key
- The GitHub, F-Droid and Google Play builds share the app ID `tv.cleanyour.app`, the version and
  the signing certificate, so an install from one channel can be updated from another and keeps
  what the app saved.
- One release now builds both reproducibly: the APK for GitHub and F-Droid and the bundle for
  Google Play, each built twice and compared byte for byte, and checked for the same permissions
  and, in the Play bundle, no GitHub host and no update check.

## 0.2.0 (2026-10-21)

Launcher 2.5 and 42 more TV lists (131 in all), with stricter safety: the home screen switch is
skipped up front on TVs where it could break casting, Wi-Fi or start-up, nothing that the TV
inputs, accessibility or the remote need is switched off by default, and the app says why when a TV
keeps its own home screen or an app stays on.

### Home screen launcher
- Bundles the Clean Your TV launcher 2.5 (upstream v2.5.0), built from source and signed with this
  project's launcher key. New on the TV: edit the dock from the remote, choose the rows under the
  dock and a wallpaper in Launcher settings, and more; every new launcher option is off by default.
- A TV set up with 0.1.0 is offered "Update the home screen" in Manage. The dock, the look and the
  card answers stay.
- The Home screen page says what happens to a dock changed on the TV: the phone cannot see it, a
  new look keeps it, and saving a different dock from the phone replaces it. A new look now sends
  the saved dock as it is, even when an app in it has gone from the TV, so the TV's own dock edit
  stays; a dock of more than 12 apps saved under Six columns keeps its first 12 in a four-tile look.
- The phone reads the settings keys launcher 2.5 added exactly as the launcher does.
- Never switches off an app that asks for the TV input hardware permission (the layer under the
  HDMI, AV and tuner inputs), even when a profile lists it and also during the home screen switch,
  where a TV's own home screen that asks for it stays on. The run goes on without it, and Done and
  Manage say it was kept because it runs the TV's HDMI and tuner inputs.
- After a look change, the launcher's remote button services come back on TVs that kept them
  unbound before (accessibility is switched off and on again around the list, as upstream does).
  Stopping a run in the middle of that step finishes it first, so the TV never stays without its
  accessibility services.
- When the home screen switch fails, Setup no longer names a TV's own fallback placeholder as
  another home screen app.

### TV lists
- Matches the TV against 131 community tested profiles from 46 brands, up from 89, from
  the upstream lists of launcher v2.5.0: 42 new ones, among them the first Fire OS 8 list (Fire
  TV Stick 4K, 2023), three more Fire TV devices, six more TCL sets, the Google TV Streamer,
  XGIMI MoGo 2 and MoGo 3 Pro, Xiaomi's China sets on PatchWall and HyperOS 3, Hisense and Vidda
  VIDAA sets, the Freebox Player POP, and first lists from AIWA, BYINTEK, Cecotec, Challenger,
  Compac, EKO, FFalcon, JVC, Kogan, Motorola, MTC, Polytron, SHARP, Tele System and Verizon.
- Sets where the home screen cannot be switched safely get the clean-up and keep their own home,
  and the "What we found" page says why: TVs with a vendor home the app does not know (Xiaomi
  PatchWall, VIDAA, OQEE, Verizon); the Kalley 43G200, the Kalley R10G and the PRISM+ Q65 Ultra,
  one family whose firmware brings the setup wizard back at every boot and has left a set stuck at
  start-up, also when no list matches the TV exactly; and Google's own Chromecast with Google TV and
  Google TV Streamer, where the switch would cost casting after every reboot.
- On TVs whose home screen switch needs the setup wizard off, the switch is skipped up front when
  the wizard saved the TV's Wi-Fi or the TV cannot say who did: switching the wizard off would
  forget the Wi-Fi, and left on, it takes over the Home button and can crash the TV (upstream
  #506). Setup does the clean-up and says how to get the new home screen later. A TV without a list
  gets the clean-up only whenever its setup wizard is on, since the switch would leave the wizard on
  with the TV's own home screen off.
- TCL Live, a TV input, is a choice on every TCL list instead of off by default, and the broadcast
  tuner inputs on the Sony BRAVIA AE2 and PRISM+ Q65 Ultra lists are always kept.
- TalkBack, the phone remote service and the remote's usage logger are a choice on every list that
  names them, never off by default, and a test keeps any accessibility, remote or pairing package
  from being off by default again.
- The app has its real icon, a glass TV, in place of the placeholder outline.
- The app takes its version from the installed APK, so Settings, diagnostics and "Check for
  updates" always name the version that runs.
- Corrections from upstream: the onn 4K box keeps Google's ambient mode so the Google Photos
  screensaver works, the Philips 65PUS8102 also switches off its reboot logger, and the TCL G07 and
  Xiaomi MSSP3 lists gained notes from more units. Home screen customizers that a TV service or
  the stock home's Inputs button relies on are now a choice rather than off by default on four
  more TVs.
- When two profiles match a TV equally, the one whose firmware fingerprint is named more exactly
  wins, so lists for neighbouring firmware builds each reach their own TVs.
- Sixteen upstream lists are not shipped, each with its reason in
  `core/spec/upstream-feedback/unconverted.txt`: duplicates of a shipped list, lists with nothing
  the app can match on, and a TCL China set whose network debugging needs a computer after every
  reboot.

## 0.1.0 (2026-10-20)

The first public release.

### Setup
- Finds Android TV, Google TV and Fire TV devices on the Wi-Fi and guides you through switching on
  network debugging with the remote, with steps per TV family.
- Reads the TV and matches it against 89 community tested profiles (27 brands), with a careful
  brand fallback for models nobody has profiled yet.
- Shows what it would switch off, grouped and counted, and what it keeps, before anything runs;
  Customise changes the list, the dock and the look, and shows every command in order.
- "Make it clean": saves a restore point, disables the clutter (never uninstalls), installs the
  Clean Your TV launcher 2.4 with a dock of the apps you use, and shows a progress animation on the
  TV while it works. A watchdog on the TV puts its own launcher back if the phone does not confirm
  the new home screen within 150 seconds.
- Asks for the phone's screen lock once per session before the first change; switches network
  debugging off again at the end unless you keep it on.

### Afterwards
- Manage: put back any switched off app, check for leftovers after a TV software update, change
  the home screen, cards and modules, undo everything from the restore point.
- Save a copy of the restore point as a zip with plain restore scripts that work from any computer
  with adb.
- Optional add-on apps for the TV from a signed list, checked by size, SHA-256 and signer before
  install.
- Settings: check for updates (one request to GitHub, on demand), share redacted diagnostics after
  a preview, licences.

### Under the hood
- No accounts, no analytics; internet only to GitHub and only on your action.
- Reproducible release build: two clean container builds byte identical; CI builds the same
  unsigned APK for every tag so the signed release can be checked against it.
- Signed with the release key, certificate SHA-256
  `eaf7af3867bb29ff4bb038854842c93f42ffe4198a087e611cb18f925201cd65`.
