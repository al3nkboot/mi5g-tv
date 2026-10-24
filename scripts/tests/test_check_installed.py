"""Tests for scripts/check-installed.sh against a fake adb (no device and no build tools needed).
Run: python3 -m unittest discover -s scripts/tests"""
import importlib.util
import os
import stat
import subprocess
import tempfile
import textwrap
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
SCRIPT = os.path.join(ROOT, "scripts", "check-installed.sh")
spec = importlib.util.spec_from_file_location("check_apk", os.path.join(ROOT, "scripts", "check-apk.py"))
ca = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ca)

# What the app requests (app and library manifests merged), as `dumpsys package` lists it.
REAL = [
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.CHANGE_WIFI_MULTICAST_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.USE_BIOMETRIC",
]

FAKE_ADB = textwrap.dedent("""\
    #!/usr/bin/env bash
    # adb shell pm path, adb pull, adb shell dumpsys package: from files in $FAKE_DIR.
    case "$*" in
      "shell pm path tv.cleanyour.app") cat "$FAKE_DIR/pm-path.txt" ;;
      pull*) echo "$2" >> "$FAKE_DIR/pulled.txt"; printf 'not an apk' > "$3" ;;
      "shell dumpsys package tv.cleanyour.app") cat "$FAKE_DIR/dumpsys.txt" ;;
      *) echo "fake adb: unexpected $*" >&2; exit 1 ;;
    esac
    """)
# aapt2 that always fails, so check-apk.py's APK checks fail the same way on every machine; the
# tests look at the dumpsys part.
FAKE_AAPT2 = "#!/bin/sh\necho 'fake aapt2' >&2\nexit 1\n"


def dumpsys(perms):
    lines = "\n".join(f"      {p}" for p in perms)
    return (f"Packages:\n  Package [tv.cleanyour.app] (1234):\n    pkgFlags=[ HAS_CODE ]\n"
            f"    requested permissions:\n{lines}\n    install permissions:\n"
            f"      android.permission.INTERNET: granted=true\n")


class ExpectedPermissions(unittest.TestCase):
    def test_both_flavours_request_d16_and_biometric(self):
        for flavour in ca.FLAVOURS:
            self.assertEqual(set(REAL), ca.expected_permissions(flavour), flavour)
        self.assertIn("android.permission.USE_BIOMETRIC", ca.expected_permissions("play"))
        with self.assertRaises(ValueError):
            ca.expected_permissions("beta")

    def test_print_permissions(self):
        out = subprocess.run(["python3", os.path.join(ROOT, "scripts", "check-apk.py"), "--print-permissions", "--flavour", "play"],
                             capture_output=True, text=True, check=True).stdout
        self.assertEqual(sorted(REAL), out.split())


class CheckInstalled(unittest.TestCase):
    def run_script(self, perms, flavour=None, paths=("base.apk",)):
        with tempfile.TemporaryDirectory() as d:
            bindir = os.path.join(d, "bin")
            os.mkdir(bindir)
            for name, body in (("adb", FAKE_ADB), ("aapt2", FAKE_AAPT2)):
                path = os.path.join(bindir, name)
                with open(path, "w") as f:
                    f.write(body)
                os.chmod(path, os.stat(path).st_mode | stat.S_IXUSR)
            with open(os.path.join(d, "pm-path.txt"), "w") as f:
                f.write("".join(f"package:/data/app/~~x/tv.cleanyour.app-y/{p}\r\n" for p in paths))
            with open(os.path.join(d, "dumpsys.txt"), "w") as f:
                f.write(dumpsys(perms))
            env = dict(os.environ, FAKE_DIR=d, PATH=bindir + os.pathsep + os.environ["PATH"])
            env.pop("CYT_FLAVOUR", None)
            if flavour:
                env["CYT_FLAVOUR"] = flavour
            r = subprocess.run(["bash", SCRIPT], capture_output=True, text=True, env=env)
            pulled = []
            if os.path.exists(os.path.join(d, "pulled.txt")):
                with open(os.path.join(d, "pulled.txt")) as f:
                    pulled = f.read().split()
            return r, pulled

    def test_real_permissions_pass_for_both_flavours(self):
        for flavour in (None, "full", "play"):
            r, _ = self.run_script(REAL, flavour)
            self.assertIn("android.permission.USE_BIOMETRIC", r.stdout)
            self.assertNotIn("FAIL requested permissions", r.stdout, flavour)
            # Only the fake APK fails (check-apk.py), so the script still reports a failure.
            self.assertIn("check-installed: FAILED", r.stdout)

    def test_missing_and_extra_permissions_fail(self):
        perms = [p for p in REAL if not p.endswith("USE_BIOMETRIC")] + ["android.permission.WAKE_LOCK"]
        r, _ = self.run_script(perms, "play")
        self.assertIn("FAIL requested permissions differ from the play build's", r.stdout)
        self.assertIn("missing android.permission.USE_BIOMETRIC", r.stdout)
        self.assertIn("extra android.permission.WAKE_LOCK", r.stdout)
        self.assertEqual(1, r.returncode)

    def test_base_apk_of_a_split_install(self):
        _, pulled = self.run_script(REAL, "play", paths=("split_config.arm64_v8a.apk", "base.apk", "split_config.xxhdpi.apk"))
        self.assertEqual(1, len(pulled))
        self.assertTrue(pulled[0].endswith("/base.apk"), pulled)

    def test_unknown_flavour(self):
        r, pulled = self.run_script(REAL, "beta")
        self.assertEqual(2, r.returncode)
        self.assertEqual([], pulled)


if __name__ == "__main__":
    unittest.main()
