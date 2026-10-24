#!/usr/bin/env python3
# Re-date and sign the bundled seed index (android/platform/src/main/assets/bundled/), PRODUCT-SPEC
# D29 and SECURITY 2.4 S2. Run on the maintainer's machine, where the release index keys live
# (CYT_INDEX_SECRETS, default ~/.config/cleanyourtv/secrets/cleanyourtv-index-release, or
# $CYT_SECRETS/cleanyourtv-index-release; the files index-{root,ci}.key, .pub and -password.txt):
#
#   scripts/sign-seed-index.py index              # sequence + 1, generated now, expires in 90 days
#   scripts/sign-seed-index.py index --days 30    # another expiry
#   scripts/sign-seed-index.py index --sequence 5 # explicit sequence (never lower than the last one)
#   scripts/sign-seed-index.py keys               # sign keys.json with the root key (after editing it)
#
# index.json is signed with the index (CI) key, keys.json with the root key. Passwords are read from
# the secrets directory and passed to minisign on stdin; nothing secret is printed or written here.
# After `keys`, raise TrustRoots.BUNDLED_KEYS_SEQUENCE to the new keys.json sequence.
import argparse
import datetime
import json
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUNDLED = os.path.join(ROOT, "android/platform/src/main/assets/bundled")
SECRETS = os.environ.get("CYT_INDEX_SECRETS") or os.path.join(
    os.environ.get("CYT_SECRETS") or os.path.expanduser("~/.config/cleanyourtv/secrets"), "cleanyourtv-index-release")
MINISIGN = os.environ.get("MINISIGN", "minisign")
EXPIRY_DAYS = 90


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


def sign(path, key, trusted):
    with open(os.path.join(SECRETS, f"index-{key}-password.txt"), "rb") as pw:
        subprocess.run([MINISIGN, "-S", "-s", os.path.join(SECRETS, f"index-{key}.key"), "-m", path,
                        "-x", path + ".minisig", "-t", trusted],
                       stdin=pw, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
    subprocess.run([MINISIGN, "-V", "-q", "-p", os.path.join(SECRETS, f"index-{key}.pub"), "-m", path],
                   check=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("what", choices=["index", "keys"])
    ap.add_argument("--sequence", type=int)
    ap.add_argument("--days", type=int, default=EXPIRY_DAYS, help="days until the index expires")
    args = ap.parse_args()
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
    stamp = int(now.timestamp())
    if args.what == "index":
        path = os.path.join(BUNDLED, "index.json")
        with open(path, encoding="utf-8") as f:
            doc = json.load(f)
        seq = args.sequence if args.sequence is not None else doc["sequence"] + 1
        if seq < doc["sequence"]:
            sys.exit(f"sequence {seq} is lower than the current {doc['sequence']}")
        doc["sequence"] = seq
        doc["generated"] = iso(now)
        doc["expires"] = iso(now + datetime.timedelta(days=args.days))
        with open(path, "w", encoding="utf-8") as f:
            json.dump(doc, f, indent=2, ensure_ascii=False)
            f.write("\n")
        sign(path, "ci", f"timestamp:{stamp}\tfile:index.json\tsequence:{seq}")
        print(f"index.json sequence {seq}, expires {doc['expires']}")
    else:
        path = os.path.join(BUNDLED, "keys.json")
        with open(path, encoding="utf-8") as f:
            doc = json.load(f)
        sign(path, "root", f"timestamp:{stamp}\tfile:keys.json\tsequence:{doc['sequence']}")
        print(f"keys.json sequence {doc['sequence']} signed by the root key")


if __name__ == "__main__":
    main()
