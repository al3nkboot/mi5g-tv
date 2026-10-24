#!/usr/bin/env bash
# Session smoke check, run by scripts/emulator-session.sh after boot: the host oracle sees the
# emulator, adbd answers a CNXN on the forwarded TCP endpoint (it must ask for AUTH, as a TV with
# ro.adb.secure=1 does), and the memory the session uses is printed for the 3 GB budget.
set -euo pipefail
: "${CYT_E2E_ADB:?run inside scripts/emulator-session.sh}"
adb() { "$CYT_E2E_ADB" -s "$CYT_E2E_SERIAL" "$@"; }

echo "== devices"; "$CYT_E2E_ADB" devices -l
echo "== props"
for p in ro.build.fingerprint ro.build.version.sdk ro.product.model ro.product.device ro.adb.secure \
  ro.debuggable service.adb.tcp.port; do echo "$p=$(adb shell getprop "$p" | tr -d '\r')"; done
echo "== home"; adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tr -d '\r'
echo "== tools"; adb shell 'for t in sha256sum pkill setsid nohup cmd; do command -v $t || echo "missing $t"; done' | tr -d '\r'
echo "== packages: $(adb shell pm list packages | wc -l) total, $(adb shell pm list packages -d | wc -l) disabled"

echo "== CNXN on $CYT_E2E_ENDPOINT"
python3 - "$CYT_E2E_ENDPOINT" <<'EOF'
import socket, struct, sys
host, port = sys.argv[1].split(":")
def msg(cmd, a0, a1, data):
    c = struct.unpack("<I", cmd)[0]
    return struct.pack("<6I", c, a0, a1, len(data), sum(data) & 0xFFFFFFFF, c ^ 0xFFFFFFFF) + data
s = socket.create_connection((host, int(port)), timeout=10)
s.sendall(msg(b"CNXN", 0x01000001, 256 * 1024, b"host::cyt-e2e-smoke\0"))
hdr = b""
while len(hdr) < 24:
    chunk = s.recv(24 - len(hdr))
    if not chunk: raise SystemExit("adbd closed the connection before answering")
    hdr += chunk
cmd, a0, a1, n, _, _ = struct.unpack("<6I", hdr)
name = struct.pack("<I", cmd).decode("ascii", "replace")
print(f"adbd answered {name} arg0={a0} payload={n} bytes")
if name not in ("AUTH", "CNXN"): raise SystemExit("unexpected first reply")
EOF

echo "== memory"
free -m || true
ps -eo rss,comm --sort=-rss | head -6 || true
