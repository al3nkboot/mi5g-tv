#!/usr/bin/env python3
# Copies a Mi TV capture folder (adb getprop, pm list, settings list dumps) into
# fixtures/conformance/ with device identifiers replaced: serial numbers, OEM keys, android_id,
# Bluetooth address and name, any MAC address, any private or CGNAT IPv4 address, any SSID.
# restore.sh is never copied (it names hosts and addresses of the capture machine).
# Usage: scripts/import-mitv-capture.py <capture dir> <dest dir>    (run with python3 -I)
import re
import sys
from pathlib import Path

FIXTURE_SERIAL = "FIXTURESERIAL0001"
MAC = re.compile(r"\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\b")
PRIVATE_IP = re.compile(
    r"\b(10\.\d{1,3}|172\.(1[6-9]|2\d|3[01])|192\.168|100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7]))\.\d{1,3}\.\d{1,3}\b"
)
PROP_REPLACE = {
    "ro.serialno": FIXTURE_SERIAL,
    "ro.boot.serialno": FIXTURE_SERIAL,
    "ro.oem.key1": "REDACTED",
}
SETTING_REPLACE = {
    "android_id": "0000000000000000",
    "bluetooth_address": "00:00:00:00:00:00",
    "bluetooth_name": "REDACTED",
}
SKIP = {"restore.sh"}


def redact_line(line: str, counts: dict) -> str:
    m = re.match(r"^\[([^\]]+)\]: \[(.*)\]$", line)
    if m and m.group(1) in PROP_REPLACE:
        counts[m.group(1)] = counts.get(m.group(1), 0) + 1
        return f"[{m.group(1)}]: [{PROP_REPLACE[m.group(1)]}]"
    m = re.match(r"^([^=]+)=(.*)$", line)
    if m and (m.group(1) in SETTING_REPLACE or "ssid" in m.group(1).lower()):
        counts[m.group(1)] = counts.get(m.group(1), 0) + 1
        return f"{m.group(1)}={SETTING_REPLACE.get(m.group(1), 'REDACTED')}"
    out, n = MAC.subn("00:00:00:00:00:00", line)
    if n:
        counts["mac"] = counts.get("mac", 0) + n
    out, n = PRIVATE_IP.subn("192.0.2.1", out)
    if n:
        counts["ipv4"] = counts.get("ipv4", 0) + n
    return out


def main() -> None:
    src, dest = Path(sys.argv[1]), Path(sys.argv[2])
    dest.mkdir(parents=True, exist_ok=True)
    counts: dict = {}
    for f in sorted(src.iterdir()):
        if not f.is_file() or f.name in SKIP:
            continue
        lines = f.read_text(encoding="utf-8").splitlines()
        (dest / f.name).write_text("".join(redact_line(l, counts) + "\n" for l in lines), encoding="utf-8")
        print(f"copied {f.name}")
    print("redactions:", ", ".join(f"{k} x{v}" for k, v in sorted(counts.items())) or "none")


if __name__ == "__main__":
    main()
