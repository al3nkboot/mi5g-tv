#!/usr/bin/env bash
# Android TV emulator sessions on forge (W9). forge has /dev/kvm but only about 7 GB of free
# memory beside its model server, so a session is short, runs in one container capped at 3 GB,
# holds the same host lock as scripts/build-on-forge.sh (no Gradle build runs beside it), and is
# always torn down: container, emulator and the remote checkout, also on failure or Ctrl-C.
#
#   scripts/emulator-on-forge.sh prepare [api...]       # image + SDK volume (default api 28)
#   scripts/emulator-on-forge.sh run [api]              # e2e harness session (default api 28)
#   scripts/emulator-on-forge.sh run 28 -- <command>    # any command inside the session
#
# prepare builds the docker/emulator image (tagged by the Dockerfile's hash) and fills the
# cyt-emulator-sdk volume with cmdline-tools, platform-tools, the emulator and the Android TV
# system image per API. It downloads about 1 GB once and builds nothing, so it takes no lock.
#
# run rsyncs the checkout like build-on-forge.sh, compiles the e2e classes and builds the e2e
# fixture APKs first (scripts/build-e2e-fixtures.sh; E2E_FIXTURES=0 skips them), so Gradle and the
# emulator never hold memory at the same time, stops Gradle, boots the emulator through
# scripts/emulator-session.sh, runs :e2e:emulatorTest (or the command after --) and copies
# build/e2e (timings, logs, package lists) back to build/e2e-forge/ here.
set -euo pipefail
cd "$(dirname "$0")/.."

. scripts/lib/forge.sh
BUILD_VOLUME="mitv-android-cache"
EMU_VOLUME="cyt-emulator-sdk"
CMDLINE_TOOLS="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
SESSION_TIMEOUT_S="${SESSION_TIMEOUT_S:-2400}"
forge_init -o ServerAliveInterval=30 -o ServerAliveCountMax=4

TAG="cyt-emu:$(shasum -a 256 docker/emulator/Dockerfile | cut -c1-12)"
MODE="${1:-run}"
shift || true

build_image() {
  echo "==> image $TAG"
  "${SSH[@]}" "docker image inspect '$TAG' >/dev/null 2>&1" </dev/null && return 0
  "${SSH[@]}" "docker build -q -t '$TAG' -" < docker/emulator/Dockerfile
}

case "$MODE" in
prepare)
  APIS="${*:-28}"
  build_image
  PKGS='"platform-tools" "emulator"'
  for a in $APIS; do PKGS="$PKGS \"system-images;android-$a;android-tv;x86\""; done
  echo "==> SDK volume $EMU_VOLUME: $PKGS"
  "${SSH[@]}" "docker run --rm --memory 1g -v $EMU_VOLUME:/emu '$TAG' bash -euc '
      sdk=/emu/sdk
      if [ ! -x \$sdk/cmdline-tools/latest/bin/sdkmanager ]; then
        mkdir -p \$sdk/cmdline-tools && cd \$sdk/cmdline-tools
        curl -fsSL -o tools.zip $CMDLINE_TOOLS && unzip -q tools.zip && rm tools.zip
        rm -rf latest && mv cmdline-tools latest
      fi
      yes | \$sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=\$sdk --licenses >/dev/null
      \$sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=\$sdk $PKGS | tr \"\\r\" \"\\n\" | grep -v \"^\\[\" | grep -v \"^ *\$\" | tail -5 || true
      \$sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=\$sdk --list_installed | sed -n \"/Installed/,\\\$p\"
      du -sh \$sdk'" </dev/null
  ;;
run)
  API=28
  if [ $# -gt 0 ] && [ "$1" != "--" ]; then API="$1"; shift; fi
  [ "${1:-}" = "--" ] && shift
  CMD="${*:-./gradlew -Dorg.gradle.jvmargs=-Xmx768m :e2e:emulatorTest --console=plain}"
  [ -f third_party/cleanyourtv/README.md ] || { echo "upstream submodule is empty" >&2; exit 1; }
  PRECOMPILE=0
  FIXTURES="${E2E_FIXTURES:-1}"   # 0 skips building the launcher APKs the install scenarios need
  case "$CMD" in *gradlew*) PRECOMPILE=1 ;; esac
  build_image

  RUN_ID="cyt-emu-$(date +%Y%m%d%H%M%S)-$$"
  REMOTE_DIR="$FORGE_ROOT/$RUN_ID"
  cleanup() {
    "${SSH[@]}" "docker rm -f '$RUN_ID' >/dev/null 2>&1 || true; rm -rf '$REMOTE_DIR'" </dev/null || true
  }
  trap cleanup EXIT INT TERM

  echo "==> rsync to $FORGE:$REMOTE_DIR"
  "${SSH[@]}" "mkdir -p '$REMOTE_DIR'" </dev/null
  rsync -az --delete -e "ssh -i $SSH_KEY -o BatchMode=yes" \
    --exclude .git --exclude .gradle/ --exclude .kotlin/ --exclude build/ --exclude local.properties \
    --exclude dist/ --exclude .idea/ ./ "$FORGE:$REMOTE_DIR/"

  echo "==> session api $API (3 GB cap, host lock): $CMD"
  set +e
  # Two clocks. `timeout` inside the container is its PID 1, so the container ends by itself after
  # SESSION_TIMEOUT_S even when this ssh session is gone (killing the docker client on forge would
  # leave the container and its emulator running without the lock); the outer one is a backstop.
  "${SSH[@]}" "flock -w 3600 $LOCK timeout --kill-after=30 $((SESSION_TIMEOUT_S + 120)) docker run --rm --name '$RUN_ID' \
    --memory 3g --memory-swap 3g --cpus 4 --device /dev/kvm \
    -e OWNER=\$(id -u):\$(id -g) -e GRADLE_USER_HOME=/cache/gradle \
    -v $BUILD_VOLUME:/cache -v $EMU_VOLUME:/emu -v '$REMOTE_DIR':/src -w /src '$TAG' \
    timeout --kill-after=30 $SESSION_TIMEOUT_S bash -euc '
      trap \"chown -R \$OWNER /src\" EXIT
      export ANDROID_HOME=/cache/sdk
      echo sdk.dir=/cache/sdk > local.properties
      chmod +x gradlew scripts/*.sh
      if [ $PRECOMPILE = 1 ]; then
        echo \"==> compile the harness before the emulator takes its memory\"
        ./gradlew :e2e:emulatorTestClasses --console=plain -q
        ./gradlew --stop >/dev/null
        if [ $FIXTURES = 1 ]; then
          scripts/build-e2e-fixtures.sh -o /src/build/e2e-fixtures.env
          set -a; . /src/build/e2e-fixtures.env; set +a
        fi
      fi
      export GRADLE_OPTS=-Xmx256m CYT_E2E_OUT=/src/build/e2e
      scripts/emulator-session.sh $API $CMD
    '" </dev/null
  STATUS=$?
  set -e
  mkdir -p build/e2e-forge
  rsync -az -e "ssh -i $SSH_KEY -o BatchMode=yes" "$FORGE:$REMOTE_DIR/build/e2e/" build/e2e-forge/ 2>/dev/null || true
  rsync -az -e "ssh -i $SSH_KEY -o BatchMode=yes" "$FORGE:$REMOTE_DIR/e2e/build/test-results/" build/e2e-forge/test-results/ 2>/dev/null || true
  echo "==> session exit $STATUS; results in build/e2e-forge; tidying forge"
  "${SSH[@]}" "docker ps --filter name=$RUN_ID --format '{{.Names}}'" </dev/null || true
  exit "$STATUS"
  ;;
*)
  echo "usage: $0 prepare [api...] | run [api] [-- command]" >&2
  exit 2
  ;;
esac
