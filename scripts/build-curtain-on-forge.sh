#!/usr/bin/env bash
# Build the TV curtain APK (tv.cleanyour.curtain, module :tv:curtain, RFC-CURTAIN-1) on forge, prove
# the build reproducible, sign it with the launcher release key K1 and place it in the app's assets.
#
#   scripts/build-curtain-on-forge.sh            # build twice, compare, sign, verify, copy into assets
#
# Reproducibility: the release APK (unsigned, no R8) is built twice, in two fresh checkouts and two
# throwaway containers, with SOURCE_DATE_EPOCH set to the commit time and the Gradle build cache off,
# and the two unsigned APKs must be byte for byte identical before anything is signed. The builds run
# in eclipse-temurin:21-jdk (this repo's JVM toolchain) under the same host lock as
# scripts/build-on-forge.sh, one at a time. Signing follows scripts/build-launcher-on-forge.sh: a third
# throwaway container, the keystore and its password over the ssh stdin into the container's /dev/shm,
# never on forge's disk; v2 and v3 only. Key custody: $CYT_SECRETS/cleanyourtv-launcher/ on the
# maintainer's machine only (scripts/lib/forge.sh). The signer digest must equal PinnedLauncher.SIGNER_SHA256 (same key, one pin).
# The signature block of the signed APK is extracted into android/platform/signatures/ for the F-Droid
# rebuild (scripts/fdroid-build-tv-apks.sh); commit it with the APK.
# After a rebuild, update PinnedCurtain in core/trust with the printed size and sha256; the
# verifyCurtainPin task fails the build until they agree.
set -euo pipefail
cd "$(dirname "$0")/.."

. scripts/lib/forge.sh
SECRETS="${SECRETS:-$CYT_SECRETS/cleanyourtv-launcher}"
IMAGE="eclipse-temurin:21-jdk"
SIGN_IMAGE="eclipse-temurin:17-jdk"
VOLUME="mitv-android-cache"
BUILD_TOOLS="34.0.0"
RUN_ID="cyt-curtain-$(date +%Y%m%d%H%M%S)-$$"
OUT="android/platform/src/main/assets/bundled/cleanyourtv-curtain.apk"
UNSIGNED="tv/curtain/build/outputs/apk/release/curtain-release-unsigned.apk"
CMDLINE_TOOLS="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

[ -r "$SECRETS/launcher-release.p12" ] && [ -r "$SECRETS/keystore-password.txt" ] || { echo "launcher key not found in $SECRETS" >&2; exit 1; }
[ -f third_party/cleanyourtv/README.md ] || { echo "upstream submodule is empty; run: git submodule update --init third_party/cleanyourtv" >&2; exit 1; }
EPOCH=$(git log -1 --format=%ct)
EXPECTED_SIGNER=$(sed -n '/^object PinnedLauncher/,/^}/s/.*const val SIGNER_SHA256 = "\([0-9a-f]\{64\}\)".*/\1/p' core/trust/src/main/kotlin/tv/cleanyour/core/trust/Pins.kt | head -1)
[ -n "$EXPECTED_SIGNER" ] || { echo "PinnedLauncher.SIGNER_SHA256 not found" >&2; exit 1; }

forge_init
BASE="$FORGE_ROOT/$RUN_ID"

cleanup() {
  "${SSH[@]}" "docker rm -f '$RUN_ID-a' '$RUN_ID-b' '$RUN_ID-sign' >/dev/null 2>&1 || true; rm -rf '$BASE'" </dev/null || true
}
trap cleanup EXIT INT TERM

# One build in a fresh checkout and a fresh container: ./gradlew assembleRelease of :tv:curtain only.
build() {
  local name=$1 dir="$BASE/$1"
  echo "==> rsync to $FORGE:$dir"
  "${SSH[@]}" "mkdir -p '$dir'" </dev/null
  rsync -az --delete -e "ssh -i $SSH_KEY -o BatchMode=yes" \
    --exclude .git --exclude .gradle/ --exclude .kotlin/ --exclude build/ --exclude local.properties \
    --exclude dist/ --exclude .idea/ \
    ./ "$FORGE:$dir/"
  echo "==> build $name: :tv:curtain:assembleRelease in $IMAGE (SOURCE_DATE_EPOCH=$EPOCH)"
  "${SSH[@]}" "flock -w 3600 '$LOCK' docker run --rm --name '$RUN_ID-$name' --memory 3g --cpus 4 \
    -e HOME=/cache/home -e OWNER=\$(id -u):\$(id -g) -e SOURCE_DATE_EPOCH=$EPOCH -e TZ=UTC -e LC_ALL=C.UTF-8 \
    -v $VOLUME:/cache -v '$dir':/src -w /src $IMAGE bash -euc '
      export ANDROID_HOME=/cache/sdk GRADLE_USER_HOME=/cache/gradle
      trap \"chown -R \$OWNER /src\" EXIT
      mkdir -p /cache/home
      if [ ! -x \$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager ]; then
        mkdir -p \$ANDROID_HOME/cmdline-tools && cd \$ANDROID_HOME/cmdline-tools
        curl -fsSL -o tools.zip $CMDLINE_TOOLS && jar xf tools.zip && rm tools.zip
        rm -rf latest && mv cmdline-tools latest && chmod +x latest/bin/*
        cd /src
      fi
      yes | \$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null
      \$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \"build-tools;$BUILD_TOOLS\" >/dev/null
      echo sdk.dir=\$ANDROID_HOME > local.properties
      chmod +x gradlew
      ./gradlew :tv:curtain:assembleRelease :tv:curtain:checkApkSizeRelease --no-build-cache --no-configuration-cache --console=plain -q
      ls -l $UNSIGNED; sha256sum $UNSIGNED
    '" </dev/null
}

build a
build b

echo "==> compare the two unsigned APKs"
"${SSH[@]}" "cmp '$BASE/a/$UNSIGNED' '$BASE/b/$UNSIGNED' && echo identical" </dev/null || {
  echo "the two builds differ: not reproducible" >&2
  "${SSH[@]}" "cd '$BASE' && unzip -lv 'a/$UNSIGNED' > a.lst && unzip -lv 'b/$UNSIGNED' > b.lst; diff a.lst b.lst | head -40" </dev/null || true
  exit 1
}

echo "==> sign with the launcher release key (in memory) and verify"
{ cat "$SECRETS/keystore-password.txt"; printf '\n'; base64 < "$SECRETS/launcher-release.p12"; } | \
"${SSH[@]}" "flock -w 3600 '$LOCK' docker run -i --rm --name '$RUN_ID-sign' --memory 1g --cpus 2 \
  -e OWNER=\$(id -u):\$(id -g) \
  -v $VOLUME:/cache -v '$BASE':/src -w /src $SIGN_IMAGE bash -euc '
    trap \"rm -f /dev/shm/ks.p12 /dev/shm/ks.pass; chown -R \$OWNER /src\" EXIT
    umask 077
    IFS= read -r pass; printf %s \"\$pass\" > /dev/shm/ks.pass; unset pass
    base64 -d > /dev/shm/ks.p12
    BT=/cache/sdk/build-tools/$BUILD_TOOLS
    in=a/$UNSIGNED
    \$BT/zipalign -c -p 4 \"\$in\"
    \$BT/apksigner sign --ks /dev/shm/ks.p12 --ks-type PKCS12 --ks-pass file:/dev/shm/ks.pass \
      --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false \
      --out /src/cleanyourtv-curtain.apk \"\$in\"
    rm -f /dev/shm/ks.p12 /dev/shm/ks.pass
    \$BT/apksigner verify --verbose --print-certs /src/cleanyourtv-curtain.apk | grep -E \"^Verified using|^Signer #1 certificate (DN|SHA-256)|^Number of signers\" | tee /src/verify.txt
    \$BT/aapt2 dump badging /src/cleanyourtv-curtain.apk | head -1
    sha256sum /src/cleanyourtv-curtain.apk; stat -c \"size %s\" /src/cleanyourtv-curtain.apk
  '"

SIGNER=$("${SSH[@]}" "sed -n 's/^Signer #1 certificate SHA-256 digest: //p' '$BASE/verify.txt'" </dev/null)
[ "$SIGNER" = "$EXPECTED_SIGNER" ] || { echo "signer digest $SIGNER differs from PinnedLauncher.SIGNER_SHA256 $EXPECTED_SIGNER" >&2; exit 1; }
echo "==> signer digest equals PinnedLauncher.SIGNER_SHA256"

mkdir -p "$(dirname "$OUT")"
scp -q -i "$SSH_KEY" "$FORGE:$BASE/cleanyourtv-curtain.apk" "$OUT"
forge_extract_signature "$BASE/cleanyourtv-curtain.apk" android/platform/signatures/cleanyourtv-curtain
echo "==> $OUT"
shasum -a 256 "$OUT"
wc -c < "$OUT" | tr -d ' ' | sed 's/^/size /'
