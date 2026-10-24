# Contributing to Clean Your TV

Thanks for helping. Bug reports, TV support requests, profile fixes and code are all welcome. This
app changes other people's TVs, so the bar for safety is high; the rules below exist for that.

By contributing you agree that your contribution is licensed under the [MIT licence](LICENSE) and
that you follow the [code of conduct](CODE_OF_CONDUCT.md). Security problems go through
[SECURITY.md](SECURITY.md), never a public issue.

## Development setup

You need JDK 21 and the Android SDK (compile SDK 37, build tools 36; Gradle downloads what is
missing once the licences are accepted). Linux or macOS. Gradle never downloads a JDK: the JDK 21
that runs Gradle is the toolchain of every module.

```bash
git clone --recurse-submodules https://github.com/udhaykumarbala/cleanyourtv-app.git
cd cleanyourtv-app
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew check assembleDebug
```

The submodule `third_party/cleanyourtv` is the upstream launcher at its pinned release; without
it the build stops and tells you to run `git submodule update --init`.

| Command | What it runs |
| :- | :- |
| `./gradlew check` | the module graph rules, the build-logic tests, every unit test (the app's on both store flavours), Android Lint and the pin checks |
| `./gradlew verifyRoborazziDebug` | the screenshot tests against the committed goldens |
| `./gradlew :app:assembleDebug` | debug APKs of both flavours in `app/build/outputs/apk/full/debug/` and `.../play/debug/` |
| `./gradlew :app:assembleFullRelease :app:bundlePlayRelease :app:checkApkSizeRelease` | the release APK (full) and AAB (play) through R8, and the 8 MB size gate on both flavours |
| `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` | the tests of the release and redaction scripts |

CI (`.github/workflows/pr.yml`) runs all of these on every push and pull request; please run at
least `check` before opening one.

### Store flavours

The app module has two product flavours (dimension `store`) built from the same code, with the
same application ID, versionCode and signing certificate:

- `full`, for GitHub and F-Droid: "Check for updates" asks GitHub for the latest release, the
  optional add-ons (Cards and modules) download from GitHub, and software updates on the TV are
  a choice in Customise. The default flavour.
- `play`, for Google Play: no update check (Play updates the app), no add-on downloads (Play
  allows no app downloads from outside Play), the TV's software updaters always stay on, and the
  network security config names no host.

The difference lives in `app/src/full` and `app/src/play` only: `Store.kt` (the ports AppGraph
wires: the hosts, the update checker, the add-on fetcher, the rules the flows follow) and
`res/xml/network_security_config.xml`. The screens read the rules from their state (`StoreRules`
in `:core:flows`) and leave out what a build does not offer. Tests that only hold for one flavour
go in `app/src/testFull` or `app/src/testPlay`. The version lives in `app/build.gradle.kts`.

Optional helpers: `scripts/build-on-forge.sh` and the other `scripts/*-on-forge.sh` scripts run the
same Gradle tasks, emulator sessions and release builds in throwaway Docker containers on a remote
Linux build host over ssh ("forge"); see `scripts/lib/forge.sh` for the settings. You never need
them for a contribution.

### Tests and screenshot goldens

- Every core interface has a fake in `:testing:fixtures`, and `:testing:fake-adbd` is a fake TV
  that speaks the ADB protocol. Tests never touch a real TV; nothing in the build may need one.
- Screenshot tests are Roborazzi on Robolectric (native graphics) in each `feature/*` module and
  `:ui:design`, in four renderings: dark, light, 200 % font and right to left, with the
  accessibility checks on. Goldens live in `src/test/screenshots/`.
- Changed a screen on purpose? Record the goldens with `./gradlew recordRoborazziDebug`, look at
  every changed PNG, and commit them with the change. Record on Linux x86_64 (as CI does): other
  platforms can render text a pixel differently.
- End to end scenarios against an Android TV emulator are in `:e2e`
  (`scripts/emulator-session.sh 28 ./gradlew :e2e:emulatorTest` on a machine with KVM; nightly CI
  runs them).

## Module rules

The project is split into small modules with one direction of dependency, checked by
`checkModuleGraph` (rules in `build-logic/convention/src/main/kotlin/tv/cleanyour/buildlogic/ModuleRules.kt`):

- `core/*` and `adb/*` are pure Kotlin JVM modules: no Android imports, no Android plugin.
  `:core:model` holds the shared types and interfaces.
- `android/*`, `ui/design`, `feature/*` and `app` are Android modules. A feature module may depend
  only on `:core:flows`, `:core:model` and `:ui:design`, never on another feature or on a lower
  core module.
- No upward edges, no test module in a main configuration, and a third-party library only when it
  is on that module's allowlist. Versions live only in `gradle/libs.versions.toml`, stable releases
  only, and every artifact is checked by `gradle/verification-metadata.xml`
  (`scripts/update-verification-metadata.sh` after a version change).
- Shell lines that reach the TV are built only by `:core:ops` and `:core:probe` (`@RawShell`);
  credential passes only by the gate implementations (`@GateIssuer`). Do not work around either.

Changing a rule, a public interface in `:core:model` or a pin is fine, but say so in the pull
request and explain why; reviewers look at those first.

## Reporting a problem or a new TV

You don't need a computer or adb for either. The app makes the report, shows it to you first, and
sends nothing itself: you copy it to GitHub, share it, or close the screen.

- **Something went wrong.** Tap **Report a problem**, on the card that stopped you or in the app's
  Settings, then **Open GitHub**. The "Report a problem" form opens with the app version, the phone,
  the TV and the report filled in; say in a few words what happened. (A report too long for the link
  is copied instead, the button says **Copy and open GitHub**, and you paste it into the last box.)
  The report is the app's log with addresses, serial numbers, keys and codes removed.
- **Your TV isn't listed.** When the app doesn't know your exact TV it is careful and says so. Tap
  **Help add this TV** (on the "We found" screen, or the TV's Manage screen), then **Open GitHub**.
  The "Help add my TV" form opens with the model, the Android version and the report filled in (or,
  for a report too long for the link, **Copy and open GitHub** and paste it). It lists the apps the
  TV came with; apps you installed are only counted.

We read every report. For a new TV we make a list from the report, ask you to try it, and send it
upstream once it works (below). How maintainers sort reports is in [docs/TRIAGE.md](docs/TRIAGE.md).

## Propose a TV profile

The profiles come from the upstream launcher's community lists, so a new TV goes upstream first:

1. **Upstream.** Add `examples/<brand>-<model>/debloat.txt` to
   [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) following its
   [CONTRIBUTING.md](https://github.com/Gathin23/cleanyourtv/blob/main/CONTRIBUTING.md): only
   packages you have disabled and lived with, the model, Android version and firmware in the
   header, and what broke. That list is useful to everyone, with or without this app.
2. **Here, once it is merged upstream.** Open a pull request that moves the
   `third_party/cleanyourtv` submodule to the upstream commit with your list, and adds the TV's
   entry to `core/spec/src/main/resources/tv/cleanyour/core/spec/profile-meta.json`: how to
   recognise it (`match`: model or device names, brand, SDK range), its platform, stock launcher,
   Live TV app and setup wizard, `neverDisable` for anything that must stay on, a category for
   each group of the list, and `tested` (who, which firmware, how many days, whether a reboot was
   tried). The build converts the list at build time and fails on anything it does not
   understand. Bundled launcher changes need the maintainer (the APK is re-signed).

No upstream list yet? Open a "Help add my TV" issue with the app's report (above); someone with the
same TV may pick it up.

## Commits and pull requests

- Small, focused commits with plain messages that say what changed and why.
- No em dashes and no double hyphen sequences in prose, comments, strings, docs or commit
  messages (real command line flags are fine). Use a period, comma, colon or a single hyphen.
- No generated banners or trailers in commit messages.
- User facing text is plain, calm English (see the strings in `feature/*/src/main/res/values/`).
  Explain what happens to the TV, not how the code works.
- Tests come with the change. A bug fix starts with a test that fails without it.

## Security rules every change must respect

- **Reversible only.** Packages are disabled for the user (`pm disable-user`), never uninstalled.
  Every write to the TV is recorded so it can be undone, and a restore point is saved before the
  first one.
- **Show before run.** Every command reaches the TV through the plan the user saw ("Show
  commands"). No hidden commands, no shell strings assembled outside `:core:ops` and `:core:probe`.
- **The protected list stays protected.** `Denylist` in `:core:ops` (Play services, the Play
  Store, Wi-Fi, Bluetooth for the remote, the TV's inputs, the shell and adbd) is never relaxed
  for convenience.
- **The watchdog stays in front of every home screen change.**
- **Network.** The app talks to the chosen TV on the LAN and, on the user's action only, to the
  GitHub hosts in `DownloadHosts` (the full flavour's network security config lists the same; the
  play flavour reaches no host at all). No analytics, no crash reporting, no new hosts or
  permissions without an explicit review.
- **Downloads are pinned.** Anything installed on a TV is checked by size, SHA-256 and signer
  (`:core:trust`) before it is installed. The bundled launcher and TV curtain are pinned in
  `Pins.kt` and checked at build time.
- **No secrets and no personal data in the repository.** Never commit keys, keystores, passwords,
  tokens, or a real TV's serial, MAC or IP address. Captures from a real TV go through
  `scripts/import-mitv-capture.py` and `scripts/check-redaction.py` before they become fixtures.
- **Logs and reports are redacted.** Addresses, serials, keys and codes never reach a log line or
  a report. A TV report names only the TV's system apps; the person's own apps are a count.

## Releases (maintainers)

One tag, three channels: every `vX.Y.Z` ships to GitHub and Google Play the same day and to
F-Droid on its next build cycle, from the same commit, with the same versionCode and the same
signing certificate (`docs/release-signer.sha256`), so an install from any channel updates from
any other. GitHub and F-Droid get the `full` APK, Google Play the `play` AAB.

1. Set `versionCode` and `versionName` in `app/build.gradle.kts`, add the CHANGELOG entry and the
   short store notes (`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, at most 500
   characters; F-Droid and Google Play both read them), commit, and tag `vX.Y.Z`.
2. `scripts/release-on-forge.sh` builds the release set: two clean builds of
   `:app:assembleFullRelease` and `:app:bundlePlayRelease` in the pinned container
   (`docker/repro`, `scripts/repro-build.sh`), each pair byte identical. On the maintainer's
   machine only, the APK is signed with the release key (apksigner, v2 and v3) and the AAB with
   the Play upload key (jarsigner). `scripts/verify-release.sh` then checks the APK (signature,
   signer, `apksigcopier compare` against the unsigned build, `scripts/check-apk.py --flavour
   full`) and the AAB (signature, upload signer, the unsigned build plus that signature,
   `check-apk.py --flavour play`: the same permissions, no GitHub host, no update check). Output:
   `dist/cleanyourtv-X.Y.Z.apk` and `.aab` with their checksums, evidence in
   `dist/release-X.Y.Z/`. `--apk-only` signs the APK alone (no upload key yet); `--test-key`
   proves the pipeline with throwaway keys (evidence in `dist/release-X.Y.Z-TESTKEY/`, so a real
   run's evidence stays).
3. Push the tag, write the GitHub notes (`dist/release-X.Y.Z/RELEASE-NOTES.md` or `--notes`),
   then `scripts/publish-release.sh vX.Y.Z` publishes: the GitHub release with the APK and its
   checksum, the AAB and the store notes to Google Play through the Play Developer API
   (`scripts/play-upload.py`; the internal track by default, `--track closed` for the closed test,
   `--track production --rollout 0.2` for a staged rollout, `--draft` until the first release is
   published from the Play Console), then the release workflow's verify job. `--dry-run` checks
   every input and sends nothing.
4. F-Droid needs nothing per release: it builds new tags itself and ships our signed APK when
   its build matches.

CI never holds a key or a credential: on a `v*` tag, `.github/workflows/release.yml` builds the
unsigned APK and AAB twice each, compares them and publishes them as artifacts, and its verify job
checks the signed APK attached to the GitHub Release against that build.

Keys and credentials live outside the repository, under `$CYT_SECRETS` (see `scripts/lib/forge.sh`),
and only the scripts read them:

| Path under `$CYT_SECRETS` | What | Used by |
| :- | :- | :- |
| `cleanyourtv-release/release.p12`, `keystore-password.txt` | the release key (PKCS#12) and its password | `release-on-forge.sh` (the APK) |
| `cleanyourtv-play-upload/upload.p12`, `keystore-password.txt` | the Play upload key (PKCS#12, RSA 2048 or more, alias `upload`) and its password | `release-on-forge.sh` (the AAB) |
| `cleanyourtv-play/service-account.json` | a Google Cloud service account key with release rights on the app in the Play Console | `play-upload.py` (read only, never printed) |

The Play upload key: Play App Signing holds the release key (uploaded once from the existing key,
so Play installs share the certificate of the other channels), and a separate upload key signs
what is uploaded. Make it once, keep it with the release key, and register its certificate in the
Play Console (Setup, App signing):

```bash
keytool -genkeypair -keystore "$CYT_SECRETS/cleanyourtv-play-upload/upload.p12" -storetype PKCS12 \
  -alias upload -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Clean Your TV upload key" \
  -storepass:file "$CYT_SECRETS/cleanyourtv-play-upload/keystore-password.txt" \
  -keypass:file "$CYT_SECRETS/cleanyourtv-play-upload/keystore-password.txt"
keytool -exportcert -rfc -keystore "$CYT_SECRETS/cleanyourtv-play-upload/upload.p12" -alias upload \
  -storepass:file "$CYT_SECRETS/cleanyourtv-play-upload/keystore-password.txt" > upload-cert.pem
```

`scripts/play-upload.py` uses only the Python standard library (it signs the OAuth request itself),
so no extra package is needed.

Once, before the first Google Play release (decision D2, one certificate for every channel): give
Play App Signing the existing release key instead of letting Google make a new one. In the Play
Console (Test and release, App integrity, App signing) choose "Export and upload a key from Java
keystore", download Google's PEPK tool and its encryption public key, and run the tool on the
maintainer's machine only, where the release key lives; it asks for the keystore and key passwords
itself, so they never appear on the command line:

```bash
java -jar pepk.jar --keystore="$CYT_SECRETS/cleanyourtv-release/release.p12" --alias=cleanyourtv \
  --output=cleanyourtv-release-for-play.zip --include-cert --rsa-aes-encryption \
  --encryption-key-path=encryption_public_key.pem
```

Upload the zip (it holds the key encrypted for Google only), then delete it. Check afterwards that
the Console shows the app signing certificate SHA-256 of `docs/release-signer.sha256`, and register
the upload key's certificate (`upload-cert.pem` above) on the same page.

Google Play's first steps are by hand: the first bundle of a new app is uploaded in the Play
Console (the API takes uploads only once the app has one), and until the app's first release is
published there, `scripts/publish-release.sh` needs `--draft`. The first production release cannot
be a staged rollout, so it takes `--track production --rollout 1`; staged rollouts (the default
0.2) work from the second production release on.

## F-Droid (maintainers)

F-Droid builds the app from source with the recipe in `docs/fdroid/tv.cleanyour.app.yml` and
publishes our own signed APK when its build is that APK minus the signature (`Binaries` and
`AllowedAPKSigningKeys` in the recipe). After the one-time merge request to
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) (text in `docs/fdroid/MERGE-REQUEST.md`), a
release reaches F-Droid by itself:

1. F-Droid's update check sees the new `vX.Y.Z` tag and reads versionName and versionCode from the
   app's build file.
2. Its build server checks out the tag with the submodule, removes the two bundled TV APKs and runs
   `scripts/fdroid-build-tv-apks.sh`, which builds the launcher (from `third_party/cleanyourtv`) and
   `:tv:curtain` unsigned, puts the committed signature blocks from `android/platform/signatures/`
   back on them with `apksigcopier patch` and checks the result against `Pins.kt`. Then it builds
   the app's `full` release.
3. It downloads `cleanyourtv-X.Y.Z.apk` from the GitHub Release and publishes it if
   `apksigcopier compare` passes and the signer is the release certificate.

So per release: keep the tag format `vX.Y.Z` and the asset name `cleanyourtv-X.Y.Z.apk`, and check
the F-Droid build before F-Droid does. The `fdroid-check` job (`.github/workflows/fdroid.yml`) runs
fdroidserver's `fdroid build` on the recipe in F-Droid's buildserver image on every tag and nightly,
and compares the result with the Release's APK when there is one. Once the Release is published,
run it on the tag: `gh workflow run fdroid.yml --ref vX.Y.Z`; it must say "match".

When a bundled TV APK is rebuilt and re-signed (`scripts/build-launcher-on-forge.sh`,
`scripts/build-curtain-on-forge.sh`), those scripts also refresh its signature block in
`android/platform/signatures/`; commit the APK, the signature block and the `Pins.kt` change
together. A stale signature block makes `scripts/fdroid-build-tv-apks.sh` fail.

Run the check locally:

```bash
# Only the bundled TV APKs: rebuild both from source and check them against their pins
# (JDK 21, the Android SDK and apksigcopier; the assets are rewritten with the same bytes).
scripts/fdroid-build-tv-apks.sh

# The whole F-Droid build, on a Linux x86_64 machine with Docker, from a normal clone (not a git
# worktree). Mount a signed APK at /in/signed.apk to compare it; outputs land in dist/fdroid/.
IMAGE=$(sed -n 's/^  FDROID_IMAGE: //p' .github/workflows/fdroid.yml)
docker run --rm -v "$PWD":/seed:ro -v "$PWD/dist/fdroid":/out \
  -v "$PWD/cleanyourtv-X.Y.Z.apk":/in/signed.apk:ro "$IMAGE" bash /seed/scripts/fdroid-check.sh vX.Y.Z
```
