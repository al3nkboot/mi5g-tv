#!/usr/bin/env bash
# The release set (STORES-PLAN 2): the signed APK (GitHub and F-Droid, the full flavour) and the
# signed AAB (Google Play, the play flavour), built reproducibly on the build host and signed on
# this machine.
#
#   scripts/release-on-forge.sh              # the committed HEAD, version from app/build.gradle.kts
#   scripts/release-on-forge.sh --apk-only   # no Play upload key yet: sign the APK only (the AAB stays unsigned)
#   scripts/release-on-forge.sh --test-key   # throwaway keys: proves the pipeline, never for users
#
# 1. The committed tree plus the upstream submodule at its pinned commit is archived (no working
#    tree files: the tree must be clean). SOURCE_DATE_EPOCH is the commit time.
# 2. On the build host (scripts/lib/forge.sh), under the host build lock, two clean builds of
#    :app:assembleFullRelease and :app:bundlePlayRelease run in two fresh containers of
#    docker/repro (scripts/repro-build.sh): A with the shared Gradle cache, B with an empty Gradle
#    home of its own, so a cache cannot hide a difference. The two unsigned APKs must be byte
#    identical, and so must the two unsigned AABs.
#    BUILD_MEMORY caps each build container (default 4g: a cache-less build with R8 needs more
#    than the 3g of scripts/build-on-forge.sh).
# 3. The unsigned APK and AAB and apksigner (from the same pinned build tools image) come back here.
#    The APK is signed HERE with the release key: v2 and v3, v1 off (minSdk 28). The AAB is signed
#    HERE with the Play upload key (jarsigner; Play App Signing re-signs what it serves with the
#    release key, decision D2). No key or password leaves this machine, not even into a container
#    on the build host.
# 4. apksigner verifies the APK here and its signer must be the release certificate. The signed
#    APK and AAB (public data) go back to a container that runs scripts/verify-release.sh on both:
#    the APK against build B (apksigner, apksigcopier compare, check-apk --flavour full) and the
#    AAB against build B (jarsigner, the upload certificate, the bundle is build B plus a
#    signature, check-apk --flavour play: the same permissions, no GitHub host, no update check).
#    Both must carry this release's versionCode and versionName.
#
# Outputs in dist/: cleanyourtv-<version>.apk, .apk.sha256, .signer-sha256.txt (as before), and
# cleanyourtv-<version>.aab, .aab.sha256, .aab.upload-signer-sha256.txt; the evidence (unsigned
# builds and sha256s, R8 mappings of both flavours, apksigner, check-apk and check-aab output,
# notes) in dist/release-<version>/ (dist/release-<version>-TESTKEY/ with --test-key).
#
# Keys ($CYT_SECRETS, see scripts/lib/forge.sh), overridable one by one:
#   CYT_RELEASE_KEYSTORE           default $CYT_SECRETS/cleanyourtv-release/release.p12 (PKCS#12)
#   CYT_RELEASE_KEY_PASS_FILE      default $CYT_SECRETS/cleanyourtv-release/keystore-password.txt
#   CYT_RELEASE_KEY_ALIAS          default cleanyourtv
#   CYT_PLAY_UPLOAD_KEYSTORE       default $CYT_SECRETS/cleanyourtv-play-upload/upload.p12 (PKCS#12,
#                                  RSA 2048 or more; the maintainer creates it once, see CONTRIBUTING)
#   CYT_PLAY_UPLOAD_KEY_PASS_FILE  default $CYT_SECRETS/cleanyourtv-play-upload/keystore-password.txt
#                                  (one line; the store and key password)
#   CYT_PLAY_UPLOAD_KEY_ALIAS      default upload
# Without the upload key the script stops before building, unless --apk-only is given.
# The expected APK signer is docs/release-signer.sha256 (the public certificate's SHA-256).
# JAVA: a JDK 17 or newer here for apksigner, jarsigner and keytool (default: java on PATH; the
# other two are taken from next to it, or set JARSIGNER and KEYTOOL).
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib/forge.sh

TEST_KEY=0 APK_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --test-key) TEST_KEY=1 ;;
    --apk-only) APK_ONLY=1 ;;
    *) echo "unknown option $arg (use --apk-only or --test-key)" >&2; exit 2 ;;
  esac
done
JAVA="${JAVA:-java}"
JDK_BIN="$(dirname "$(command -v "$JAVA" || echo "$JAVA")")"
JARSIGNER="${JARSIGNER:-$JDK_BIN/jarsigner}"
KEYTOOL="${KEYTOOL:-$JDK_BIN/keytool}"
KS="${CYT_RELEASE_KEYSTORE:-$CYT_SECRETS/cleanyourtv-release/release.p12}"
KS_PASS="${CYT_RELEASE_KEY_PASS_FILE:-$CYT_SECRETS/cleanyourtv-release/keystore-password.txt}"
KS_ALIAS="${CYT_RELEASE_KEY_ALIAS:-cleanyourtv}"
UKS="${CYT_PLAY_UPLOAD_KEYSTORE:-$CYT_SECRETS/cleanyourtv-play-upload/upload.p12}"
UKS_PASS="${CYT_PLAY_UPLOAD_KEY_PASS_FILE:-$CYT_SECRETS/cleanyourtv-play-upload/keystore-password.txt}"
UKS_ALIAS="${CYT_PLAY_UPLOAD_KEY_ALIAS:-upload}"
EXPECT_CERT=$(tr -d ' \n' < docs/release-signer.sha256)

[ -z "$(git status --porcelain)" ] || { echo "the working tree is not clean; commit first (the build uses the commit)" >&2; exit 2; }
[ -f third_party/cleanyourtv/README.md ] || { echo "upstream submodule is empty; run git submodule update --init" >&2; exit 2; }
"$JAVA" -version >/dev/null 2>&1 || { echo "no JDK here ($JAVA); set JAVA to a JDK 17+ java" >&2; exit 2; }
[ -x "$JARSIGNER" ] && [ -x "$KEYTOOL" ] || { echo "jarsigner or keytool not found next to $JAVA (set JARSIGNER and KEYTOOL)" >&2; exit 2; }
printf '%s' "$EXPECT_CERT" | grep -Eq '^[0-9a-f]{64}$' || { echo "docs/release-signer.sha256 is not a SHA-256" >&2; exit 2; }
if [ "$TEST_KEY" = 0 ]; then
  [ -r "$KS" ] && [ -r "$KS_PASS" ] || { echo "release key not found ($KS, $KS_PASS)" >&2; exit 2; }
  if [ "$APK_ONLY" = 0 ] && { [ ! -r "$UKS" ] || [ ! -r "$UKS_PASS" ]; }; then
    cat >&2 <<EOF
The Play upload key was not found:
  $UKS
  $UKS_PASS
It signs the AAB for Google Play (decision D2: Play App Signing keeps the release key; this
separate upload key only proves the upload is ours). Create it once as CONTRIBUTING.md says
(Releases, "The Play upload key") and keep it with the release key, never in the repository; or
set CYT_PLAY_UPLOAD_KEYSTORE and CYT_PLAY_UPLOAD_KEY_PASS_FILE.
To build the set now and sign only the APK (the AAB stays unsigned), run:
  scripts/release-on-forge.sh --apk-only
EOF
    exit 2
  fi
fi

COMMIT=$(git rev-parse HEAD)
SUB=$(git ls-tree "$COMMIT" third_party/cleanyourtv | awk '{print $3}')
EPOCH=$(git log -1 --format=%ct "$COMMIT")
# The one place the version lives (STORES-PLAN 3): app/build.gradle.kts, read by both flavours.
VERSION=$(sed -n 's/^ *versionName = "\([0-9][0-9A-Za-z.-]*\)".*/\1/p' app/build.gradle.kts | head -1)
VERSION_CODE=$(sed -n 's/^ *versionCode = \([0-9][0-9]*\).*/\1/p' app/build.gradle.kts | head -1)
printf '%s' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$' || { echo "versionName '$VERSION' not understood" >&2; exit 2; }
printf '%s' "$VERSION_CODE" | grep -Eq '^[1-9][0-9]*$' || { echo "versionCode '$VERSION_CODE' not understood" >&2; exit 2; }
NAME="cleanyourtv-$VERSION"
[ "$TEST_KEY" = 1 ] && NAME="$NAME-TESTKEY-not-for-distribution"

forge_init -o ServerAliveInterval=30 -o ServerAliveCountMax=4
TAG_IMAGE="cyt-build:$(shasum -a 256 docker/repro/Dockerfile | cut -c1-12)"
RUN_ID="cyt-rel-$(date +%Y%m%d%H%M%S)-$$"
REMOTE="$FORGE_ROOT/$RUN_ID"
SCRATCH=$(mktemp -d)
cleanup() {
  "${SSH[@]}" "docker rm -f $RUN_ID-a $RUN_ID-b $RUN_ID-check >/dev/null 2>&1; docker volume rm $RUN_ID-gradle >/dev/null 2>&1; rm -rf '$REMOTE'" </dev/null || true
  rm -rf "$SCRATCH"
}
trap cleanup EXIT INT TERM

echo "==> $NAME (versionCode $VERSION_CODE) from $COMMIT (submodule $SUB), SOURCE_DATE_EPOCH=$EPOCH"
git archive --format=tar --prefix=src/ "$COMMIT" > "$SCRATCH/src.tar"
git -C third_party/cleanyourtv archive --format=tar --prefix=src/third_party/cleanyourtv/ "$SUB" > "$SCRATCH/sub.tar"
"${SSH[@]}" "mkdir -p '$REMOTE/a' '$REMOTE/b' '$REMOTE/out'" </dev/null
scp -q -i "$SSH_KEY" -o BatchMode=yes "$SCRATCH/src.tar" "$SCRATCH/sub.tar" "$FORGE:$REMOTE/"

echo "==> image $TAG_IMAGE"
"${SSH[@]}" "docker image inspect '$TAG_IMAGE' >/dev/null 2>&1 || docker build -q -t '$TAG_IMAGE' -" < docker/repro/Dockerfile

build() {  # <name> <gradle home mount and env>
  local n="$1" home="$2"
  echo "==> clean build $n (the fullRelease APK and the playRelease AAB)"
  "${SSH[@]}" "flock -w 3600 '$LOCK' docker run --rm --name $RUN_ID-$n --memory ${BUILD_MEMORY:-4g} --memory-swap ${BUILD_MEMORY:-4g} --cpus 4 \
    -e SOURCE_DATE_EPOCH=$EPOCH -e OWNER=\$(id -u):\$(id -g) -v $home -v '$REMOTE':/in:ro -v '$REMOTE/$n':/out \
    '$TAG_IMAGE' bash -euc 'tar -xf /in/src.tar -C / && tar -xf /in/sub.tar -C / && cd /src \
      && { scripts/repro-build.sh /out; s=\$?; chown -R \$OWNER /out; exit \$s; }'" </dev/null
}
build a "mitv-android-cache:/cache -e GRADLE_USER_HOME=/cache/gradle"
# B: an empty Gradle home; only the wrapper's distribution zip is seeded (checked against
# distributionSha256Sum by the wrapper before it unpacks it).
build b "$RUN_ID-gradle:/gradle -v mitv-android-cache:/seed:ro -e SEED_GRADLE=/seed/gradle"

APK_OUT=app-full-release-unsigned.apk
AAB_OUT=app-play-release-unsigned.aab
echo "==> compare the two unsigned builds of each"
"${SSH[@]}" "cd '$REMOTE' && sha256sum a/$APK_OUT b/$APK_OUT a/$AAB_OUT b/$AAB_OUT && cmp a/$APK_OUT b/$APK_OUT && cmp a/$AAB_OUT b/$AAB_OUT" </dev/null \
  || { echo "the two clean builds differ: not reproducible, nothing signed" >&2; exit 1; }

# A --test-key run keeps its evidence apart, so it never wipes the evidence of a real run (which
# scripts/publish-release.sh reads from dist/release-<version>/).
DEST="dist/release-$VERSION"
[ "$TEST_KEY" = 1 ] && DEST="dist/release-$VERSION-TESTKEY"
rm -rf "$DEST" && mkdir -p "$DEST"
scp -q -i "$SSH_KEY" -o BatchMode=yes "$FORGE:$REMOTE/a/$APK_OUT" "$DEST/$NAME-unsigned.apk"
scp -q -i "$SSH_KEY" -o BatchMode=yes "$FORGE:$REMOTE/a/$AAB_OUT" "$DEST/$NAME-unsigned.aab"
rsync -az -e "ssh -i $SSH_KEY -o BatchMode=yes" "$FORGE:$REMOTE/a/mapping/" "$DEST/mapping/" 2>/dev/null || true
# apksigner from the pinned build tools of the same image, so signing uses the version CI verifies with.
"${SSH[@]}" "docker run --rm '$TAG_IMAGE' cat /opt/android-sdk/build-tools/36.0.0/lib/apksigner.jar" </dev/null > "$SCRATCH/apksigner.jar"
APKSIGNER=("$JAVA" -jar "$SCRATCH/apksigner.jar")

if [ "$TEST_KEY" = 1 ]; then
  testkey() {  # <keystore> <password file> <alias> <what>
    head -c 24 /dev/urandom | base64 > "$2"
    "$KEYTOOL" -genkeypair -keystore "$1" -storetype PKCS12 -alias "$3" -keyalg RSA -keysize 3072 -validity 2 \
      -dname "CN=Clean Your TV throwaway test $4, O=not for distribution" \
      -storepass:file "$2" -keypass:file "$2" >/dev/null 2>&1
  }
  KS="$SCRATCH/test.p12" KS_PASS="$SCRATCH/test.pass" KS_ALIAS=test
  testkey "$KS" "$KS_PASS" "$KS_ALIAS" "release key"
  UKS="$SCRATCH/upload.p12" UKS_PASS="$SCRATCH/upload.pass" UKS_ALIAS=upload
  testkey "$UKS" "$UKS_PASS" "$UKS_ALIAS" "upload key"
fi

echo "==> sign the APK here (v2 and v3, v1 off)"
SIGNED="dist/$NAME.apk"
# --min-sdk-version 24 only steers the scheme choice (with 28 apksigner writes v3 alone);
# --alignment-preserved keeps the zip exactly as built, so apksigcopier can compare it with build B.
"${APKSIGNER[@]}" sign --ks "$KS" --ks-type PKCS12 --ks-key-alias "$KS_ALIAS" --ks-pass "file:$KS_PASS" \
  --min-sdk-version 24 --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --alignment-preserved --out "$SIGNED" "$DEST/$NAME-unsigned.apk"
rm -f "$SIGNED.idsig"

echo "==> verify the APK here"
# --min-sdk-version 24 so apksigner checks v2 as well (for 28 and up it reports only v3).
"${APKSIGNER[@]}" verify --verbose --print-certs --min-sdk-version 24 "$SIGNED" \
  | grep -E "^Verifie|Verified using|Number of signers|Signer #1 certificate (DN|SHA-256)" | tee "$DEST/apksigner-verify.txt"
grep -q "Verified using v2 scheme (APK Signature Scheme v2): true" "$DEST/apksigner-verify.txt" || { echo "no v2 signature" >&2; exit 1; }
grep -q "Verified using v3 scheme (APK Signature Scheme v3): true" "$DEST/apksigner-verify.txt" || { echo "no v3 signature" >&2; exit 1; }
CERT=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$DEST/apksigner-verify.txt")
if [ "$TEST_KEY" = 0 ] && [ "$CERT" != "$EXPECT_CERT" ]; then
  echo "signer $CERT is not the release certificate $EXPECT_CERT" >&2; exit 1
fi

SIGNED_AAB=""
UPLOAD_CERT=""
if [ "$APK_ONLY" = 0 ]; then
  echo "==> sign the AAB here (the Play upload key)"
  SIGNED_AAB="dist/$NAME.aab"
  rm -f "$SIGNED_AAB"
  "$JARSIGNER" -keystore "$UKS" -storetype PKCS12 -storepass:file "$UKS_PASS" -keypass:file "$UKS_PASS" \
    -signedjar "$SIGNED_AAB" "$DEST/$NAME-unsigned.aab" "$UKS_ALIAS" >/dev/null
  UPLOAD_CERT=$("$KEYTOOL" -list -v -keystore "$UKS" -storetype PKCS12 -alias "$UKS_ALIAS" -storepass:file "$UKS_PASS" \
    | sed -n 's/^[[:space:]]*SHA256: *\([0-9A-Fa-f:]*\).*/\1/p' | head -1 | tr -d ':' | tr 'A-F' 'a-f')
  printf '%s' "$UPLOAD_CERT" | grep -Eq '^[0-9a-f]{64}$' || { echo "could not read the upload key's certificate" >&2; exit 1; }
  [ "$UPLOAD_CERT" != "$CERT" ] || { echo "the upload key is the release key; D2 wants a separate upload key" >&2; exit 1; }
  "$JARSIGNER" -verify "$SIGNED_AAB" | grep -q "jar verified" || { echo "the signed AAB does not verify" >&2; exit 1; }
  echo "upload key certificate SHA-256: $UPLOAD_CERT" | tee "$DEST/aab-upload-signer.txt"
fi

echo "==> apksigcopier compare with build B, APK and AAB assertions (in the build image)"
scp -q -i "$SSH_KEY" -o BatchMode=yes "$SIGNED" "$FORGE:$REMOTE/out/signed.apk"
if [ -n "$SIGNED_AAB" ]; then
  scp -q -i "$SSH_KEY" -o BatchMode=yes "$SIGNED_AAB" "$FORGE:$REMOTE/out/signed.aab"
  AAB_CHECK="scripts/verify-release.sh /in/out/signed.aab /in/b/$AAB_OUT $UPLOAD_CERT /out"
else
  # --apk-only: the unsigned AAB is still checked (manifest, permissions, no GitHub host).
  AAB_CHECK="python3 scripts/check-apk.py /in/b/$AAB_OUT --flavour play --unsigned --expect-version-code $VERSION_CODE --expect-version-name $VERSION --json /out/check-aab.json | tee /out/check-aab.txt"
fi
"${SSH[@]}" "flock -w 3600 '$LOCK' docker run --rm --name $RUN_ID-check --memory 1g -e OWNER=\$(id -u):\$(id -g) \
  -e CYT_EXPECT_VERSION_CODE=$VERSION_CODE -e CYT_EXPECT_VERSION_NAME=$VERSION \
  -v '$REMOTE':/in:ro -v '$REMOTE/out':/out '$TAG_IMAGE' bash -euc 'set -o pipefail; tar -xf /in/src.tar -C / && cd /src \
    && { s=0; scripts/verify-release.sh /in/out/signed.apk /in/b/$APK_OUT $CERT /out && $AAB_CHECK || s=\$?; chown -R \$OWNER /out; exit \$s; }'" </dev/null
rsync -az -e "ssh -i $SSH_KEY -o BatchMode=yes" --exclude signed.apk --exclude signed.aab "$FORGE:$REMOTE/out/" "$DEST/"

SUM=$(shasum -a 256 "$SIGNED" | cut -d' ' -f1)
( cd dist && shasum -a 256 "$NAME.apk" > "$NAME.apk.sha256" )
echo "$CERT" > "dist/$NAME.signer-sha256.txt"
UNSIGNED_SUM=$(shasum -a 256 "$DEST/$NAME-unsigned.apk" | cut -d' ' -f1)
UNSIGNED_AAB_SUM=$(shasum -a 256 "$DEST/$NAME-unsigned.aab" | cut -d' ' -f1)
AAB_LINES="- AAB (Google Play): not signed (--apk-only); unsigned reproducible build SHA-256 (both clean builds): $UNSIGNED_AAB_SUM
- AAB checks (unsigned): $(tail -1 "$DEST/check-aab.txt" 2>/dev/null)"
AAB_SUM=""
if [ -n "$SIGNED_AAB" ]; then
  AAB_SUM=$(shasum -a 256 "$SIGNED_AAB" | cut -d' ' -f1)
  ( cd dist && shasum -a 256 "$NAME.aab" > "$NAME.aab.sha256" )
  echo "$UPLOAD_CERT" > "dist/$NAME.aab.upload-signer-sha256.txt"
  AAB_LINES="- AAB (Google Play): $NAME.aab, $(wc -c < "$SIGNED_AAB" | tr -d ' ') bytes, SHA-256 $AAB_SUM
- AAB upload key certificate SHA-256: $UPLOAD_CERT (Play App Signing serves the app signed by the release certificate)
- Unsigned reproducible AAB SHA-256 (both clean builds): $UNSIGNED_AAB_SUM
- AAB checks: $(tail -1 "$DEST/check-aab.txt" 2>/dev/null)"
fi
cat > "$DEST/NOTES.md" <<EOF
# Clean Your TV $VERSION (versionCode $VERSION_CODE)

- Commit: $COMMIT
- Upstream launcher submodule: $SUB
- APK (GitHub and F-Droid): $NAME.apk, $(wc -c < "$SIGNED" | tr -d ' ') bytes
- SHA-256: $SUM
- Signer certificate SHA-256: $CERT
- Unsigned reproducible build SHA-256 (both clean builds): $UNSIGNED_SUM
- Checks: $(tail -1 "$DEST/check-apk.txt" 2>/dev/null)
$AAB_LINES
$( [ "$TEST_KEY" = 1 ] && echo "- SIGNED WITH THROWAWAY TEST KEYS: not for anyone's phone or for Google Play" )
EOF
echo
echo "$SUM  $SIGNED"
echo "signer certificate SHA-256: $CERT"
if [ -n "$SIGNED_AAB" ]; then
  echo "$AAB_SUM  $SIGNED_AAB"
  echo "upload key certificate SHA-256: $UPLOAD_CERT"
else
  echo "AAB not signed (--apk-only): $DEST/$NAME-unsigned.aab"
fi
echo "==> done; evidence in $DEST"
