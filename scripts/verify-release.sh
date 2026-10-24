#!/usr/bin/env bash
# Check a signed release against an unsigned reproducible build of the same commit. Used by
# scripts/release-on-forge.sh after signing, by the verify job of .github/workflows/release.yml,
# and by anyone who rebuilt the app and wants to confirm the published APK is that build.
#
#   scripts/verify-release.sh <signed.apk> <unsigned.apk> [expected-signer-sha256] [out-dir]
#   scripts/verify-release.sh <signed.aab> <unsigned.aab> <upload-signer-sha256> [out-dir]
#
# Needs the Android build tools on PATH (apksigner, aapt2; the docker/repro image has them),
# apksigcopier, python3, and for a bundle jarsigner and keytool (the JDK).
# The APK (the full flavour, GitHub and F-Droid). The expected signer defaults to
# docs/release-signer.sha256.
#   1. apksigner: the signature verifies, v2 and v3 present, one signer, the release certificate;
#   2. apksigcopier compare: the signed APK is exactly the unsigned build plus a signature, so the
#      published APK contains nothing the public source did not build;
#   3. scripts/check-apk.py --flavour full: package, SDK levels, permissions, exported components,
#      the GitHub hosts, schemes, signer.
# The AAB (the play flavour, Google Play; STORES-PLAN 2). It is signed with the upload key, whose
# certificate must be given; Google re-signs the APKs it serves with the release key.
#   scripts/check-apk.py --flavour play: the JAR signature verifies, by the upload certificate; the
#   bundle is the unsigned build plus that signature; the base module's manifest, permissions,
#   exported components, and no GitHub host or release check.
# CYT_EXPECT_VERSION_CODE and CYT_EXPECT_VERSION_NAME, when set, must be the build's own.
# Exit 0 when all pass.
set -euo pipefail
SIGNED="${1:?usage: verify-release.sh <signed.apk|signed.aab> <unsigned build> [expected-signer-sha256] [out-dir]}"
UNSIGNED="${2:?unsigned build}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
VERSION_ARGS=()
[ -n "${CYT_EXPECT_VERSION_CODE:-}" ] && VERSION_ARGS+=(--expect-version-code "$CYT_EXPECT_VERSION_CODE")
[ -n "${CYT_EXPECT_VERSION_NAME:-}" ] && VERSION_ARGS+=(--expect-version-name "$CYT_EXPECT_VERSION_NAME")
case "$SIGNED" in
  *.aab)
    EXPECT="${3:?the upload key certificate SHA-256 is needed to check a bundle}"
    OUT="${4:-$(mktemp -d)}"
    mkdir -p "$OUT"
    printf '%s' "$EXPECT" | grep -Eq '^[0-9a-f]{64}$' || { echo "expected signer is not a SHA-256 in lower-case hex" >&2; exit 2; }
    echo "==> bundle: signature, upload signer, unsigned build plus a signature, assertions (play)"
    python3 "$HERE/scripts/check-apk.py" "$SIGNED" --flavour play --expect-cert "$EXPECT" --unsigned-build "$UNSIGNED" \
      ${VERSION_ARGS[@]+"${VERSION_ARGS[@]}"} --json "$OUT/check-aab.json" | tee "$OUT/check-aab.txt"
    echo "==> verified: $(sha256sum "$SIGNED" 2>/dev/null || shasum -a 256 "$SIGNED")"
    exit 0
    ;;
esac
EXPECT="${3:-$(tr -d ' \n' < "$HERE/docs/release-signer.sha256")}"
OUT="${4:-$(mktemp -d)}"
mkdir -p "$OUT"
printf '%s' "$EXPECT" | grep -Eq '^[0-9a-f]{64}$' || { echo "expected signer is not a SHA-256 in lower-case hex" >&2; exit 2; }

echo "==> 1. apksigner verify"
# --min-sdk-version 24 so apksigner checks v2 as well (for 28 and up it reports only v3).
apksigner verify --verbose --print-certs --min-sdk-version 24 "$SIGNED" > "$OUT/apksigner-full.txt"
grep -E "^Verifie|Verified using|Number of signers|Signer #1 certificate (DN|SHA-256)" "$OUT/apksigner-full.txt" | tee "$OUT/apksigner-verify.txt"
grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" "$OUT/apksigner-full.txt" || { echo "FAIL: no v2 signature" >&2; exit 1; }
grep -q "Verified using v3 scheme (APK Signature Scheme v3): true" "$OUT/apksigner-full.txt" || { echo "FAIL: no v3 signature" >&2; exit 1; }
grep -q "Number of signers: 1" "$OUT/apksigner-full.txt" || { echo "FAIL: not exactly one signer" >&2; exit 1; }
CERT=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$OUT/apksigner-full.txt")
[ "$CERT" = "$EXPECT" ] || { echo "FAIL: signer $CERT is not the expected $EXPECT" >&2; exit 1; }
echo "signer: $CERT (expected)"

echo "==> 2. apksigcopier compare (signed against the unsigned build)"
if ! apksigcopier compare "$SIGNED" --unsigned "$UNSIGNED"; then
  echo "FAIL: the signed APK is not the unsigned build plus a signature" >&2
  exit 1
fi
echo "apksigcopier compare: match" | tee "$OUT/apksigcopier.txt"

echo "==> 3. APK assertions"
python3 "$HERE/scripts/check-apk.py" "$SIGNED" --flavour full --expect-cert "$EXPECT" ${VERSION_ARGS[@]+"${VERSION_ARGS[@]}"} --json "$OUT/check-apk.json" | tee "$OUT/check-apk.txt"
echo "==> verified: $(sha256sum "$SIGNED" 2>/dev/null || shasum -a 256 "$SIGNED")"
