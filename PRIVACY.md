# Privacy policy

Clean Your TV (the Android app, package `tv.cleanyour.app`) is a community-built phone app for
[Clean Your TV](https://github.com/Gathin23/cleanyourtv). It is published as open source by udhaykumarbala at [github.com/udhaykumarbala/cleanyourtv-app](https://github.com/udhaykumarbala/cleanyourtv-app).

This policy is published at [cleanyour.tv/app/android/privacy](https://cleanyour.tv/app/android/privacy).

Effective date: 2026-10-23.

## The short version

- The app does not collect your data. It has no accounts, no analytics, no advertising and no
  crash reporting service.
- Nothing about you, your phone or your TV is sent to us or to anyone else. We run no server for
  this app, so we could not receive it if it were.
- Everything the app learns about your TV stays on your phone.

## What the app talks to

**Your TV, on your home network.** To clean your TV the app connects to the TV you pick, over your
Wi-Fi, using Android's debugging connection (ADB, port 5555), and it listens for TVs announcing
themselves on the network (mDNS) so it can find yours. This traffic stays inside your home
network. The app reads the list of apps and the model of your TV, and it changes settings and
apps on the TV when you tell it to.

**GitHub, only in the GitHub and F-Droid versions, and only when you ask.** The version you
download from GitHub or F-Droid has three features that contact GitHub, and only when you use
them: "Check for updates" makes one request for the latest release, opening Cards and modules
fetches the signed list of add-ons, and an optional add-on downloads from its GitHub release when
you add it. GitHub sees the usual details of any web request (your IP
address, for example) under [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
The app sends no identifier of its own. The home screen launcher and the TV progress screen are
inside the app, so a normal clean-up makes no internet request at all.

**The Google Play version makes no internet requests to GitHub.** It has no update check and no
add-on downloads, because Google Play updates the app. A normal clean-up needs the internet for
nothing.

The app has no other internet connections: no advertising networks, analytics or tracking
services.

## What stays on your phone

- **The key your TV trusts.** The first time you connect, the app makes a key that the TV
  remembers as "this phone". It lives in the Android Keystore on your phone and never leaves it.
- **Restore points.** Before the first change the app saves a note of what your TV looked like, so
  everything can be put back. It stays on your phone. It leaves your phone only if you choose
  "Save a copy" and send the file somewhere yourself.
- **Your choices**, such as the dock and the look you picked, and the TVs you have set up.
- **The app's diagnostics log.** A plain text record of what the app did (connecting, reading the
  TV, each step and how it went), with network addresses, serial numbers, keys and pairing codes
  removed. It stays on your phone. "Delete everything" in the app's Settings deletes it, and so
  does clearing the app's storage. It leaves your phone only if you share a report.

You can delete all of this at any time from the app's Settings, or by clearing the app's storage
or uninstalling it in Android's settings.

## Reports you choose to share

"Report a problem" and "Help add this TV" are off until you use them, and the app sends nothing by
itself. Each one shows you the whole report first, as plain text, and then does only what you tap:

- **Share** opens Android's share sheet and you pick where it goes (a message, an email, a note).
- **Open GitHub** (or **Copy and open GitHub**) copies the report to your phone's clipboard and
  opens the GitHub form for it in your browser. When the report is short enough, it is also part of
  the form's link, so the form opens with it filled in; your browser sends that link to GitHub when
  it opens the page, and may keep it in its history. The app makes no request itself, and nothing
  is posted until you post the form. A GitHub issue is public.

Or you close the screen and nothing happens. "Report a problem" holds the app's own log with
network addresses, serial numbers, keys and pairing codes removed, and a short summary of the TV.
The log can name the apps the app switched off or tried to switch off or back on, which can
include an app you picked yourself in Customise. "Help add this TV" holds the TV's brand, model,
Android version and build, how the app matched it, and the apps the TV came with. In that report
and in the summary, apps you installed yourself are only counted, never named, and that includes a
home screen app you installed. Both name the app version and your phone's model and Android
version. Neither has the TV's serial number, its name or room, your network addresses or your
Wi-Fi name. This is the same in the Google Play version.

## Permissions

The app asks Android for: internet and network state (to reach the TV and, in the GitHub and
F-Droid versions, GitHub), Wi-Fi multicast (to find the TV), a foreground service while it works
on the TV, notifications (to tell you when it is done), and your screen lock (confirmed once
before the first change, so a stranger holding your unlocked phone cannot change your TV). It
does not ask for location, contacts, camera, microphone, photos or storage.

## Children

The app is made for adults who set up their own or their family's TV. It does not knowingly
collect anything from anyone, children included.

## Changes to this policy

If the app ever starts to collect or send data, this page changes first and the change is noted in
the [changelog](CHANGELOG.md). The history of this file is public in the repository.

## Contact

Questions or concerns: [open an issue](https://github.com/udhaykumarbala/cleanyourtv-app/issues)
on GitHub. For a security problem, please follow [SECURITY.md](SECURITY.md) instead.
