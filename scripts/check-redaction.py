#!/usr/bin/env python3
"""Redaction check (SECURITY T-P9, PRODUCT-SPEC 7.3 item 6, SECURITY 6.4 item 5).

Greps text for what must never leave the phone or reach a log: ADB keys and fingerprints, private
keys, tokens, pairing codes, device serials, android_id, full IP and MAC addresses, Wi-Fi names,
e-mail addresses, plus any exact secret values you know (the TV's real serial, the phone's serial,
your LAN address) given with --secret or --secrets-file.

Inputs: files, directories (walked), .zip files (every entry, e.g. a "Share diagnostics" zip or a
restore zip) and "-" for stdin, so a logcat can be piped in:

  adb logcat -d | scripts/check-redaction.py - --secret <tv serial> --secret <tv address>
  scripts/check-redaction.py diagnostics.zip restore.zip fixtures/

Patterns need a key or a shape: a bare serial (an `adb devices` line), a Wi-Fi name in free text
or a MAC without separators is only found when it is given with --secret, so always pass the real
TV serial, phone serial, LAN address and Wi-Fi name. Binary files and entries that are not zips or
UTF-16 text are listed as not checked.

Placeholders the code base uses for redacted values (REDACTED, FIXTURESERIAL0001, x.x masks) are
never findings. --allow REGEX drops matches that are known to be harmless (repeatable).
Exit 0 clean, 1 findings, 2 usage error. Stdlib only.
"""
import argparse
import io
import os
import re
import sys
import zipfile

RULES = [
    ("private-key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----")),
    # The same keys as bare base64 without the PEM lines: PKCS#8 RSA ("MII..IBADANBgkqhkiG9w0BAQEFAASC",
    # version 0 and rsaEncryption), PKCS#8 EC P-256, and a minisign secret key ("EdSc" + "B2").
    ("private-key-base64", re.compile(r"MII[A-Za-z0-9+/]{3}IBADANBgkqhkiG9w0BAQEFAASC|MIGHAgEAMBMGByqGSM49AgEGCCqGSM49|\bRWRTY0Iy[A-Za-z0-9+/]{40,}")),
    # The adbd public key line: the RSAPublicKey struct (524 bytes, word count 64 first, so the
    # base64 starts "QAAAA") and a comment. Other long base64 (public certificates, config blobs)
    # is not a key of ours.
    ("adb-public-key", re.compile(r"\bQAAAA[A-Za-z0-9+/]{400,}={0,2}")),
    ("key-fingerprint", re.compile(r"\b(?:[0-9A-Fa-f]{2}:){15}[0-9A-Fa-f]{2}\b")),
    ("github-token", re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{40,})\b")),
    ("auth-header", re.compile(r"(?i)\b(?:authorization|proxy-authorization)\s*[:=]\s*\S+")),
    ("bearer-token", re.compile(r"(?i)\bbearer\s+[A-Za-z0-9._~+/-]{12,}=*")),
    ("token-assignment", re.compile(r"(?i)\b(?:token|secret|password|passwd|api[_-]?key)[\"']?\s*[:=]\s*[\"']?[A-Za-z0-9._~+/-]{8,}")),
    ("pairing-code", re.compile(r"(?i)\b(?:pairing|wi-?fi pairing|pair)\s*code\D{0,12}\d{6}\b")),
    # Key and value, also quoted as in JSON ("serial":"...") and spelled out ("Serial number: ...").
    ("serial", re.compile(r"(?i)(?:\bro\.(?:boot\.)?serialno\]?\s*[:=]\s*\[?|\bserial(?:no|_?number| number)?[\"']?\s*[:=]\s*[\"']?)\[?([A-Za-z0-9]{6,})")),
    ("android-id", re.compile(r"(?i)\bandroid_id[\"']?\s*[:=]\s*[\"']?([0-9a-f]{16})\b")),
    ("mac-address", re.compile(r"\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b")),
    ("ipv4", re.compile(r"(?<![\d.])(?:25[0-5]|2[0-4]\d|1?\d?\d)(?:\.(?:25[0-5]|2[0-4]\d|1?\d?\d)){3}(?![\d.])")),
    # Full form, link local, and the compressed form with at least two groups before "::"
    # (fd12:3456::1, 2001:db8::8a2e:370:7334); "::1" and Kotlin's "Foo::bar" are not addresses.
    ("ipv6", re.compile(r"(?i)\b(?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4}\b|\bfe80::[0-9a-f:]{4,}"
                        r"|(?<![0-9a-z:])(?:[0-9a-f]{1,4}:){2,6}:(?:[0-9a-f]{1,4}:){0,5}[0-9a-f]{1,4}(?![0-9a-z:])")),
    ("wifi-name", re.compile(r"(?i)\b(?:ssid|wifi_ssid|network name)[\"']?\s*[:=]\s*[\"']?([^\"',\s]{2,})")),
    ("email", re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b")),
]

# Values that are placeholders or not identifying, per rule.
# "x.x" only as its own word (192.168.x.x), not inside a real value such as "Box.Xfinity".
PLACEHOLDERS = re.compile(r"(?i)REDACTED|FIXTURESERIAL\d*|\bx\.x\b|<[a-z _-]+>|<unknown|\*{3,}|[:=]\s*[\"']?0{16}\b")
# Documentation ranges (RFC 5737), which redacted captures use on purpose.
DOC_NET = re.compile(r"^(?:192\.0\.2|198\.51\.100|203\.0\.113)\.\d+$")
HARMLESS = {
    "mac-address": {"00:00:00:00:00:00", "02:00:00:00:00:00", "ff:ff:ff:ff:ff:ff"},
    "ipv4": {"0.0.0.0", "127.0.0.1", "255.255.255.255", "255.255.255.0", "1.1.1.1", "8.8.8.8"},
}
EMAIL_OK = re.compile(r"(?i)@(?:example\.(?:com|org)|users\.noreply\.github\.com)$")


def mask(text):
    text = text.strip()
    if len(text) <= 8:
        return text[:2] + "*" * (len(text) - 2)
    return text[:4] + "..." + text[-2:] + f" ({len(text)} chars)"


class Checker:
    def __init__(self, secrets, allows, skip_rules):
        self.secrets = [s for s in secrets if len(s) >= 4]
        self.allows = [re.compile(a) for a in allows]
        self.skip = set(skip_rules)
        self.findings = []
        self.skipped = []

    def allowed(self, rule, value):
        if PLACEHOLDERS.search(value):
            return True
        if value.strip().lower() in {v.lower() for v in HARMLESS.get(rule, set())}:
            return True
        if rule == "ipv4" and DOC_NET.match(value.strip()):
            return True
        if rule == "email" and EMAIL_OK.search(value):
            return True
        return any(a.search(value) for a in self.allows)

    def line(self, where, n, text):
        for name, rx in RULES:
            if name in self.skip:
                continue
            for m in rx.finditer(text):
                value = m.group(0)
                if self.allowed(name, value):
                    continue
                self.findings.append((where, n, name, mask(value)))
        low = text.lower()
        for s in self.secrets:
            if s.lower() in low:
                self.findings.append((where, n, "known-secret", mask(s)))

    def text(self, where, stream):
        for n, raw in enumerate(stream, 1):
            if isinstance(raw, bytes):
                raw = raw.decode("utf-8", "replace")
            self.line(where, n, raw.rstrip("\n"))

    def data(self, where, data):
        """One file's bytes: a nested zip is opened, UTF-16 text (PowerShell, some Windows tools)
        is decoded, other binary content is listed as skipped instead of passing in silence."""
        if data[:4] == b"PK\x03\x04" and not where.endswith((".apk", ".jar", ".aar")):
            try:
                with zipfile.ZipFile(io.BytesIO(data)) as z:
                    for info in z.infolist():
                        if not info.is_dir():
                            self.data(f"{where}!{info.filename}", z.read(info))
                return
            except zipfile.BadZipFile:
                pass
        head = data[:4096]
        if head.startswith((b"\xff\xfe", b"\xfe\xff")) or (len(head) >= 4 and head[1::2].count(0) > len(head) // 4):
            enc = "utf-16" if head.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-16-le"
            self.text(where, io.StringIO(data.decode(enc, "replace")))
            return
        if b"\x00" in head:
            self.skipped.append(where)
            return
        self.text(where, io.BytesIO(data))

    def path(self, p):
        if p == "-":
            self.text("<stdin>", sys.stdin)
        elif os.path.isdir(p):
            for root, dirs, files in os.walk(p):
                dirs[:] = [d for d in dirs if d not in {".git", "build", ".gradle"}]
                for f in sorted(files):
                    self.path(os.path.join(root, f))
        else:
            with open(p, "rb") as f:
                self.data(p, f.read())


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("inputs", nargs="+", help="files, directories, zips, or - for stdin")
    ap.add_argument("--secret", action="append", default=[], help="an exact value that must not appear (repeatable)")
    ap.add_argument("--secrets-file", help="one exact secret value per line")
    ap.add_argument("--allow", action="append", default=[], help="regex of matches to ignore (repeatable)")
    ap.add_argument("--skip-rule", action="append", default=[], help="rule name to skip (repeatable)")
    ap.add_argument("--quiet", action="store_true", help="print the summary only")
    a = ap.parse_args(argv)
    secrets = list(a.secret)
    if a.secrets_file:
        with open(a.secrets_file) as f:
            secrets += [l.strip() for l in f if l.strip() and not l.startswith("#")]
    c = Checker(secrets, a.allow, a.skip_rule)
    for p in a.inputs:
        if p != "-" and not os.path.exists(p):
            print(f"error: no such input {p}", file=sys.stderr)
            return 2
        c.path(p)
    if not a.quiet:
        for where, n, rule, value in c.findings:
            print(f"{where}:{n}: {rule}: {value}")
    for where in c.skipped:
        print(f"skipped (binary, not checked): {where}", file=sys.stderr)
    print(f"check-redaction: {len(c.findings)} finding(s) in {len(a.inputs)} input(s)"
          + (f", {len(c.skipped)} binary file(s) not checked" if c.skipped else ""))
    return 1 if c.findings else 0


if __name__ == "__main__":
    sys.exit(main())
