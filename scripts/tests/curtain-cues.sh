#!/usr/bin/env bash
# Debug only: drive the TV curtain through all seven stages by hand, for a visual check on an
# emulator or a test TV that already has tv.cleanyour.curtain installed. Sends exactly the lines the
# phone's plan sends (am start, then one am broadcast per cue), with the pauses of a quick run.
#
#   ANDROID_SERIAL=emulator-5556 scripts/tests/curtain-cues.sh          # 12 switch-offs, 3 groups
#   ADB="adb -s 127.0.0.1:5555" OFFS=4 scripts/tests/curtain-cues.sh
#
# Never part of a plan; the phone app never runs this. Nothing here reads or writes secrets.
set -euo pipefail
ADB="${ADB:-adb}"
OFFS="${OFFS:-12}"
TOKEN="${TOKEN:-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')}"
RECEIVER="tv.cleanyour.curtain/.CueReceiver"

cue() {   # stage count total group gcount gtotal
  $ADB shell am broadcast -n "$RECEIVER" -a tv.cleanyour.curtain.CUE \
    --es token "$TOKEN" --es stage "$1" --es group "${4:-NONE}" \
    --ei count "${2:-0}" --ei total "${3:-0}" --ei gcount "${5:-0}" --ei gtotal "${6:-0}" --receiver-foreground >/dev/null
}

$ADB shell am start -n tv.cleanyour.curtain/.CurtainActivity >/dev/null
sleep 0.5
cue SCAN 0 "$OFFS"
cue RESTORE_SAVED
sleep 6
groups=(ADVERTS TRACKING NOBODY_ASKED)
k=0
for g in 0 1 2; do
  n=$(( OFFS / 3 + (g < OFFS % 3 ? 1 : 0) ))
  for i in $(seq 1 "$n"); do
    k=$((k + 1)); sleep 1.3
    cue OFF "$k" "$OFFS" "${groups[$g]}" "$i" "$n"
  done
  [ "$n" -gt 0 ] && cue GROUP_DONE "$k" "$OFFS" "${groups[$g]}" "$n" "$n"
done
sleep 1
cue INSTALL
sleep 6
cue INSTALLED
sleep 2
cue FINALE
echo "cued all seven stages with token $TOKEN"
