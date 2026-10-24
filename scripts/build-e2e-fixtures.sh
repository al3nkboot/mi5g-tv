#!/usr/bin/env bash
# Builds the two APKs the TV e2e scenarios install (W9), before an emulator session:
#  - the upstream launcher at the pinned submodule commit (third_party/cleanyourtv/launcher, its own
#    Gradle wrapper and AGP, release build signed with the build machine's debug key, as upstream
#    does), until W5's bundled, pinned launcher replaces it;
#  - the S10 crashing launcher fixture (e2e/fixtures/crashing-launcher), with this repo's wrapper.
# Prints the CYT_E2E_* lines to export; with -o FILE also writes them there. Needs ANDROID_HOME.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=""
[ "${1:-}" = "-o" ] && OUT="$2"

: "${ANDROID_HOME:?set ANDROID_HOME to an SDK with platforms 34 and 36}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

echo "==> upstream launcher at $(git -C third_party/cleanyourtv rev-parse --short HEAD 2>/dev/null || echo pinned)" >&2
( cd third_party/cleanyourtv/launcher && echo "sdk.dir=$ANDROID_HOME" > local.properties && chmod +x gradlew \
  && ./gradlew assembleRelease --console=plain -q -Dorg.gradle.jvmargs=-Xmx1g )
LAUNCHER=$(ls "$PWD"/third_party/cleanyourtv/launcher/app/build/outputs/apk/release/*.apk | head -1)

echo "==> crashing launcher fixture" >&2
echo "sdk.dir=$ANDROID_HOME" > e2e/fixtures/crashing-launcher/local.properties
./gradlew -p e2e/fixtures/crashing-launcher assembleDebug --console=plain -q -Dorg.gradle.jvmargs=-Xmx1g
CRASHING=$(ls "$PWD"/e2e/fixtures/crashing-launcher/build/outputs/apk/debug/*.apk | head -1)
./gradlew -p e2e/fixtures/crashing-launcher --stop >/dev/null 2>&1 || true
( cd third_party/cleanyourtv/launcher && ./gradlew --stop >/dev/null 2>&1 || true )
rm -f third_party/cleanyourtv/launcher/local.properties

lines="CYT_E2E_LAUNCHER_APK=$LAUNCHER
CYT_E2E_CRASHING_LAUNCHER_APK=$CRASHING"
echo "$lines"
[ -n "$OUT" ] && echo "$lines" > "$OUT"
sha256sum "$LAUNCHER" "$CRASHING" >&2 2>/dev/null || shasum -a 256 "$LAUNCHER" "$CRASHING" >&2
