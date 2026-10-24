# F-Droid buildability: evidence (stores stream S2, 2026-10-23)

Everything below ran on the build host in throwaway containers under the shared build lock, from
branch `stores-s2` (main 56802ac plus this stream's commits). The F-Droid image is the one
F-Droid's own CI and build server use, pinned by digest in `.github/workflows/fdroid.yml`:

| Thing | Value |
| :- | :- |
| F-Droid image | `registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie@sha256:1ea8bef1820bff06c2974e31b4fc4c79c364cd24231b86e71fa4b926797e0dac` (built 2026-10-21), Debian 13.7 |
| JDK in it | Debian `openjdk-21-jdk-headless` 21.0.12.1+1-1~deb13u1 |
| fdroidserver | git `2d1e622fbbce7eec379453983435e5b14bea22b5` (master, 2026-10-22) |
| Gradle | `gradlew-fdroid` from the image (the wrappers are deleted, as on F-Droid): 8.9 for the launcher, 9.7.1 for the app |
| apksigcopier | Debian 1.1.1-1 (installed by the recipe's `sudo` step) |
| Reference build | `docker/repro` image (Temurin 21.0.12.1, pinned), `scripts/repro-build.sh` at `/src` |

## 1. Bundled APKs rebuilt from source with the committed signatures

`scripts/fdroid-build-tv-apks.sh` with both APKs removed, wrappers removed and upstream's
`signingConfig` line stripped (what fdroidserver does), checkout at
`/home/vagrant/build/tv.cleanyour.app`, Debian JDK 21:

```
==> launcher: assembleRelease in a copy of third_party/cleanyourtv/launcher (unsigned)
Found 8.9 via distributionUrl
BUILD SUCCESSFUL in 1m 10s
==> android/platform/src/main/assets/bundled/cleanyourtv-launcher.apk: size 421299, sha256 42692e10a44562f47ed63275578589ee61dae2356ff97f584ab8a59d56dc6bdd
    matches PinnedLauncher
==> curtain: :tv:curtain:assembleRelease (unsigned)
Found 9.7.1 via distributionUrl
BUILD SUCCESSFUL in 1m 41s
==> android/platform/src/main/assets/bundled/cleanyourtv-curtain.apk: size 65955, sha256 eb21e112c97121a3ed7f4db1c7fbf2169bc172a14bb3f8ca781079de4651662e
    matches PinnedCurtain
==> both bundled APKs rebuilt from source and match their pins
```

Then the app build in the same container ran `:core:trust:verifyCurtainPin` and
`:core:trust:verifyLauncherPin` unchanged ("bundled launcher matches its pin: 42692e10..., signer
57641815..."). The signature blocks (`android/platform/signatures/<asset>/APKSigningBlock` and
`APKSigningBlockOffset`) were made with `apksigcopier extract` from the committed signed APKs; the
new `forge_extract_signature` helper (used by `build-launcher-on-forge.sh` and
`build-curtain-on-forge.sh` after signing) produced byte identical files from the bundled launcher.

## 2. JDK: no re-pin needed

The pinned launcher was built on Temurin 17 (LAUNCHER-250-build-output.txt). The unsigned launcher
built on Debian's JDK 21 plus the committed signature block is the pinned APK byte for byte
(section 1), so 17 and 21 give the same launcher 2.5 and PinnedLauncher stays. The curtain was
already built on JDK 21 and matches too. `build-launcher-on-forge.sh` now builds on
`eclipse-temurin:21-jdk` so future launcher pins come from the JDK major F-Droid uses; it was not
re-run (re-signing is a key operation and nothing changed).

## 3. App determinism in F-Droid's environment

| Run | Image, JDK | Checkout path | CPUs (JVM) | Gradle | Bundled APKs | Unsigned `app-release` SHA-256 |
| :- | :- | :- | :- | :- | :- | :- |
| Reference | docker/repro, Temurin 21 | `/src` | 4 | wrapper, `--no-build-cache` | committed | `9d10f5bebcfedbbd940d33c44aece4d6f23783a2e355edd16a612355c0b64cf4` |
| F-Droid-like | buildserver, Debian 21 | `/home/vagrant/build/tv.cleanyour.app` | 8 | gradlew-fdroid in `app/` | rebuilt (section 1) | `9d10f5bebcfedbbd940d33c44aece4d6f23783a2e355edd16a612355c0b64cf4` |
| `fdroid build` | buildserver, Debian 21 | `/home/vagrant/work/data/build/tv.cleanyour.app` | 2 | fdroidserver, empty Gradle home | rebuilt by the recipe | compared with the published 0.2.0 APK: match |
| `scripts/fdroid-check.sh` | buildserver (by digest), Debian 21 | `/home/vagrant/fdroiddata/build/tv.cleanyour.app` | 4 | fdroidserver, empty Gradle home and SDK | rebuilt by the recipe | `9d10f5bebcfedbbd940d33c44aece4d6f23783a2e355edd16a612355c0b64cf4`, compare: match |

No drift: the same bytes across JDK vendor, path, CPU count (2, 4 and 8), Gradle launcher and a
cold or warm Gradle home. `assets/dexopt/baseline.prof` (profileinstaller, from the libraries'
profiles; the app has no `baseline-prof.txt` of its own) is part of those identical bytes, so it
needs no workaround (unlike the rebuild loop some apps carry in fdroiddata). The app has had no
APK-relevant change since v0.2.0, so the published 0.2.0 APK is the right reference; the `fdroid
build` run printed:

```
INFO: Successfully built tv.cleanyour.app:2 from 7e9f5fecec8c9f8c5b269159adb4815d9436af9b
INFO: ...retrieving https://github.com/udhaykumarbala/cleanyourtv-app/releases/download/v0.2.0/cleanyourtv-0.2.0.apk
INFO: compared built binary to supplied reference binary successfully
INFO: supplied reference binary has allowed signer eaf7af3867bb29ff4bb038854842c93f42ffe4198a087e611cb18f925201cd65
INFO: 1 build succeeded
```

and the same check through `scripts/fdroid-check.sh` (what the CI job runs):

```
==> tv.cleanyour.app 0.2.0 (2) at 6966dbaab80a85060cee7f55d110e8a512094d63, gradle flavour: yes
9d10f5bebcfedbbd940d33c44aece4d6f23783a2e355edd16a612355c0b64cf4  tv.cleanyour.app_2-unsigned.apk
signer eaf7af3867bb29ff4bb038854842c93f42ffe4198a087e611cb18f925201cd65 is in AllowedAPKSigningKeys
match: the signed APK is the F-Droid build plus our signature; F-Droid will publish it
```

Toolchain resolver: the foojay settings plugin is removed from `settings.gradle.kts` (and its two
entries from `gradle/verification-metadata.xml`). The JVM modules' `jvmToolchain(21)` is satisfied
by the JDK that runs Gradle; every run above built without it, so the recipe needs no prebuild for it.

Scanner: fdroidserver's scanner removes the wrapper jars and the two APKs and finds nothing else
except the two `APKSigningBlock` files (binary, no extension), which the recipe lists in
`scanignore`. fdroidserver also strips the `signingConfig` lines from the upstream launcher's and the
module-card fixture project's `build.gradle` (logged as "Cleaned build.gradle of keysigning
configs"); neither changes the outputs.

## 4. Theme wallpaper licence

The app bundles from upstream `themes/` (core/spec `bundleThemes`): `default/config.json`,
`aurora/config.json`, `aurora/wallpaper.jpg` and `six-columns/config.json`. The only picture is
`themes/aurora/wallpaper.jpg` (1920x1080 JPEG, 92,891 bytes, a soft teal and violet gradient on
navy with grain), added by the upstream author (Gathin23) in upstream commit b01bb94 (2026-10-19,
"themes/: default, aurora and six-columns"). The upstream repository is MIT ("Copyright (c) 2026
Gathin"); there is no separate licence, credit or source for the picture anywhere upstream (README,
themes/README.md, commit message), and the JPEG carries no EXIF or creator metadata.
`themes/dussehra/wallpaper.jpg` is not bundled.

Finding: the picture is the author's own contribution to an MIT repository, so it is most likely
MIT like the rest, but that is not stated anywhere. Not clearly free on paper. Proposed fix, without
changing upstream ourselves: ask Gathin to confirm in writing (an upstream issue reply, or a line
in `themes/README.md` such as "The wallpapers in this folder are original works and MIT licensed
like the rest of the repository") and link it in the F-Droid merge request (`docs/fdroid/MERGE-REQUEST.md`
has the place). If he cannot confirm, drop `aurora/wallpaper.jpg` from `bundleThemes` (the aurora
theme then shows its background colour) or replace it with a gradient we generate in the build.
THIRD-PARTY-NOTICES now names the bundled themes under the upstream MIT entry.

## 5. Metadata validation

On `docs/fdroid/tv.cleanyour.app.yml` with fdroiddata's current `config/categories.yml`:
`fdroid readmeta` ok, `fdroid lint tv.cleanyour.app` exit 0 with no findings, and the committed file
is `fdroid rewritemeta`'s own output (it wraps the long `Binaries` URL and orders `prebuild` before
`scanignore`). `.github/workflows/fdroid.yml`: actionlint clean; shellcheck clean on
`scripts/fdroid-build-tv-apks.sh` and `scripts/fdroid-check.sh`.

## 6. The merged tree for 0.3.0 (store flavours, `gradle: [full]`)

After the store flavours (S1) and this stream were merged, the recipe builds the `full` flavour
(`assembleFullRelease`, output `app/build/outputs/apk/full/release/app-full-release-unsigned.apk`),
and `scripts/fdroid-check.sh` checks that the APK fdroid build collected is that file. Run on commit
98b3d1270de29eb1c2301eb648c1aa7fbeabc98e (versionName 0.3.0, versionCode 3) in the same pinned image
and fdroidserver commit as above:

```
==> tv.cleanyour.app 0.3.0 (3) at 98b3d1270de29eb1c2301eb648c1aa7fbeabc98e, gradle flavour full (assembleFullRelease)
==> both bundled APKs rebuilt from source and match their pins
INFO: Successfully built tv.cleanyour.app:3 from 98b3d1270de29eb1c2301eb648c1aa7fbeabc98e
==> the F-Droid build is app/build/outputs/apk/full/release/app-full-release-unsigned.apk
84a9f044a4d2b9a253f8ae6af3e03165121105e7536e64570cbac421174a2ac4  tv.cleanyour.app_3-unsigned.apk
```

`scripts/release-on-forge.sh --test-key` on the same commit (docker/repro, two clean builds):
unsigned full APK `84a9f044a4d2b9a253f8ae6af3e03165121105e7536e64570cbac421174a2ac4` in both, the
same bytes as the F-Droid build; signed with a throwaway key, `apksigcopier compare: match` and
"22 of 22 checks passed (full)"; the play AAB
`dc4f2c1284fa7a1623bebd3a4c69cd0c7fc8382d459cbea5d5df9a856f32235d` in both, "25 of 25 checks passed
(play, bundle)". The full check build on that commit (eclipse-temurin JDK 21) gave the same full APK.

Metadata on that commit: `fdroid readmeta` ok, `fdroid lint tv.cleanyour.app` exit 0, and
checkupdates' own version detection (`common.manifest_paths` and `parse_androidmanifests` on
`app/build.gradle.kts`, flavour full) reads versionName 0.3.0 and versionCode 3, as in the recipe.
`fdroid rewritemeta` changes only the placeholder comment above `commit: v0.3.0`, which goes at
submission with the full commit hash.
