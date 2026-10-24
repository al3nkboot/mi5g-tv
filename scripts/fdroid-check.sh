#!/usr/bin/env bash
# Build the app the way F-Droid will, before F-Droid does: fdroidserver's own `fdroid build` runs
# the recipe in docs/fdroid/tv.cleanyour.app.yml (sudo, rm, prebuild, build, gradle) inside F-Droid's
# buildserver image, on a commit of this repository. The unsigned APK it produces is then checked
# against our signed release APK with `apksigcopier compare`, which is the check F-Droid makes before
# it publishes our APK (Binaries and AllowedAPKSigningKeys in the recipe).
#
# Runs INSIDE the buildserver image, as root, with the repository (a normal clone with its .git,
# not a worktree) mounted read-only at /seed:
#
#   docker run --rm -v "$PWD":/seed:ro -v "$PWD/dist/fdroid":/out \
#     [-v /path/to/cleanyourtv-X.Y.Z.apk:/in/signed.apk:ro] \
#     registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie@sha256:<digest> bash /seed/scripts/fdroid-check.sh [ref]
#
# ref: the commit or tag to build (default HEAD of /seed). The image digest and the fdroidserver
# commit are pinned in .github/workflows/fdroid.yml (job fdroid-check), which runs this on every tag
# and nightly. With /in/signed.apk present the result is "match" or a failure; without it, the
# unsigned APK's SHA-256 is reported. Writes /out/<appid>_<versionCode>-unsigned.apk, its sha256
# and fdroid-build.log. Needs network (Debian packages, Gradle, Maven, the upstream submodule).
#
# FDROIDSERVER_COMMIT: the fdroidserver commit to use (cloned from GitLab); FDROIDSERVER_DIR: an
# existing fdroidserver checkout instead.
set -euo pipefail
REF="${1:-HEAD}"
SEED=/seed
OUT=/out
APPID=tv.cleanyour.app
RECIPE="$SEED/docs/fdroid/$APPID.yml"
FDROIDSERVER_COMMIT="${FDROIDSERVER_COMMIT:-2d1e622fbbce7eec379453983435e5b14bea22b5}"
die() { echo "fdroid-check: $*" >&2; exit 1; }

[ "$(id -u)" = 0 ] || die "run as root in the buildserver image (the recipe's sudo step installs packages)"
[ -d "$SEED/.git" ] || die "$SEED is not a git clone (a worktree's .git file does not survive the mount)"
[ -f "$RECIPE" ] || die "no recipe at $RECIPE"
mkdir -p "$OUT"
git config --global --add safe.directory '*'
export ANDROID_HOME=/opt/android-sdk LC_ALL=C.UTF-8

COMMIT=$(git -C "$SEED" rev-parse --verify "$REF^{commit}")
# versionName and versionCode as F-Droid's checkupdates reads them: the literals in
# app/build.gradle.kts (there since 0.3.0, the first version with the store flavours).
GRADLE_FILE=app/build.gradle.kts
read_version() {
  git -C "$SEED" show "$COMMIT:$GRADLE_FILE" 2>/dev/null \
    | sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\"\{0,1\}\([0-9A-Za-z.-]*\)\"\{0,1\}.*/\1/p" | head -1
}
VERSION_CODE=$(read_version versionCode)
VERSION_NAME=$(read_version versionName)
[ -n "$VERSION_CODE" ] && [ -n "$VERSION_NAME" ] || die "versionCode or versionName not found in $GRADLE_FILE at $COMMIT"
# F-Droid ships the `full` flavour (STORES-PLAN 1), so this always builds it: Gradle task
# assembleFullRelease, output app/build/outputs/apk/full/release/app-full-release-unsigned.apk.
# The recipe must name the same flavour.
FLAVOUR=full
FULL_TASK=assembleFullRelease
FULL_APK=app/build/outputs/apk/full/release/app-full-release-unsigned.apk
python3 - "$RECIPE" "$FLAVOUR" <<'EOF'
import sys, yaml
recipe, flavour = sys.argv[1:]
gradle = yaml.safe_load(open(recipe))['Builds'][-1].get('gradle')
if gradle != [flavour]:
    sys.exit(f"fdroid-check: the recipe's last build has gradle {gradle}, expected [{flavour}]")
EOF
echo "==> $APPID $VERSION_NAME ($VERSION_CODE) at $COMMIT, gradle flavour $FLAVOUR ($FULL_TASK)"

if [ -z "${FDROIDSERVER_DIR:-}" ]; then
  FDROIDSERVER_DIR=/home/vagrant/fdroidserver
  rm -rf "$FDROIDSERVER_DIR" && mkdir -p "$FDROIDSERVER_DIR"
  git -C "$FDROIDSERVER_DIR" init -q
  git -C "$FDROIDSERVER_DIR" fetch -q --depth 1 https://gitlab.com/fdroid/fdroidserver.git "$FDROIDSERVER_COMMIT"
  git -C "$FDROIDSERVER_DIR" checkout -q FETCH_HEAD
fi
echo "==> fdroidserver $(git -C "$FDROIDSERVER_DIR" rev-parse HEAD)"

# A one-app fdroiddata: the committed recipe with Repo pointing at the mounted clone and one build
# entry for this commit. Binaries is dropped here: the comparison runs below, on a local file.
DATA=/home/vagrant/fdroiddata
rm -rf "$DATA" && mkdir -p "$DATA/metadata"
printf 'sdk_path: %s\n' "$ANDROID_HOME" > "$DATA/config.yml"
python3 - "$RECIPE" "$DATA/metadata/$APPID.yml" "$SEED" "$COMMIT" "$VERSION_NAME" "$VERSION_CODE" "$FLAVOUR" <<'EOF'
import sys, yaml
src, dst, repo, commit, vname, vcode, flavour = sys.argv[1:]
m = yaml.safe_load(open(src))
build = dict(m['Builds'][-1])
build.update(versionName=vname, versionCode=int(vcode), commit=commit, gradle=[flavour])
m.update(Repo=repo, Builds=[build], CurrentVersion=vname, CurrentVersionCode=int(vcode))
m.pop('Binaries', None)
yaml.safe_dump(m, open(dst, 'w'), sort_keys=False)
EOF

echo "==> fdroid build --on-server $APPID:$VERSION_CODE"
cd "$DATA"
set +e
"$FDROIDSERVER_DIR/fdroid" build --on-server -v -l "$APPID:$VERSION_CODE" > "$OUT/fdroid-build.log" 2>&1
status=$?
set -e
grep -E 'INFO: (Removing|Cleaned|Running|Ignoring|Building|Successfully)|ERROR|CRITICAL|==>|matches|^BUILD' "$OUT/fdroid-build.log" || true
[ "$status" = 0 ] || die "fdroid build failed (exit $status); the full log is fdroid-build.log"

UNSIGNED="$DATA/unsigned/${APPID}_${VERSION_CODE}.apk"
[ -f "$UNSIGNED" ] || die "no unsigned APK at $UNSIGNED"
# The build ran the full flavour's task, and its APK is the one fdroid build collected.
grep -q "$FULL_TASK" "$OUT/fdroid-build.log" || die "fdroid build did not run $FULL_TASK"
BUILT="$DATA/build/$APPID/$FULL_APK"
[ -f "$BUILT" ] || die "no full flavour APK at $BUILT"
cmp -s "$BUILT" "$UNSIGNED" || die "$UNSIGNED is not the full flavour's $FULL_APK"
echo "==> the F-Droid build is $FULL_APK"
NAME="${APPID}_${VERSION_CODE}-unsigned.apk"
cp "$UNSIGNED" "$OUT/$NAME"
( cd "$OUT" && sha256sum "$NAME" | tee "$NAME.sha256" )

if [ -f /in/signed.apk ]; then
  echo "==> apksigcopier compare: /in/signed.apk against the F-Droid build"
  SIGNER=$(apksigner verify --print-certs /in/signed.apk | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
  ALLOWED=$(python3 -c 'import sys,yaml; k=yaml.safe_load(open(sys.argv[1]))["AllowedAPKSigningKeys"]; print(" ".join(k if isinstance(k,list) else [k]))' "$RECIPE")
  case " $ALLOWED " in *" $SIGNER "*) echo "signer $SIGNER is in AllowedAPKSigningKeys" ;; *) die "signer $SIGNER is not in AllowedAPKSigningKeys ($ALLOWED)" ;; esac
  apksigcopier compare /in/signed.apk --unsigned "$OUT/$NAME" || die "the signed APK is NOT the F-Droid build plus a signature: F-Droid would not publish it"
  echo "match: the signed APK is the F-Droid build plus our signature; F-Droid will publish it" | tee "$OUT/compare.txt"
else
  echo "no signed APK given: nothing to compare; the F-Droid build's SHA-256 is above" | tee "$OUT/compare.txt"
fi
