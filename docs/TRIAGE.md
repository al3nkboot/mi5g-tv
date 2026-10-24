# Triage: reading and answering reports

For maintainers. Most people who open an issue here are not developers; many are setting up a TV
for a parent. They took the time to tell us something. The job is to thank them, explain in plain
words, give them a way forward today, and say what happens next. Then come back when it ships.

## Labels

| Label | When |
| :- | :- |
| `bug` | Set by the "Report a problem" form. Something in the app went wrong. |
| `tv-profile` | Set by the "Help add my TV" form. A TV with no exact profile. |
| `needs-info` | We asked a question and are waiting. Close after 30 days without an answer, kindly, and say it can be reopened. |
| `safety` | The TV lost something it needs (Wi-Fi, input, remote, HDMI, updates, boot) or could not be put back. Look at it first, the same day if possible. |
| `stuck` | The person cannot finish or undo a setup. Give the way out in the first reply. |
| `upstream` | The fix belongs in the Clean Your TV launcher or its lists ([Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv)); link the upstream issue or pull request. |
| `fixed-next` | Fixed on main, not released yet. Swap for a release comment when it ships. |
| `duplicate` | Point to the other issue and thank them: a second report of the same thing is still useful. |

## Reading a report from the app

Both forms ask for the report the app copies. It is plain text with a fixed shape:

- `App:` and `Phone:` lines: the version and build (`full` is GitHub and F-Droid, `play` is
  Google Play) and the phone.
- The `TV` block: brand, maker, model, device codename, Android release and SDK, build fingerprint,
  and the home screen it came with (the app's guess from the TV's packages).
- `Matching`: how the profile resolved (`exact <id>`, `brand set <Brand>` for the careful fallback,
  or `none`) and the home screen switch decision (`planned`, or `blocked <REASON>` with the
  `HomeBlock` name from `SetupMachine`, for example `WIZARD_STAYS_ON`).
- `Home screen`: what HOME resolves to and every app that claims it, by priority.
- For a TV report, the system apps with their state (`enabled`, `default`, `disabled-user`,
  `disabled`, and `switched off by this app`), and a count of the person's own apps.
- For a problem report, the log after the TV summary. The lines to look for:
  - `setup: probe done ... profile <how>, tv <brand> <model> (<device>) sdk <n>`
  - `setup: home switch planned` or `setup: home switch blocked <REASON>`
  - `setup: run paused <REASON> at step <n>: step failed: <Op> <package>: <code> <stderr head>`;
    `undo step failed` means step R (putting the TV's own home screen back) failed.
  - Error codes are fixed words: `nonzero_exit(<exit>)`, `lost`, `timeout(<phase>)`, `refused`,
    `unreachable`, `protected`, `install_failed(<code>)`, `no_space`, `auth_denied`, and so on
    (`LogCodes` in `:core:flows`).

Never ask anyone to post their serial number, IP address, Wi-Fi name or a pairing code. If a report
has one anyway, edit it out of the issue and tell them you did, without fuss.

## From a TV report to a list upstream

Lists live upstream, in `examples/<brand>-<model>/debloat.txt` of
[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv), so they help everyone, with or
without this app. The steps:

1. **Find the family.** Search upstream `examples/` for the brand and the device codename from the
   report (`Device:`); a list for the same codename or the same chassis is the best start. Check
   the profile meta in `core/spec/src/main/resources/tv/cleanyour/core/spec/profile-meta.json` for
   a match that almost fits (another SDK, another model name).
2. **Draft the list.** From the report's system apps: adverts, tracking, shop and demo apps, and
   brand extras nobody asked for. Never anything on the protected list (`Denylist` in `:core:ops`):
   Play services, the Play Store, Wi-Fi, Bluetooth, the inputs and Live TV, the IME, accessibility,
   the setup wizard unless the TV is known to need it gone. When unsure, leave it out.
3. **Write the header** in the upstream format (see any `examples/*/debloat.txt`): model, Android
   version and firmware build from the report (`Build:`), the stock launcher (`Resolves to:`), what
   was tested and for how long, what is kept on purpose and why, and the packages that were
   already off from the factory (`disabled` in the report).
4. **Ask the reporter to try it.** Put the list in a comment and ask them to live with it for a few
   days: Wi-Fi, the remote, every HDMI input, Live TV, the voice button, a restart. Offer the
   upstream `tools/disable.sh` and `tools/restore.sh` route for people with a computer; for
   everyone else, the next app build with the profile.
5. **Send it upstream** once it held: a pull request to Gathin23/cleanyourtv with the folder and a
   row in `examples/README.md`, crediting the reporter if they agree. Label this issue `upstream`
   and link the pull request.
6. **Bring it into the app** after the upstream merge: move the `third_party/cleanyourtv` submodule
   and add the profile meta entry (CONTRIBUTING.md, "Propose a TV profile"). Label `fixed-next`.
7. **Close the loop** when the release is out: a comment with the version and what changes for
   them, then close.

## Reply starters

Change them to fit; keep them short and warm. Thank, explain, give a workaround, say what happens
next.

**A new TV (careful mode did little).**

> Thank you for the report, and for offering to test. Your TV is one we haven't met yet, so the
> app was careful: it only switched off what is safe on every TV of this brand, and it kept the TV's
> own home screen because on unknown TVs the switch can go wrong. That is why it looked like it
> did nothing. Nothing on your TV is broken.
>
> What happens next: we'll make a list for this TV from your report and post it here for you to
> try. When it holds, it goes into the app and into the Clean Your TV lists, and we'll tell you
> here which version has it.

**A TV report is missing.**

> Thank you for writing. To make a list for this TV we need to see which apps it came with. The
> app can do this for you: open the TV in the app, tap **Help add this TV**, then **Share** and
> choose Copy, and paste the report into a comment here. It shows you everything before you post it,
> and it never includes your serial number, addresses or Wi-Fi name.

**Stuck: the setup didn't finish.**

> Thank you, and sorry for the trouble. The app stopped partway through and is waiting for you to
> carry on or undo. Your TV is fine as it is. If the buttons keep bringing you back to the same card,
> here is a way out today: <workaround for this case>. We're fixing the cause in <version>; after
> the update the app sorts this out by itself, and we'll let you know here when it is out.

**Something stopped working on the TV.**

> Thank you for telling us, and sorry. To put everything back right away: in the app, open the TV,
> then **Restore point**, **Undo everything**. Without the phone, the restore copy ("Save a copy")
> has scripts that run from any computer with adb. Could you tell us what stopped working, and
> paste the report from **Report a problem**? We'll find which app it was and keep it on for
> everyone with this TV.

**The fix is released.**

> This is fixed in <version>, out now on <GitHub, F-Droid, Google Play>. <One sentence on what
> changes for them.> Thank you again for reporting it; it helps everyone with this TV.
