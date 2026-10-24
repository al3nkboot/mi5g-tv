#!/usr/bin/env bash
# Rebuild the two TV APKs the app bundles (android/platform/src/main/assets/bundled/) from source,
# with no key, and put the committed signatures back on them, so the result is byte for byte the
# pinned APK (Pins.kt: PinnedLauncher, PinnedCurtain) and verifyLauncherPin and verifyCurtainPin pass
# unchanged. This is what F-Droid runs before it builds the app (docs/fdroid/), and anyone can run it
# to check that the bundled APKs are what the public source builds.
#
#   scripts/fdroid-build-tv-apks.sh
#
# 1. Launcher (com.gathin.tvhome): third_party/cleanyourtv/launcher is copied to a temporary
#    directory (no .git there, as in scripts/build-launcher-on-forge.sh, so AGP records "no VCS"),
#    upstream's `signingConfig signingConfigs.debug` is taken out of the copy (F-Droid's own
#    source preparation removes the same line), and `assembleRelease` builds the unsigned APK.
# 2. Curtain (tv.cleanyour.curtain): `:tv:curtain:assembleRelease` in this repository (no R8, no
#    signing config: the release APK is unsigned already).
# 3. `apksigcopier patch` puts each signature block from android/platform/signatures/<asset>/
#    (made by `apksigcopier extract` from the signed APK) onto the unsigned APK; the result goes
#    into the assets and its size and SHA-256 must equal the pin.
#
# The signature only verifies when the rebuilt bytes are exactly the ones that were signed, so a
# pass proves the bundled APKs are built from this source. The submodule must be at the pinned
# commit (PinnedLauncher.UPSTREAM_COMMIT) when it is a git checkout.
#
# Needs: a JDK 21 (the launcher's Gradle 8.9 and AGP 8.5 run on it too), the Android SDK
# (ANDROID_HOME or local.properties; AGP fetches the platform and build tools it lacks once the
# licences are accepted), apksigcopier 1.1 on PATH, python3 or sha256sum. Gradle: GRADLE when set,
# else each project's ./gradlew, else `gradle` on PATH (F-Droid removes the wrappers and provides
# gradlew-fdroid as `gradle`, which reads the version from gradle-wrapper.properties).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD

ASSETS=android/platform/src/main/assets/bundled
SIGS=android/platform/signatures
PINS=core/trust/src/main/kotlin/tv/cleanyour/core/trust/Pins.kt
LAUNCHER_SRC=third_party/cleanyourtv/launcher
GRADLE_ARGS=(--no-daemon --no-build-cache --no-configuration-cache --console=plain -Dorg.gradle.jvmargs=-Xmx1536m)

die() { echo "fdroid-build-tv-apks: $*" >&2; exit 1; }
sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
size() { wc -c < "$1" | tr -d ' '; }
# The value of `const val NAME = ...` inside `object OBJ` of Pins.kt, without quotes and underscores.
pin() { sed -n "/^object $1 /,/^}/s/.*const val $2 = \"*\([0-9a-f_L]*\)\"*.*/\1/p" "$PINS" | head -1 | tr -d '_L'; }
gradle_in() {   # gradle_in <dir> <args...>: the Gradle to run in that project
  local dir=$1; shift
  if [ -n "${GRADLE:-}" ]; then (cd "$dir" && $GRADLE "$@")
  elif [ -f "$dir/gradlew" ] && [ -f "$dir/gradle/wrapper/gradle-wrapper.jar" ]; then (cd "$dir" && sh ./gradlew "$@")
  else command -v gradle >/dev/null || die "no ./gradlew in $dir and no gradle on PATH"; (cd "$dir" && gradle "$@")
  fi
}

command -v apksigcopier >/dev/null || die "apksigcopier not found (Debian: apt-get install apksigcopier; else pip install apksigcopier)"
[ -f "$LAUNCHER_SRC/app/build.gradle" ] || die "the upstream submodule is empty; run: git submodule update --init third_party/cleanyourtv"
want_commit=$(sed -n 's/.*const val UPSTREAM_COMMIT = "\([0-9a-f]\{40\}\)".*/\1/p' "$PINS")
if git -C third_party/cleanyourtv rev-parse HEAD >/dev/null 2>&1; then
  have_commit=$(git -C third_party/cleanyourtv rev-parse HEAD)
  [ "$have_commit" = "$want_commit" ] || die "third_party/cleanyourtv is at $have_commit, the pin is $want_commit"
fi
for a in cleanyourtv-launcher cleanyourtv-curtain; do
  [ -s "$SIGS/$a/APKSigningBlock" ] && [ -s "$SIGS/$a/APKSigningBlockOffset" ] || die "no committed signature in $SIGS/$a"
done
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -z "$SDK" ] && [ -f local.properties ] && SDK=$(sed -n 's/^sdk\.dir=//p' local.properties)
[ -n "$SDK" ] || die "set ANDROID_HOME (or sdk.dir in local.properties)"

WORK=$(mktemp -d "${TMPDIR:-/tmp}/cyt-tv-apks.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$ASSETS"

# patch_and_check <unsigned.apk> <asset name> <Pins object>
patch_and_check() {
  local unsigned=$1 name=$2 obj=$3 out="$ASSETS/$2.apk"
  rm -f "$out"
  apksigcopier patch "$SIGS/$name" "$unsigned" "$out"
  local got_sha got_size want_sha want_size
  got_sha=$(sha256 "$out"); got_size=$(size "$out")
  want_sha=$(pin "$obj" SHA256); want_size=$(pin "$obj" SIZE)
  echo "==> $out: size $got_size, sha256 $got_sha"
  if [ "$got_sha" != "$want_sha" ] || [ "$got_size" != "$want_size" ]; then
    echo "    unsigned build: size $(size "$unsigned"), sha256 $(sha256 "$unsigned")" >&2
    rm -f "$out"
    die "$name does not match $obj (size $want_size, sha256 $want_sha): the source build differs from the signed one"
  fi
  echo "    matches $obj"
}

echo "==> launcher: assembleRelease in a copy of $LAUNCHER_SRC (unsigned)"
cp -R "$LAUNCHER_SRC" "$WORK/launcher"
rm -rf "$WORK/launcher/.gradle" "$WORK/launcher/build" "$WORK/launcher/app/build" "$WORK/launcher/.git"
sed -i.orig -e '/^[[:space:]]*signingConfig[[:space:]]\{1,\}signingConfigs\.debug[[:space:]]*$/d' "$WORK/launcher/app/build.gradle"
rm -f "$WORK/launcher/app/build.gradle.orig"
! grep -n 'signingConfig' "$WORK/launcher/app/build.gradle" || die "the launcher still names a signing config"
echo "sdk.dir=$SDK" > "$WORK/launcher/local.properties"
gradle_in "$WORK/launcher" assembleRelease "${GRADLE_ARGS[@]}" -PlauncherApplicationId=com.gathin.tvhome
LAUNCHER_UNSIGNED="$WORK/launcher/app/build/outputs/apk/release/app-release-unsigned.apk"
[ -f "$LAUNCHER_UNSIGNED" ] || die "no unsigned launcher APK at $LAUNCHER_UNSIGNED"
patch_and_check "$LAUNCHER_UNSIGNED" cleanyourtv-launcher PinnedLauncher

echo "==> curtain: :tv:curtain:assembleRelease (unsigned)"
[ -f local.properties ] || echo "sdk.dir=$SDK" > local.properties
gradle_in "$ROOT" :tv:curtain:assembleRelease "${GRADLE_ARGS[@]}"
patch_and_check tv/curtain/build/outputs/apk/release/curtain-release-unsigned.apk cleanyourtv-curtain PinnedCurtain

echo "==> both bundled APKs rebuilt from source and match their pins"
