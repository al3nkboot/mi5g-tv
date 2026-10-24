"""Tests for scripts/play-upload.py: the RS256 signing, the input checks and --dry-run, with no
network call. A throwaway RSA key is made per run with openssl (the tests that need one are
skipped without it). Run: python3 -m unittest discover -s scripts/tests"""
import base64
import contextlib
import hashlib
import importlib.util
import io
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("play_upload", os.path.join(HERE, "..", "play-upload.py"))
pu = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pu)

OPENSSL = shutil.which("openssl")


def run_main(argv):
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = pu.main(argv)
    return code, out.getvalue(), err.getvalue()


@unittest.skipUnless(OPENSSL, "openssl is needed to make a throwaway key")
class PlayUpload(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dir = tempfile.mkdtemp()
        cls.key = os.path.join(cls.dir, "key.pem")
        subprocess.run([OPENSSL, "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", cls.key],
                       check=True, capture_output=True)
        subprocess.run([OPENSSL, "pkey", "-in", cls.key, "-pubout", "-out", os.path.join(cls.dir, "pub.pem")], check=True, capture_output=True)
        with open(cls.key) as f:
            cls.pem = f.read()

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.dir, ignore_errors=True)

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.account = os.path.join(self.tmp, "service-account.json")
        with open(self.account, "w") as f:
            json.dump({"type": "service_account", "client_email": "uploader@example.iam.gserviceaccount.com",
                       "private_key": self.pem, "token_uri": "https://oauth2.googleapis.com/token"}, f)
        os.chmod(self.account, 0o600)
        self.aab = os.path.join(self.tmp, "app.aab")
        with zipfile.ZipFile(self.aab, "w") as z:
            for n in ("META-INF/MANIFEST.MF", "META-INF/UPLOAD.SF", "META-INF/UPLOAD.RSA"):
                z.writestr(n, b"sig")
            z.writestr("BundleConfig.pb", b"cfg")
            z.writestr("base/manifest/AndroidManifest.xml", b"m")
        self.notes = os.path.join(self.tmp, "metadata")
        os.makedirs(os.path.join(self.notes, "en-US", "changelogs"))
        with open(os.path.join(self.notes, "en-US", "changelogs", "3.txt"), "w") as f:
            f.write("The Google Play build.\n")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def args(self, *extra):
        return ["--aab", self.aab, "--version-code", "3", "--version-name", "0.3.0", "--notes-dir", self.notes,
                "--service-account", self.account, "--dry-run", *extra]

    def test_rs256_verifies_with_openssl(self):
        n, d = pu.rsa_key_from_pem(self.pem)
        msg = b"header.claims"
        sig = os.path.join(self.tmp, "sig")
        data = os.path.join(self.tmp, "msg")
        with open(sig, "wb") as f:
            f.write(pu.rs256(msg, n, d))
        with open(data, "wb") as f:
            f.write(msg)
        r = subprocess.run([OPENSSL, "dgst", "-sha256", "-verify", os.path.join(self.dir, "pub.pem"), "-signature", sig, data],
                           capture_output=True, text=True)
        self.assertIn("Verified OK", r.stdout)

    def test_the_pkcs1_form_reads_the_same_key(self):
        pkcs1 = subprocess.run([OPENSSL, "pkey", "-in", self.key, "-traditional"], capture_output=True, text=True)
        if "RSA PRIVATE KEY" not in pkcs1.stdout:
            self.skipTest("this openssl writes no PKCS#1 form")
        self.assertEqual(pu.rsa_key_from_pem(self.pem), pu.rsa_key_from_pem(pkcs1.stdout))

    def test_the_jwt_names_the_account_scope_and_audience(self):
        account, _ = pu.load_service_account(self.account)
        token = pu.jwt_assertion(account, 1_700_000_000)
        header, claims, sig = token.split(".")
        pad = lambda s: s + "=" * (-len(s) % 4)
        self.assertEqual({"alg": "RS256", "typ": "JWT"}, json.loads(base64.urlsafe_b64decode(pad(header))))
        c = json.loads(base64.urlsafe_b64decode(pad(claims)))
        self.assertEqual(("uploader@example.iam.gserviceaccount.com", pu.SCOPE, "https://oauth2.googleapis.com/token", 3600),
                         (c["iss"], c["scope"], c["aud"], c["exp"] - c["iat"]))
        self.assertEqual(256, len(base64.urlsafe_b64decode(pad(sig))))

    def test_dry_run_checks_everything_and_sends_nothing(self):
        sent = []
        real = urllib.request.urlopen
        urllib.request.urlopen = lambda *a, **k: sent.append(a) or (_ for _ in ()).throw(AssertionError("network"))
        try:
            code, out, err = run_main(self.args())
        finally:
            urllib.request.urlopen = real
        self.assertEqual(0, code, err)
        self.assertEqual([], sent)
        self.assertIn("track internal: status completed", out)
        self.assertIn("dry run: would open an edit for tv.cleanyour.app", out)
        with open(self.aab, "rb") as f:
            self.assertIn(hashlib.sha256(f.read()).hexdigest(), out)
        # The key never reaches the output.
        body = self.pem.splitlines()[1]
        self.assertNotIn(body, out + err)
        self.assertNotIn("uploader@example", out + err)

    def test_tracks_status_and_rollout(self):
        code, out, _ = run_main(self.args("--track", "production"))
        self.assertEqual(0, code)
        self.assertIn("track production: status inProgress, rollout 20%", out)
        code, out, _ = run_main(self.args("--track", "production", "--rollout", "1"))
        self.assertIn("track production: status completed", out)
        code, out, _ = run_main(self.args("--track", "closed"))
        self.assertIn("track alpha: status completed", out)
        code, out, _ = run_main(self.args("--track", "closed", "--closed-track", "testers"))
        self.assertIn("track testers:", out)
        code, out, _ = run_main(self.args("--draft"))
        self.assertIn("status draft", out)
        self.assertEqual(2, run_main(self.args("--rollout", "0.5"))[0])                           # not production
        self.assertEqual(2, run_main(self.args("--track", "production", "--rollout", "0"))[0])
        self.assertEqual(2, run_main(self.args("--track", "production", "--rollout", "1.5"))[0])

    def test_bad_inputs_stop_before_anything_is_sent(self):
        with open(os.path.join(self.notes, "en-US", "changelogs", "3.txt"), "w") as f:
            f.write("x" * 501)
        code, _, err = run_main(self.args())
        self.assertEqual(2, code)
        self.assertIn("at most 500", err)
        os.remove(os.path.join(self.notes, "en-US", "changelogs", "3.txt"))
        self.assertEqual(2, run_main(self.args())[0])                                             # no en-US notes
        self.assertIn("no service account key", run_main(self.args("--service-account", os.path.join(self.tmp, "none.json")))[2])

    def test_an_unsigned_or_wrong_bundle_is_refused(self):
        with zipfile.ZipFile(self.aab, "w") as z:
            z.writestr("BundleConfig.pb", b"cfg")
            z.writestr("base/manifest/AndroidManifest.xml", b"m")
        with self.assertRaises(pu.InputError):
            pu.check_bundle(self.aab)
        with zipfile.ZipFile(self.aab, "w") as z:
            z.writestr("AndroidManifest.xml", b"an apk")
        with self.assertRaises(pu.InputError):
            pu.check_bundle(self.aab)

    def test_a_key_file_that_is_not_a_service_account_is_refused(self):
        for bad in ({"type": "authorized_user"}, {"type": "service_account", "client_email": "x"},
                    {"type": "service_account", "client_email": "x", "private_key": self.pem, "token_uri": "https://evil.example/token"}):
            with open(self.account, "w") as f:
                json.dump(bad, f)
            with self.assertRaises(pu.InputError):
                pu.load_service_account(self.account)

    def test_a_readable_key_file_is_warned_about(self):
        os.chmod(self.account, 0o644)
        _, warnings = pu.load_service_account(self.account)
        self.assertTrue(any("chmod 600" in w for w in warnings))


if __name__ == "__main__":
    unittest.main()
