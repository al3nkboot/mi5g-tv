#!/usr/bin/env bash
# One short Android TV emulator session, inside the emulator container (docker/emulator) or on a
# CI runner with KVM. Boots a fresh AVD, runs the given command with the CYT_E2E_* variables set
# (the endpoint is the emulator's adb port, 127.0.0.1:5555, which speaks the ADB protocol over
# TCP like a TV with network debugging on), and always kills the emulator afterwards, also on
# failure or a signal.
#
#   scripts/emulator-session.sh <api> <command...>
#   scripts/emulator-session.sh 28 ./gradlew :e2e:emulatorTest
#
# EMU_SDK (default /emu/sdk) holds emulator, platform-tools, cmdline-tools and the system image
# system-images;android-<api>;android-tv;x86. EMU_MEMORY_MB (default 1024, the Mi TV 4A's RAM)
# sets the guest RAM. BOOT_TIMEOUT_S (default 420) bounds the cold boot.
set -euo pipefail

API="${1:?usage: emulator-session.sh <api> <command...>}"
shift
EMU_SDK="${EMU_SDK:-/emu/sdk}"
EMU_MEMORY_MB="${EMU_MEMORY_MB:-1024}"
BOOT_TIMEOUT_S="${BOOT_TIMEOUT_S:-420}"
IMAGE="system-images;android-$API;android-tv;x86"
AVD="cyt-tv$API"
SERIAL="emulator-5554"
LOG_DIR="${CYT_E2E_OUT:-$PWD/build/e2e}"

export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-/tmp/avd}"
export ANDROID_EMULATOR_HOME="${ANDROID_EMULATOR_HOME:-/tmp/emuhome}"
# A private HOME for adb: the session gets a fresh host key in ~/.android, never a shared one.
# Gradle keeps its own home (and its caches) whatever HOME becomes.
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
export HOME="${E2E_HOME:-/tmp/e2e-home}"
mkdir -p "$ANDROID_AVD_HOME" "$ANDROID_EMULATOR_HOME" "$HOME/.android" "$LOG_DIR"
# An empty console token turns console auth off for this throwaway emulator.
: > "$HOME/.emulator_console_auth_token"

ADB="$EMU_SDK/platform-tools/adb"
emu_env=(env ANDROID_HOME="$EMU_SDK" ANDROID_SDK_ROOT="$EMU_SDK")

[ -d "$EMU_SDK/system-images/android-$API/android-tv/x86" ] || {
  echo "missing $IMAGE under $EMU_SDK; run scripts/emulator-on-forge.sh prepare $API" >&2
  exit 2
}

EMU_PID=""
teardown() {
  set +e
  echo "==> emulator teardown"
  "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1
  if [ -n "$EMU_PID" ]; then
    for _ in $(seq 1 20); do kill -0 "$EMU_PID" 2>/dev/null || break; sleep 1; done
    kill -9 "$EMU_PID" >/dev/null 2>&1
  fi
  pkill -9 -f qemu-system >/dev/null 2>&1
  "$ADB" kill-server >/dev/null 2>&1
  cp /tmp/emulator.log "$LOG_DIR/emulator-api$API.log" 2>/dev/null
  rm -rf "$ANDROID_AVD_HOME/$AVD.avd" "$ANDROID_AVD_HOME/$AVD.ini"
}
# A signal ends the script (the EXIT trap then tears down once); a bare INT or TERM trap would run
# teardown and carry on with the next command.
trap teardown EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

echo "==> create AVD $AVD from $IMAGE"
echo no | "${emu_env[@]}" "$EMU_SDK/cmdline-tools/latest/bin/avdmanager" -s create avd \
  -n "$AVD" -k "$IMAGE" -d tv_1080p --force >/dev/null
# No snapshots, no hardware keyboard surprises, the 1 GB of the reference TV.
cat >> "$ANDROID_AVD_HOME/$AVD.avd/config.ini" <<EOF
hw.ramSize=$EMU_MEMORY_MB
hw.keyboard=yes
hw.audioInput=no
hw.audioOutput=no
EOF

"$ADB" start-server >/dev/null
echo "==> boot (guest RAM $EMU_MEMORY_MB MB)"
started=$(date +%s)
"${emu_env[@]}" "$EMU_SDK/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim \
  -no-snapshot -wipe-data -gpu swiftshader_indirect -accel on -cores 2 -memory "$EMU_MEMORY_MB" \
  -camera-back none -camera-front none -port 5554 >/tmp/emulator.log 2>&1 &
EMU_PID=$!

deadline=$((started + BOOT_TIMEOUT_S))
until [ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  if ! kill -0 "$EMU_PID" 2>/dev/null; then
    echo "emulator exited during boot; last log lines:" >&2
    tail -40 /tmp/emulator.log >&2
    exit 3
  fi
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "boot timed out after ${BOOT_TIMEOUT_S}s; last log lines:" >&2
    tail -40 /tmp/emulator.log >&2
    exit 4
  fi
  sleep 3
done
boot_s=$(( $(date +%s) - started ))
echo "==> booted in ${boot_s}s"

# Quieter, faster UI for the harness; the screen stays on.
"$ADB" -s "$SERIAL" shell settings put global window_animation_scale 0 || true
"$ADB" -s "$SERIAL" shell settings put global transition_animation_scale 0 || true
"$ADB" -s "$SERIAL" shell settings put global animator_duration_scale 0 || true
"$ADB" -s "$SERIAL" shell svc power stayon true || true

# The harness speaks to the emulator's adb port (console port + 1) directly: a raw TCP endpoint
# that answers CNXN like a TV with network debugging on. `adb tcpip` is not used: on the emulator
# it restarts adbd without a guest TCP listener (seen on the API 28 TV image, 2026-10-19).
export CYT_E2E_ADB="$ADB"
export CYT_E2E_SERIAL="$SERIAL"
export CYT_E2E_ENDPOINT="127.0.0.1:5555"
export CYT_E2E_ADBKEY="$HOME/.android/adbkey"
export CYT_E2E_API="$API"
export CYT_E2E_OUT="$LOG_DIR"
export CYT_E2E_BOOT_SECONDS="$boot_s"

{
  echo "api=$API"
  echo "boot_seconds=$boot_s"
  echo "fingerprint=$("$ADB" -s "$SERIAL" shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "adb_secure=$("$ADB" -s "$SERIAL" shell getprop ro.adb.secure | tr -d '\r')"
} > "$LOG_DIR/session-api$API.properties"
"$ADB" -s "$SERIAL" shell pm list packages -f | tr -d '\r' | sort > "$LOG_DIR/packages-api$API.txt" || true
"$ADB" -s "$SERIAL" shell pm list packages -d | tr -d '\r' | sort > "$LOG_DIR/packages-disabled-api$API.txt" || true
"$ADB" -s "$SERIAL" shell cmd package resolve-activity --brief -c android.intent.category.HOME \
  -a android.intent.action.MAIN | tr -d '\r' > "$LOG_DIR/home-api$API.txt" || true

echo "==> run: $*"
set +e
"$@"
status=$?
set -e
echo "==> command exit $status"
exit "$status"
