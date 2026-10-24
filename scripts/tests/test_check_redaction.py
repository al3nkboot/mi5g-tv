"""Tests for scripts/check-redaction.py. Run: python3 -m unittest discover -s scripts/tests"""
import contextlib
import importlib.util
import io
import os
import tempfile
import unittest
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("check_redaction", os.path.join(HERE, "..", "check-redaction.py"))
cr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cr)

ADB_KEY = "QAAAA" + "B" * 690 + "= cleanyourtv"


def findings(text, **kw):
    c = cr.Checker(kw.get("secrets", []), kw.get("allows", []), kw.get("skip", []))
    c.text("t", io.StringIO(text))
    return [f[2] for f in c.findings]


class Rules(unittest.TestCase):
    def test_each_leak_is_found(self):
        cases = {
            "private-key": "-----BEGIN PRIVATE KEY-----",
            "adb-public-key": ADB_KEY,
            "key-fingerprint": "Allow fingerprint 4E:21:09:AA:BC:7F:2A:11:00:FF:EE:DD:CC:BB:AA:99",
            "github-token": "ghp_" + "a1" * 18,
            "bearer-token": "Bearer abcdefghijklmnop",
            "pairing-code": "Wi-Fi pairing code: 482913",
            "serial": "[ro.serialno]: [ABC123XYZ789]",
            "android-id": "android_id=0123456789abcdef",
            "mac-address": "bt 4c:0b:be:12:34:56",
            "ipv4": "connect 192.168.1.23:5555",
            "wifi-name": 'SSID: "HomeNet"',
            "email": "mail someone@gmail.com",
            "private-key-base64": "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC09WnL",
        }
        for rule, text in cases.items():
            with self.subTest(rule=rule):
                self.assertIn(rule, findings(text))

    def test_quoted_spelled_out_and_compressed_forms_are_found(self):
        # R5 probes: JSON keys, "Serial number", compressed IPv6, a value that contains "x.x".
        cases = {
            "serial": ['{"serial":"ABC123XYZ789"}', '{"serial": "ABC123XYZ789"}', "Serial number: ABC123XYZ789"],
            "wifi-name": ['{"ssid":"HomeNet"}', 'SSID: "Box.Xfinity"'],
            "token-assignment": ['{"token":"abcdef1234567890"}', '{"password": "hunter22hunter"}'],
            "android-id": ['{"android_id":"0123456789abcdef"}'],
            "ipv6": ["addr fd12:3456:789a::1", "2001:db8::8a2e:370:7334"],
            "email": ["max.xu@gmail.com"],
            "private-key-base64": ["MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg", "RWRTY0Iy" + "A" * 60],
        }
        for rule, texts in cases.items():
            for text in texts:
                with self.subTest(rule=rule, text=text):
                    self.assertIn(rule, findings(text))

    def test_placeholders_and_harmless_values_pass(self):
        clean = "\n".join([
            "[ro.serialno]: [FIXTURESERIAL0001]",
            "bluetooth_name=REDACTED",
            "bind 127.0.0.1 and 0.0.0.0",
            "mac 02:00:00:00:00:00",
            "TV at 192.168.x.x",
            "documentation address 192.0.2.1",
            "android_id=0000000000000000",
            "config_update_certificate=MIID" + "A" * 500 + "==",
            "fingerprint ends in 7F:2A",
            "author noreply@example.com",
            "Switching debugging off... done",
            'mWifiInfo SSID: <unknown ssid>, BSSID: 02:00:00:00:00:00',
            '{"android_id":"0000000000000000"}',
            "val k = String::class; loopback ::1; ref Foo::bar",
            "version 1.2.3 build 36.0.0",
        ])
        self.assertEqual([], findings(clean))

    def test_known_secrets_are_found_case_insensitively(self):
        self.assertEqual(["known-secret"], findings("serial is abc123def", secrets=["ABC123DEF"]))

    def test_allow_drops_a_match(self):
        self.assertEqual([], findings("version 10.2.3.4", allows=[r"^10\.2\.3\.4$"]))

    def test_mask_never_prints_the_whole_value(self):
        self.assertNotIn("192.168.1.23", cr.mask("192.168.1.23"))


class Inputs(unittest.TestCase):
    def test_zip_entries_and_exit_codes(self):
        with tempfile.TemporaryDirectory() as d:
            clean = os.path.join(d, "clean.zip")
            with zipfile.ZipFile(clean, "w") as z:
                z.writestr("restore.sh", "pm enable --user 0 com.example.app\n")
            leaky = os.path.join(d, "leaky.zip")
            with zipfile.ZipFile(leaky, "w") as z:
                z.writestr("log.txt", "connected to 192.168.1.23\n")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, cr.main([clean]))
                self.assertEqual(1, cr.main([leaky]))

    def test_utf16_nested_zips_and_binaries(self):
        with tempfile.TemporaryDirectory() as d:
            u16 = os.path.join(d, "restore-log.txt")
            with open(u16, "wb") as f:
                f.write("adb connect 192.168.1.23\r\n".encode("utf-16"))
            inner = io.BytesIO()
            with zipfile.ZipFile(inner, "w") as z:
                z.writestr("log.txt", "connected to 192.168.1.23\n")
            outer = os.path.join(d, "outer.zip")
            with zipfile.ZipFile(outer, "w") as z:
                z.writestr("diagnostics.zip", inner.getvalue())
                z.writestr("screen.png", b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR")
            c = cr.Checker([], [], [])
            c.path(u16)
            c.path(outer)
            self.assertEqual([u16, outer + "!diagnostics.zip!log.txt"], [f[0] for f in c.findings])
            self.assertEqual([outer + "!screen.png"], c.skipped)

    def test_repo_fixtures_are_clean(self):
        # The redacted Mi TV capture W1 imported must stay clean under this checker too.
        fixtures = os.path.join(HERE, "..", "..", "fixtures", "conformance")
        if not os.path.isdir(fixtures):
            self.skipTest("no fixtures in this checkout")
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            status = cr.main([fixtures])
        self.assertEqual(0, status, out.getvalue())


if __name__ == "__main__":
    unittest.main()
