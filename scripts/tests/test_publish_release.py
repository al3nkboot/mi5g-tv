"""Tests for scripts/publish-release.sh in a throwaway git repository with a fake gh (no network,
nothing is published). Run: python3 -m unittest discover -s scripts/tests"""
import os
import shutil
import stat
import subprocess
import tempfile
import textwrap
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
with open(os.path.join(ROOT, "docs", "release-signer.sha256")) as _f:
    SIGNER = _f.read().strip()

# gh: logged in; `api .../git/ref/tags/<tag>` answers $FAKE_REMOTE_TAG; every other call is logged.
FAKE_GH = textwrap.dedent("""\
    #!/usr/bin/env bash
    case "$1 $2" in
      "auth status") exit 0 ;;
      api\\ repos/*/git/ref/tags/*) [ -n "$FAKE_REMOTE_TAG" ] && echo "$FAKE_REMOTE_TAG"; exit 0 ;;
      *) echo "$*" >> "$FAKE_LOG"; exit 0 ;;
    esac
    """)


def git(repo, *args):
    return subprocess.run(["git", "-C", repo, *args], check=True, capture_output=True, text=True).stdout.strip()


class PublishRelease(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.repo = os.path.join(self.tmp, "repo")
        for d in ("scripts/lib", "docs", "app"):
            os.makedirs(os.path.join(self.repo, d))
        shutil.copy(os.path.join(ROOT, "scripts", "publish-release.sh"), os.path.join(self.repo, "scripts"))
        shutil.copy(os.path.join(ROOT, "scripts", "lib", "forge.sh"), os.path.join(self.repo, "scripts", "lib"))
        shutil.copy(os.path.join(ROOT, "docs", "release-signer.sha256"), os.path.join(self.repo, "docs"))
        with open(os.path.join(self.repo, "app", "build.gradle.kts"), "w") as f:
            f.write('android {\n    defaultConfig {\n        versionCode = 9\n        versionName = "9.9.9"\n    }\n}\n')
        git(self.repo, "init", "-q")
        git(self.repo, "-c", "user.email=t@example.invalid", "-c", "user.name=t", "add", ".")
        git(self.repo, "-c", "user.email=t@example.invalid", "-c", "user.name=t", "commit", "-q", "-m", "test")
        git(self.repo, "-c", "user.email=t@example.invalid", "-c", "user.name=t", "tag", "-a", "v9.9.9", "-m", "v9.9.9")
        commit = git(self.repo, "rev-parse", "HEAD")
        dist = os.path.join(self.repo, "dist")
        os.makedirs(os.path.join(dist, "release-9.9.9"))
        with open(os.path.join(dist, "release-9.9.9", "NOTES.md"), "w") as f:
            f.write(f"# Clean Your TV 9.9.9\n\n- Commit: {commit}\n")
        with open(os.path.join(dist, "release-9.9.9", "RELEASE-NOTES.md"), "w") as f:
            f.write("notes\n")
        with open(os.path.join(dist, "cleanyourtv-9.9.9.apk"), "wb") as f:
            f.write(b"not an apk")
        subprocess.run("shasum -a 256 cleanyourtv-9.9.9.apk > cleanyourtv-9.9.9.apk.sha256", shell=True, cwd=dist, check=True)
        with open(os.path.join(dist, "cleanyourtv-9.9.9.signer-sha256.txt"), "w") as f:
            f.write(SIGNER + "\n")
        self.bin = os.path.join(self.tmp, "bin")
        os.makedirs(self.bin)
        gh = os.path.join(self.bin, "gh")
        with open(gh, "w") as f:
            f.write(FAKE_GH)
        os.chmod(gh, os.stat(gh).st_mode | stat.S_IXUSR)
        self.log = os.path.join(self.tmp, "gh.log")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def run_publish(self, remote_tag):
        env = dict(os.environ, PATH=self.bin + os.pathsep + os.environ["PATH"], FAKE_REMOTE_TAG=remote_tag,
                   FAKE_LOG=self.log, CYT_FORGE_ENV=os.path.join(self.tmp, "none.env"), CYT_SECRETS=os.path.join(self.tmp, "secrets"))
        r = subprocess.run(["bash", os.path.join(self.repo, "scripts", "publish-release.sh"), "v9.9.9", "--skip-play", "--skip-verify"],
                           capture_output=True, text=True, env=env)
        sent = ""
        if os.path.exists(self.log):
            with open(self.log) as f:
                sent = f.read()
        return r, sent

    def test_a_different_tag_on_github_stops_before_anything_is_sent(self):
        r, sent = self.run_publish("0" * 40)
        self.assertEqual(2, r.returncode, r.stdout + r.stderr)
        self.assertIn("the two tags differ", r.stderr)
        self.assertEqual("", sent)

    def test_a_missing_tag_on_github_stops(self):
        r, sent = self.run_publish("")
        self.assertEqual(2, r.returncode, r.stdout + r.stderr)
        self.assertIn("is not on GitHub", r.stderr)
        self.assertEqual("", sent)

    def test_the_same_tag_publishes(self):
        r, sent = self.run_publish(git(self.repo, "rev-parse", "refs/tags/v9.9.9"))
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        self.assertIn("release create v9.9.9", sent)


if __name__ == "__main__":
    unittest.main()
