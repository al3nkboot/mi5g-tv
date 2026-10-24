#!/usr/bin/env bash
# PRODUCT-SPEC 7.3 item 3, on a device: the installed app is the release (same sha256), its
# manifest passes scripts/check-apk.py, and `dumpsys package` shows no debuggable or backup flag
# and only the permissions of the build's store flavour (D16 plus USE_BIOMETRIC from RFC-W8-7, the
# same for full and play; the list comes from scripts/check-apk.py --print-permissions, so the two
# checks cannot drift apart). For the user's phone during the session (runbook section B) and for
# the phone emulators in nightly.yml.
#
#   scripts/check-installed.sh [expected-apk-sha256]      # adb must reach exactly one device, or set ANDROID_SERIAL
#   CYT_FLAVOUR=play scripts/check-installed.sh            # an install from Google Play (default: full)
set -euo pipefail
cd "$(dirname "$0")/.."
PKG=tv.cleanyour.app
EXPECT="${1:-}"
FLAVOUR="${CYT_FLAVOUR:-full}"
case "$FLAVOUR" in full|play) ;; *) echo "CYT_FLAVOUR must be full or play, not $FLAVOUR" >&2; exit 2 ;; esac
expected=$(python3 scripts/check-apk.py --print-permissions --flavour "$FLAVOUR" | LC_ALL=C sort -u)
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

# The base APK (an install from Google Play also has split APKs next to it).
paths=$(adb shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p')
path=$(printf '%s\n' "$paths" | grep '/base\.apk$' | head -1 || true)
[ -n "$path" ] || path=$(printf '%s\n' "$paths" | head -1)
[ -n "$path" ] || { echo "$PKG is not installed" >&2; exit 1; }
adb pull "$path" "$TMP/base.apk" >/dev/null
sum=$( (sha256sum "$TMP/base.apk" 2>/dev/null || shasum -a 256 "$TMP/base.apk") | cut -d' ' -f1)
echo "installed APK sha256: $sum"
status=0
if [ -n "$EXPECT" ] && [ "$sum" != "$EXPECT" ]; then echo "FAIL installed APK is not the release ($EXPECT)"; status=1; fi

python3 scripts/check-apk.py "$TMP/base.apk" --flavour "$FLAVOUR" || status=1

adb shell dumpsys package "$PKG" | tr -d '\r' > "$TMP/dumpsys.txt"
flags=$(grep -m1 -E '^\s+pkgFlags=' "$TMP/dumpsys.txt" || true)
echo "dumpsys $flags"
case "$flags" in *DEBUGGABLE*) echo "FAIL debuggable"; status=1;; esac
case "$flags" in *ALLOW_BACKUP*) echo "FAIL allowBackup"; status=1;; esac
granted=$(sed -n '/requested permissions:/,/install permissions:/p' "$TMP/dumpsys.txt" | grep -o 'android\.permission\.[A-Z_]*' | LC_ALL=C sort -u)
echo "requested permissions:"; echo "$granted" | sed 's/^/  /'
if [ "$granted" != "$expected" ]; then
  echo "FAIL requested permissions differ from the $FLAVOUR build's (D16 plus USE_BIOMETRIC):"
  diff <(echo "$expected") <(echo "$granted") | sed -n 's/^</  missing/p; s/^>/  extra/p' || true
  status=1
fi
[ "$status" = 0 ] && echo "check-installed: ok" || echo "check-installed: FAILED"
exit "$status"
