#!/usr/bin/env bash
# Build the app on forge in a throwaway JDK 21 container (AGP 9 runs on JDK 17 or newer; the JVM
# modules use a JDK 21 toolchain). The build host is set in scripts/lib/forge.sh (FORGE).
#
#   scripts/build-on-forge.sh                      # ./gradlew check assembleDebug
#   scripts/build-on-forge.sh checkModuleGraph     # any Gradle tasks and flags instead
#   FETCH_BACK="gradlew gradle/wrapper" scripts/build-on-forge.sh wrapper   # copy files back on success
#
# The checkout (with the upstream submodule's files) is rsynced to a fresh directory on forge, the
# container shares the mitv-android-cache volume (Android SDK and Gradle caches) with the other
# forge builds, runs with a 3 GB memory cap, and both the container and the remote directory are
# removed afterwards, also on failure or Ctrl-C. A debug APK of the full flavour, when built, is
# copied to dist/cleanyourtv-debug.apk (and the play flavour's to dist/cleanyourtv-play-debug.apk).
set -euo pipefail
cd "$(dirname "$0")/.."

. scripts/lib/forge.sh
forge_init
IMAGE="${IMAGE:-eclipse-temurin:21-jdk}"
VOLUME="mitv-android-cache"
RUN_ID="cyt-$(date +%Y%m%d%H%M%S)-$$"
REMOTE_DIR="$FORGE_ROOT/$RUN_ID"
CONTAINER="$RUN_ID"
CMDLINE_TOOLS="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
# Licences only: AGP downloads the platform and build tools it needs into the shared SDK.
SDK_PACKAGES='"platform-tools"'

TASKS="${*:-check assembleDebug}"
# A fixed debug signing key kept outside git, so every debug APK updates in place on test phones
# (AGP would make a new one in each throwaway container). Absent: AGP's own throwaway key.
DEBUG_KEYSTORE="${DEBUG_KEYSTORE:-$CYT_SECRETS/cleanyourtv-debug/debug.keystore}"

[ -f third_party/cleanyourtv/README.md ] || {
  echo "upstream submodule is empty; run: git submodule update --init third_party/cleanyourtv" >&2
  exit 1
}

cleanup() {
  "${SSH[@]}" "docker rm -f '$CONTAINER' >/dev/null 2>&1 || true; rm -rf '$REMOTE_DIR'" </dev/null || true
}
trap cleanup EXIT INT TERM

echo "==> rsync to $FORGE:$REMOTE_DIR"
"${SSH[@]}" "mkdir -p '$REMOTE_DIR'" </dev/null
rsync -az --delete -e "ssh -i $SSH_KEY -o BatchMode=yes" \
  --exclude .git --exclude .gradle/ --exclude .kotlin/ --exclude build/ --exclude local.properties \
  --exclude dist/ --exclude .idea/ \
  ./ "$FORGE:$REMOTE_DIR/"

if [ -f "$DEBUG_KEYSTORE" ]; then
  "${SSH[@]}" "mkdir -p '$REMOTE_DIR/.android-user'" </dev/null
  scp -q -i "$SSH_KEY" -o BatchMode=yes "$DEBUG_KEYSTORE" "$FORGE:$REMOTE_DIR/.android-user/debug.keystore"
fi

echo "==> ./gradlew $TASKS in $IMAGE"
set +e
"${SSH[@]}" "flock -w 3600 '$LOCK' docker run --rm --name '$CONTAINER' --memory 3g --cpus 4 \
  -e HOME=/cache/home -e ANDROID_USER_HOME=/src/.android-user -e OWNER=\$(id -u):\$(id -g) \
  -v $VOLUME:/cache -v '$REMOTE_DIR':/src -w /src $IMAGE bash -euc '
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
    \$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager $SDK_PACKAGES >/dev/null
    echo sdk.dir=\$ANDROID_HOME > local.properties
    chmod +x gradlew
    status=0
    ./gradlew $TASKS --console=plain || status=\$?
    echo \"==> test results\"
    find . -path \"*/build/test-results/*\" -name \"*.xml\" -exec cat {} + 2>/dev/null \
      | grep -o \"<testsuite name=[^ ]* tests=[^ ]* skipped=[^ ]* failures=[^ ]* errors=[^ ]*\" || true
    find . -path \"*/build/test-results/*\" -name \"*.xml\" -exec grep -l \"<failure\" {} + 2>/dev/null || true
    for apk in \$(find app/build/outputs/apk -name \"*.apk\" 2>/dev/null); do ls -l \$apk; sha256sum \$apk; done
    exit \$status
  '" </dev/null
STATUS=$?
set -e

if [ "$STATUS" -eq 0 ] && [ -n "${FETCH_BACK:-}" ]; then
  for p in $FETCH_BACK; do
    echo "==> fetch back $p"
    rsync -az -e "ssh -i $SSH_KEY -o BatchMode=yes" "$FORGE:$REMOTE_DIR/$p" "$(dirname "$p")/"
  done
fi
if [ "$STATUS" -eq 0 ]; then
  for flavour in full play; do
    APK=$("${SSH[@]}" "ls '$REMOTE_DIR'/app/build/outputs/apk/$flavour/debug/*.apk 2>/dev/null | head -1" </dev/null || true)
    if [ -n "$APK" ]; then
      out=dist/cleanyourtv-debug.apk
      [ "$flavour" = play ] && out=dist/cleanyourtv-play-debug.apk
      mkdir -p dist
      scp -q -i "$SSH_KEY" "$FORGE:$APK" "$out"
      shasum -a 256 "$out"
    fi
  done
fi
echo "==> gradle exit $STATUS; tidying forge"
exit "$STATUS"
