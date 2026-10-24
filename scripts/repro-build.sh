#!/usr/bin/env bash
# One clean release build inside the reproducible-build container (docker/repro). Run by
# release.yml and scripts/release-on-forge.sh, twice per release in two fresh containers, with
# the checkout always at /src (machine paths must not differ between the two).
#
#   scripts/repro-build.sh <out-dir>
#
# Needs SOURCE_DATE_EPOCH (the tagged commit's time). Builds both store flavours (STORES-PLAN 1)
# and writes to <out-dir>:
#   app-full-release-unsigned.apk   the GitHub and F-Droid APK (:app:assembleFullRelease)
#   app-play-release-unsigned.aab   the Google Play bundle (:app:bundlePlayRelease)
#   their .sha256 files, and the R8 mappings in mapping/full and mapping/play.
# Both are unsigned: signing happens after the comparison, outside Gradle, so no key ever enters a
# build container. :app:checkApkSizeRelease gates the size of both flavours.
set -euo pipefail
OUT="${1:?usage: repro-build.sh <out-dir>}"
: "${SOURCE_DATE_EPOCH:?set SOURCE_DATE_EPOCH to the tagged commit time}"
[ "$PWD" = "/src" ] || { echo "run from /src so both builds see the same paths" >&2; exit 2; }
mkdir -p "$OUT"
export TZ=UTC LC_ALL=C.UTF-8
echo "sdk.dir=$ANDROID_HOME" > local.properties
# SEED_GRADLE: a read-only Gradle home to copy the wrapper's distribution zip from (nothing else).
# The wrapper checks the zip against distributionSha256Sum before it unpacks it.
if [ -n "${SEED_GRADLE:-}" ] && [ -d "$SEED_GRADLE/wrapper/dists" ]; then
  ( cd "$SEED_GRADLE" && find wrapper/dists -name '*.zip' | while read -r z; do
      mkdir -p "$GRADLE_USER_HOME/$(dirname "$z")" && cp "$z" "$GRADLE_USER_HOME/$z"; done )
fi
chmod +x gradlew

# No build cache and no configuration cache: every task runs from the sources in this container.
./gradlew --no-daemon --no-build-cache --no-configuration-cache --console=plain \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=768m" \
  :app:assembleFullRelease :app:bundlePlayRelease :app:checkApkSizeRelease

APK=$(ls app/build/outputs/apk/full/release/*.apk | head -1)
AAB=$(ls app/build/outputs/bundle/playRelease/*.aab | head -1)
cp "$APK" "$OUT/app-full-release-unsigned.apk"
cp "$AAB" "$OUT/app-play-release-unsigned.aab"
for f in full play; do
  if [ -d "app/build/outputs/mapping/${f}Release" ]; then
    mkdir -p "$OUT/mapping/$f" && cp -r "app/build/outputs/mapping/${f}Release/." "$OUT/mapping/$f/"
  fi
done
zipalign -c 4 "$OUT/app-full-release-unsigned.apk" >/dev/null && echo "zipalign: ok"
( cd "$OUT" && sha256sum app-full-release-unsigned.apk | tee app-full-release-unsigned.apk.sha256 \
  && sha256sum app-play-release-unsigned.aab | tee app-play-release-unsigned.aab.sha256 )
