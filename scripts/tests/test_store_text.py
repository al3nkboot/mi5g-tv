"""Checks on the store text in fastlane/metadata (F-Droid reads it from the tag, Google Play gets it
through scripts/play-upload.py), so a tag never freezes text the stores refuse or a dead link.
Run: python3 -m unittest discover -s scripts/tests"""
import glob
import os
import re
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
META = os.path.normpath(os.path.join(HERE, "..", "..", "fastlane", "metadata", "android"))
# Google Play's limits (F-Droid's are the same or looser).
LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
CHANGELOG_LIMIT = 500
# Links that resolve today (RF-STORES-030 M1): the app's page and privacy policy on cleanyour.tv,
# its GitHub Pages site and its repo.
URL_OK = re.compile(r"^https://(cleanyour\.tv/app/android(/privacy)?$|udhaykumarbala\.github\.io/cleanyourtv-app/|github\.com/udhaykumarbala/cleanyourtv-app)")
URL = re.compile(r"https?://\S+|\b[a-z0-9.-]+\.(?:tv|com|io|org|dev)\b(?:/\S*)?", re.I)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read().strip()


def files():
    return sorted(glob.glob(os.path.join(META, "*", "*.txt")) + glob.glob(os.path.join(META, "*", "changelogs", "*.txt")))


class StoreText(unittest.TestCase):
    def test_lengths(self):
        self.assertTrue(files(), META)
        for path in files():
            name = os.path.basename(path)
            limit = CHANGELOG_LIMIT if os.path.basename(os.path.dirname(path)) == "changelogs" else LIMITS.get(name)
            if limit:
                self.assertLessEqual(len(read(path)), limit, path)

    def test_links_resolve_today(self):
        for path in files():
            for m in URL.finditer(read(path)):
                link = m.group(0).rstrip(".,)")
                if not link.startswith("http"):
                    link = "https://" + link
                self.assertRegex(link, URL_OK, f"{path}: {link}")

    def test_no_em_dash_or_double_hyphen(self):
        for path in files():
            text = read(path)
            self.assertNotIn("\u2014", text, path)
            self.assertNotIn("-" * 2, text, path)

    def test_the_030_notes_are_true_on_every_channel(self):
        notes = read(os.path.join(META, "en-US", "changelogs", "3.txt")).lower()
        # On release day the Play listing is still in its closed test (D1).
        self.assertNotIn("now on google play", notes)
        self.assertNotIn("now also published", notes)
        self.assertIn("privacy", notes)

    def test_summary_passes_fdroid_lint(self):
        # fdroidserver lint's Summary checks (fdroidserver/lint.py REGEX_CHECKS), plus our own rule
        # that the summary does not talk to the reader as "your".
        summary = read(os.path.join(META, "en-US", "short_description.txt"))
        self.assertNotRegex(summary, re.compile(r".*\b(free software|open source)\b.*", re.I))
        self.assertNotRegex(summary, re.compile(r".*((your|for).*android|android.*(app|device|client|port|version))", re.I))
        self.assertNotRegex(summary, re.compile(r".*[a-z0-9][.!?]( |$)"))
        self.assertNotRegex(summary, re.compile(r"\byour\b", re.I))
        self.assertNotEqual(summary.lower(), read(os.path.join(META, "en-US", "title.txt")).lower())

    def test_description_names_its_connections(self):
        # F-Droid reviewers check that every connection is described; true for every channel.
        text = read(os.path.join(META, "en-US", "full_description.txt"))
        self.assertIn("contacts GitHub only when you ask it to", text)
        self.assertIn("Check for updates", text)
        self.assertIn("add-ons", text)
        self.assertIn("The Google Play version makes no internet connections of its own", text)
        for word in ("official", "ad blocker", "ad-blocker", "adblock"):
            self.assertNotIn(word, text.lower())

    def test_the_031_notes_are_true_on_every_channel(self):
        notes = read(os.path.join(META, "en-US", "changelogs", "4.txt")).lower()
        # Google Play has no add-ons: the note must not promise them.
        self.assertIn("where add-ons are offered", notes)
        self.assertNotIn("google play", notes)
        self.assertNotIn("f-droid", notes)


if __name__ == "__main__":
    unittest.main()
