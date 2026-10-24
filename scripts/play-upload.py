#!/usr/bin/env python3
"""Upload a signed AAB and its release notes to Google Play (STORES-PLAN 2, the Play half of
scripts/publish-release.sh; the maintainer runs it, never CI).

One edit of the Google Play Developer API (androidpublisher v3): edits.insert, edits.bundles.upload,
edits.tracks.update (the track, the release name, the versionCode, the per-language notes, the
status and the rollout fraction), edits.commit. A failed step deletes the edit, so nothing half
done is left in the Play Console.

  play-upload.py --aab dist/cleanyourtv-0.3.0.aab --version-code 3 --version-name 0.3.0
                 [--track internal|closed|production] [--closed-track alpha] [--rollout 0.2]
                 [--draft] [--notes-dir fastlane/metadata/android] [--service-account FILE]
                 [--package tv.cleanyour.app] [--dry-run]

Tracks: internal (the default), closed (the closed testing track, "alpha" unless --closed-track
names another), production (a staged rollout to --rollout of the users, default 0.2; 1 releases to
everyone). --draft creates the release as a draft, which Play requires until the app's first
release has been published from the Console.

Release notes: <notes-dir>/<language>/changelogs/<versionCode>.txt for every language that has
one (en-US at least), at most 500 characters each: the same files F-Droid reads.

The service account key (JSON) is read from --service-account, else $CYT_PLAY_SERVICE_ACCOUNT,
else $CYT_SECRETS/cleanyourtv-play/service-account.json ($CYT_SECRETS defaults to
~/.config/cleanyourtv/secrets, as in scripts/lib/forge.sh). It is only read, never copied and
never printed; the access token lives in memory for this run.

--dry-run checks everything without a network call: the key file (its fields, and that its
private key signs), the AAB (a bundle with a base module, signed), the notes, the track and the
rollout; then it prints what it would send.

Standard library only: the OAuth token request is a JWT signed with RS256 here (PKCS#1 v1.5 over
SHA-256, the key read from its PKCS#8 PEM), so neither google-auth nor requests nor cryptography
is needed. Exit 0 on success, 1 when Play refuses a step, 2 on bad input.
"""
import argparse
import base64
import hashlib
import json
import os
import stat
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

PACKAGE = "tv.cleanyour.app"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD_API = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
TOKEN_URI = "https://oauth2.googleapis.com/token"
NOTES_MAX = 500
TIMEOUT = 120
TRACKS = ("internal", "closed", "production")


class InputError(Exception):
    pass


class PlayError(Exception):
    pass


# ---- RS256 with the standard library -------------------------------------------------------

def _tlv(data, pos):
    """One DER element at pos: (tag, value, next position)."""
    tag = data[pos]
    length = data[pos + 1]
    pos += 2
    if length & 0x80:
        n = length & 0x7F
        if n == 0 or n > 4:
            raise InputError("the private key is not DER this script can read")
        length = int.from_bytes(data[pos:pos + n], "big")
        pos += n
    value = data[pos:pos + length]
    if len(value) != length:
        raise InputError("the private key is truncated")
    return tag, value, pos + length


def _sequence(data):
    tag, value, _ = _tlv(data, 0)
    if tag != 0x30:
        raise InputError("the private key is not a DER sequence")
    items, pos = [], 0
    while pos < len(value):
        t, v, pos = _tlv(value, pos)
        items.append((t, v))
    return items


def rsa_key_from_pem(pem):
    """(n, d) of an RSA private key in PKCS#8 ("PRIVATE KEY") or PKCS#1 ("RSA PRIVATE KEY") PEM."""
    lines = [l.strip() for l in pem.strip().splitlines()]
    if not lines or not lines[0].startswith("-----BEGIN ") or not lines[-1].startswith("-----END "):
        raise InputError("the private key is not PEM")
    kind = lines[0][len("-----BEGIN "):].rstrip("-")
    der = base64.b64decode("".join(l for l in lines[1:-1] if l and ":" not in l))
    if kind == "PRIVATE KEY":
        items = _sequence(der)
        if len(items) < 3 or items[2][0] != 0x04:
            raise InputError("the PKCS#8 key has no private key octet string")
        rsa_oid = bytes.fromhex("2a864886f70d010101")
        if rsa_oid not in items[1][1]:
            raise InputError("the private key is not an RSA key")
        der = items[2][1]
    elif kind != "RSA PRIVATE KEY":
        raise InputError(f"unsupported key type {kind}")
    rsa = _sequence(der)
    if len(rsa) < 4 or any(t != 0x02 for t, _ in rsa[:4]):
        raise InputError("the RSA key is malformed")
    n = int.from_bytes(rsa[1][1], "big")
    d = int.from_bytes(rsa[3][1], "big")
    if n.bit_length() < 2048:
        raise InputError("the RSA key is shorter than 2048 bits")
    return n, d


SHA256_DIGEST_INFO = bytes.fromhex("3031300d060960864801650304020105000420")


def rs256(message, n, d):
    """RSASSA-PKCS1-v1_5 with SHA-256 (RFC 8017 8.2.1)."""
    k = (n.bit_length() + 7) // 8
    t = SHA256_DIGEST_INFO + hashlib.sha256(message).digest()
    em = b"\x00\x01" + b"\xff" * (k - len(t) - 3) + b"\x00" + t
    return pow(int.from_bytes(em, "big"), d, n).to_bytes(k, "big")


def b64url(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def jwt_assertion(account, now):
    header = b64url(json.dumps({"alg": "RS256", "typ": "JWT"}, separators=(",", ":")).encode())
    claims = b64url(json.dumps({
        "iss": account["client_email"], "scope": SCOPE, "aud": account.get("token_uri") or TOKEN_URI,
        "iat": now, "exp": now + 3600,
    }, separators=(",", ":")).encode())
    n, d = rsa_key_from_pem(account["private_key"])
    signing_input = f"{header}.{claims}".encode()
    return f"{header}.{claims}.{b64url(rs256(signing_input, n, d))}"


# ---- inputs --------------------------------------------------------------------------------

def default_service_account():
    if os.environ.get("CYT_PLAY_SERVICE_ACCOUNT"):
        return os.environ["CYT_PLAY_SERVICE_ACCOUNT"]
    secrets = os.environ.get("CYT_SECRETS") or os.path.expanduser("~/.config/cleanyourtv/secrets")
    return os.path.join(secrets, "cleanyourtv-play", "service-account.json")


def load_service_account(path):
    """The key file's fields; only checked, never shown. Returns (account, warnings)."""
    if not os.path.isfile(path):
        raise InputError(f"no service account key at {path} (see CONTRIBUTING, Releases, \"Google Play\")")
    warnings = []
    mode = os.stat(path).st_mode
    if mode & (stat.S_IRWXG | stat.S_IRWXO):
        warnings.append(f"{path} can be read by other users; chmod 600 it")
    try:
        with open(path) as f:
            account = json.load(f)
    except (OSError, ValueError):
        raise InputError("the service account key is not readable JSON")
    if not isinstance(account, dict) or account.get("type") != "service_account":
        raise InputError("the key file is not a service account key (type service_account)")
    for field in ("client_email", "private_key"):
        if not isinstance(account.get(field), str) or not account[field]:
            raise InputError(f"the service account key has no {field}")
    token_uri = account.get("token_uri") or TOKEN_URI
    if urllib.parse.urlparse(token_uri).scheme != "https" or urllib.parse.urlparse(token_uri).hostname != "oauth2.googleapis.com":
        raise InputError("the key's token_uri is not Google's token endpoint")
    # It must sign: a broken key fails here, not halfway through a release.
    n, d = rsa_key_from_pem(account["private_key"])
    rs256(b"check", n, d)
    return account, warnings


def check_bundle(path):
    """Size and sha256 of a signed AAB with a base module."""
    if not os.path.isfile(path):
        raise InputError(f"no AAB at {path}")
    try:
        with zipfile.ZipFile(path) as z:
            names = set(z.namelist())
    except zipfile.BadZipFile:
        raise InputError(f"{path} is not a bundle (zip)")
    if "BundleConfig.pb" not in names or "base/manifest/AndroidManifest.xml" not in names:
        raise InputError(f"{path} is not an Android App Bundle with a base module")
    if not any(n.startswith("META-INF/") and n.rsplit(".", 1)[-1] in ("RSA", "EC", "DSA") for n in names):
        raise InputError(f"{path} is not signed; scripts/release-on-forge.sh signs it with the upload key")
    with open(path, "rb") as f:
        data = f.read()
    return len(data), hashlib.sha256(data).hexdigest()


def release_notes(notes_dir, version_code):
    """[{language, text}] from <notes_dir>/<language>/changelogs/<versionCode>.txt."""
    if not os.path.isdir(notes_dir):
        raise InputError(f"no notes folder {notes_dir}")
    notes = []
    for lang in sorted(os.listdir(notes_dir)):
        p = os.path.join(notes_dir, lang, "changelogs", f"{version_code}.txt")
        if os.path.isfile(p):
            with open(p, encoding="utf-8") as f:
                text = f.read().strip()
            if not text:
                raise InputError(f"{p} is empty")
            if len(text) > NOTES_MAX:
                raise InputError(f"{p} has {len(text)} characters; Play takes at most {NOTES_MAX}")
            notes.append({"language": lang, "text": text})
    if not any(n["language"] == "en-US" for n in notes):
        raise InputError(f"no {notes_dir}/en-US/changelogs/{version_code}.txt")
    return notes


def track_release(args, notes):
    """(track name, release body) for edits.tracks.update."""
    if args.track != "production" and args.rollout is not None:
        raise InputError("--rollout is for the production track only")
    track = {"internal": "internal", "closed": args.closed_track, "production": "production"}[args.track]
    rollout = args.rollout if args.rollout is not None else (0.2 if args.track == "production" else None)
    if rollout is not None and not (0 < rollout <= 1):
        raise InputError("--rollout must be above 0 and at most 1")
    release = {"name": args.version_name, "versionCodes": [str(args.version_code)], "releaseNotes": notes}
    if args.draft:
        release["status"] = "draft"
    elif rollout is not None and rollout < 1:
        release["status"] = "inProgress"
        release["userFraction"] = rollout
    else:
        release["status"] = "completed"
    return track, release


# ---- the API -------------------------------------------------------------------------------

def call(method, url, token=None, body=None, data=None, content_type=None):
    headers = {"User-Agent": "cleanyourtv-play-upload"}
    if token:
        headers["Authorization"] = "Bearer " + token
    payload = None
    if body is not None:
        payload = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    elif data is not None:
        payload = data
        headers["Content-Type"] = content_type or "application/octet-stream"
    req = urllib.request.Request(url, data=payload, method=method, headers=headers)
    last = None
    for attempt in range(2):   # one retry for a dropped connection or a 5xx
        try:
            with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
                text = r.read().decode() or "{}"
                return json.loads(text)
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")[:2000]
            try:
                err = json.loads(detail)
                # The API answers {"error": {"message": ...}}, the token endpoint {"error": "...", "error_description": ...}.
                inner = err.get("error")
                detail = inner.get("message", detail) if isinstance(inner, dict) else f"{inner}: {err.get('error_description', '')}"
            except (ValueError, AttributeError):
                pass
            last = PlayError(f"{method} {url.split('?')[0]}: HTTP {e.code}: {detail}")
            if e.code < 500:
                raise last
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            last = PlayError(f"{method} {url.split('?')[0]}: {e}")
        time.sleep(3)
    raise last


def access_token(account):
    form = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": jwt_assertion(account, int(time.time())),
    }).encode()
    r = call("POST", account.get("token_uri") or TOKEN_URI, data=form, content_type="application/x-www-form-urlencoded")
    if "access_token" not in r:
        raise PlayError("the token endpoint gave no access token")
    return r["access_token"]


def publish(args, account, aab_sha256, track, release):
    token = access_token(account)
    base = f"{API}/{args.package}/edits"
    edit = call("POST", base, token, body={})["id"]
    print(f"edit {edit} opened")
    try:
        with open(args.aab, "rb") as f:
            data = f.read()
        up = call("POST", f"{UPLOAD_API}/{args.package}/edits/{edit}/bundles?uploadType=media", token, data=data)
        if int(up.get("versionCode", -1)) != args.version_code:
            raise PlayError(f"Play read versionCode {up.get('versionCode')} from the bundle, not {args.version_code}")
        if up.get("sha256") and up["sha256"].lower() != aab_sha256:
            raise PlayError("Play's sha256 of the uploaded bundle is not the local one")
        print(f"bundle uploaded: versionCode {up['versionCode']}, sha256 {aab_sha256}")
        call("PUT", f"{base}/{edit}/tracks/{track}", token, body={"track": track, "releases": [release]})
        print(f"track {track}: release {release['name']}, status {release['status']}"
              + (f", rollout {release['userFraction']:.0%}" if "userFraction" in release else ""))
        call("POST", f"{base}/{edit}:commit", token)
        print(f"edit {edit} committed")
    except Exception:
        try:
            call("DELETE", f"{base}/{edit}", token)
            print(f"edit {edit} deleted, nothing was published", file=sys.stderr)
        except PlayError:
            pass
        raise


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--aab", required=True)
    ap.add_argument("--version-code", type=int, required=True)
    ap.add_argument("--version-name", required=True)
    ap.add_argument("--track", choices=TRACKS, default="internal")
    ap.add_argument("--closed-track", default="alpha", help="the closed testing track's name (default alpha)")
    ap.add_argument("--rollout", type=float, help="production only: the fraction of users (default 0.2; 1 for everyone)")
    ap.add_argument("--draft", action="store_true", help="create the release as a draft")
    ap.add_argument("--notes-dir", default="fastlane/metadata/android")
    ap.add_argument("--service-account", default=None)
    ap.add_argument("--package", default=PACKAGE)
    ap.add_argument("--dry-run", action="store_true", help="check every input, send nothing")
    args = ap.parse_args(argv)
    try:
        account, warnings = load_service_account(args.service_account or default_service_account())
        for w in warnings:
            print(f"warning: {w}", file=sys.stderr)
        size, sha = check_bundle(args.aab)
        notes = release_notes(args.notes_dir, args.version_code)
        track, release = track_release(args, notes)
    except InputError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    print(f"service account key: read, private key signs ({'dry run' if args.dry_run else 'live'})")
    print(f"bundle: {args.aab}, {size} bytes, sha256 {sha}, versionCode {args.version_code}")
    print(f"notes: {', '.join(n['language'] + ' ' + str(len(n['text'])) + ' chars' for n in notes)}")
    print(f"track {track}: status {release['status']}" + (f", rollout {release['userFraction']:.0%}" if "userFraction" in release else ""))
    if args.dry_run:
        print(f"dry run: would open an edit for {args.package}, upload the bundle, update track {track}, commit")
        return 0
    try:
        publish(args, account, sha, track, release)
    except PlayError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
