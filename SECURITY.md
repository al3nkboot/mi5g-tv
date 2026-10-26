# Security policy

Clean Your TV changes a TV over its debugging connection, so we take reports seriously and
answer them first.

## Reporting a vulnerability

Please report privately through GitHub:
[Report a vulnerability](https://github.com/udhaykumarbala/cleanyourtv-app/security/advisories/new)
(Security tab, "Report a vulnerability"). Do not open a public issue, pull request or discussion
for a security problem.

Useful to include: the app version (shown in the app settings), the phone and TV models with their Android
versions, what an attacker can do and from where (same Wi-Fi, the TV, a malicious app on the
phone, the network path to GitHub), and the steps to reproduce. Redact your TV's serial number
and your network addresses; we never need them.

What happens next:

- We confirm we have it within 7 days.
- We agree on a fix and a disclosure date with you; 90 days is the default, sooner when a fix
  ships sooner.
- Fixes ship as a new signed release; the advisory credits you unless you prefer otherwise.

## Scope

In scope: this app and everything in this repository, including the release pipeline, the
signed module index and its keys, the bundled launcher and TV curtain as this app pins and
installs them, and the restore scripts it writes.

Problems in the launcher itself (the TV home screen) belong to the upstream project,
[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv); report them there privately.

## Supported versions

Only the latest release gets security fixes. Android requires a matching signing identity or a
valid supported signing-certificate lineage for an in-place update. The in-app updater applies a
stricter check: it refuses an APK whose signer differs from the installed app. The repository's
release certificate SHA-256 is recorded in [docs/release-signer.sha256](docs/release-signer.sha256).

In the GitHub and F-Droid builds, the in-app updater checks only stable releases. It downloads an
APK only after the user asks, requires GitHub's published SHA-256, and checks the package name,
version, and signing certificate against the installed app before opening Android's installer.
Android still asks the user to confirm the installation; this app does not install silently. A
build signed with a different key (including a differently signed F-Droid build) is refused.
Google Play builds do not use this updater or request permission to install APKs from outside Play.
