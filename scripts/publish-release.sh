#!/usr/bin/env bash
# Publish a built release set to its channels (STORES-PLAN 2). Outward facing: the maintainer runs
# it, by hand, after scripts/release-on-forge.sh built and signed the set and the tag is pushed.
#
#   scripts/publish-release.sh vX.Y.Z [--notes FILE] [--track internal|closed|production]
#                              [--rollout F] [--closed-track NAME] [--draft]
#                              [--skip-github] [--skip-play] [--skip-verify] [--dry-run]
#
# 1. GitHub: the release vX.Y.Z with dist/cleanyourtv-X.Y.Z.apk and its .sha256, the notes from
#    --notes (default dist/release-X.Y.Z/RELEASE-NOTES.md), as with every release so far.
# 2. Google Play: dist/cleanyourtv-X.Y.Z.aab and the per-version release notes
#    (fastlane/metadata/android/<language>/changelogs/<versionCode>.txt) through the Google Play
#    Developer API, by scripts/play-upload.py: the internal track by default, --track closed for
#    the closed test, --track production for a staged rollout (--rollout, default 0.2). --draft
#    until the app's first release has been published from the Play Console.
# 3. The release workflow's verify job on the tag: the published APK is the public build plus a
#    signature (gh workflow run release.yml -f release_tag=vX.Y.Z).
# F-Droid needs nothing here: it builds new tags itself and ships our signed APK when its build
# matches.
#
# Before anything is sent, every input is checked: the tag format, the tag exists here and on
# GitHub (the same tag object in both) and is the commit the set was built from
# (dist/release-X.Y.Z/NOTES.md), the version in app/build.gradle.kts, the APK's checksum and signer
# (docs/release-signer.sha256), the AAB, the notes, gh's login, and the Play inputs (play-upload.py
# --dry-run). --dry-run stops before gh's login and the tag on GitHub, and makes no network call.
#
# Credentials, read only, never printed: gh's own login for GitHub; the Play service account key
# at $CYT_SECRETS/cleanyourtv-play/service-account.json (or CYT_PLAY_SERVICE_ACCOUNT), see
# CONTRIBUTING, Releases. CYT_GITHUB_REPO overrides the repository (owner/name).
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/lib/forge.sh   # CYT_SECRETS and the local settings file; no build host is used
export CYT_SECRETS

TAG="${1:-}"
[ -n "$TAG" ] && [ "${TAG#-}" = "$TAG" ] || { echo "usage: scripts/publish-release.sh vX.Y.Z [options]; see the header of this script" >&2; exit 2; }
shift
NOTES="" TRACK=internal ROLLOUT="" CLOSED_TRACK="" DRAFT=0 SKIP_GITHUB=0 SKIP_PLAY=0 SKIP_VERIFY=0 DRY_RUN=0
while [ $# -gt 0 ]; do
  case "$1" in
    --notes) NOTES="${2:?--notes needs a file}"; shift ;;
    --track) TRACK="${2:?--track needs internal, closed or production}"; shift ;;
    --rollout) ROLLOUT="${2:?--rollout needs a fraction}"; shift ;;
    --closed-track) CLOSED_TRACK="${2:?--closed-track needs a name}"; shift ;;
    --draft) DRAFT=1 ;;
    --skip-github) SKIP_GITHUB=1 ;;
    --skip-play) SKIP_PLAY=1 ;;
    --skip-verify) SKIP_VERIFY=1 ;;
    --dry-run) DRY_RUN=1 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done
case "$TRACK" in internal|closed|production) ;; *) echo "--track is internal, closed or production" >&2; exit 2 ;; esac

REPO="${CYT_GITHUB_REPO:-udhaykumarbala/cleanyourtv-app}"
printf '%s' "$TAG" | grep -Eq '^v[0-9]+\.[0-9]+\.[0-9]+(-(alpha|beta|rc)\.?[0-9]+)?$' \
  || { echo "tag $TAG is not vMAJOR.MINOR.PATCH with an optional -alpha/-beta/-rc suffix" >&2; exit 2; }
VERSION="${TAG#v}"
APP_VERSION=$(git show "$TAG:app/build.gradle.kts" 2>/dev/null | sed -n 's/^ *versionName = "\([0-9][0-9A-Za-z.-]*\)".*/\1/p' | head -1) \
  || true
VERSION_CODE=$(git show "$TAG:app/build.gradle.kts" 2>/dev/null | sed -n 's/^ *versionCode = \([0-9][0-9]*\).*/\1/p' | head -1) || true
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || { echo "tag $TAG does not exist here; tag the release commit first" >&2; exit 2; }
[ "$APP_VERSION" = "$VERSION" ] || { echo "tag $TAG but app/build.gradle.kts at the tag says versionName $APP_VERSION" >&2; exit 2; }
printf '%s' "$VERSION_CODE" | grep -Eq '^[1-9][0-9]*$' || { echo "no versionCode in app/build.gradle.kts at $TAG" >&2; exit 2; }
TAG_COMMIT=$(git rev-parse "$TAG^{commit}")

NAME="cleanyourtv-$VERSION"
APK="dist/$NAME.apk"
AAB="dist/$NAME.aab"
EVIDENCE="dist/release-$VERSION"
NOTES="${NOTES:-$EVIDENCE/RELEASE-NOTES.md}"
fail() { echo "$1" >&2; exit 2; }

echo "==> check the release set for $TAG (versionCode $VERSION_CODE, commit $TAG_COMMIT)"
[ -f "$EVIDENCE/NOTES.md" ] || fail "no $EVIDENCE/NOTES.md: build the set with scripts/release-on-forge.sh at the tag"
BUILT=$(sed -n 's/^- Commit: //p' "$EVIDENCE/NOTES.md" | head -1)
[ "$BUILT" = "$TAG_COMMIT" ] || fail "the set in dist/ was built from $BUILT, not from $TAG ($TAG_COMMIT)"
grep -q "TESTKEY\|THROWAWAY" "$EVIDENCE/NOTES.md" && fail "the set in dist/ was signed with throwaway test keys"
if [ "$SKIP_GITHUB" = 0 ]; then
  [ -f "$APK" ] && [ -f "$APK.sha256" ] || fail "no $APK or $APK.sha256"
  ( cd dist && shasum -a 256 -c "$NAME.apk.sha256" >/dev/null ) || fail "$APK does not match $APK.sha256"
  SIGNER=$(cat "dist/$NAME.signer-sha256.txt" 2>/dev/null || true)
  [ "$SIGNER" = "$(tr -d ' \n' < docs/release-signer.sha256)" ] || fail "$APK is not signed by the release certificate (docs/release-signer.sha256)"
  [ -f "$NOTES" ] || fail "no release notes $NOTES (pass --notes FILE)"
fi
if [ "$SKIP_PLAY" = 0 ]; then
  [ -f "$AAB" ] || fail "no signed $AAB (scripts/release-on-forge.sh without --apk-only signs it)"
  [ -f "$AAB.sha256" ] && ( cd dist && shasum -a 256 -c "$NAME.aab.sha256" >/dev/null ) || fail "$AAB does not match $AAB.sha256"
  PLAY_ARGS=(--aab "$AAB" --version-code "$VERSION_CODE" --version-name "$VERSION" --track "$TRACK")
  [ -n "$ROLLOUT" ] && PLAY_ARGS+=(--rollout "$ROLLOUT")
  [ -n "$CLOSED_TRACK" ] && PLAY_ARGS+=(--closed-track "$CLOSED_TRACK")
  [ "$DRAFT" = 1 ] && PLAY_ARGS+=(--draft)
  python3 scripts/play-upload.py "${PLAY_ARGS[@]}" --dry-run
fi
PRERELEASE=()
case "$VERSION" in *-*) PRERELEASE=(--prerelease) ;; esac

if [ "$DRY_RUN" = 1 ]; then
  echo "==> dry run: every input checked, nothing sent. Without --dry-run this would:"
  [ "$SKIP_GITHUB" = 0 ] && echo "  gh release create $TAG -R $REPO --verify-tag --title \"Clean Your TV $VERSION\" ${PRERELEASE[*]+${PRERELEASE[*]}} --notes-file $NOTES $APK $APK.sha256"
  [ "$SKIP_PLAY" = 0 ] && echo "  python3 scripts/play-upload.py ${PLAY_ARGS[*]}"
  [ "$SKIP_VERIFY" = 0 ] && echo "  gh workflow run release.yml -R $REPO --ref $TAG -f release_tag=$TAG"
  exit 0
fi

command -v gh >/dev/null || fail "gh (the GitHub CLI) is needed"
gh auth status >/dev/null 2>&1 || fail "gh is not logged in (gh auth login)"
REMOTE_TAG=$(gh api "repos/$REPO/git/ref/tags/$TAG" --jq .object.sha 2>/dev/null || true)
[ -n "$REMOTE_TAG" ] || fail "tag $TAG is not on GitHub ($REPO); push it first: git push origin $TAG"
# The same tag object as here (a lightweight tag: the same commit), so GitHub, Play and F-Droid all
# get the commit this set was built from.
LOCAL_TAG=$(git rev-parse "refs/tags/$TAG")
[ "$REMOTE_TAG" = "$LOCAL_TAG" ] || fail "tag $TAG on GitHub is $REMOTE_TAG, here it is $LOCAL_TAG: the two tags differ; fix the tag before publishing"

if [ "$SKIP_GITHUB" = 0 ]; then
  echo "==> 1. GitHub release $TAG"
  gh release create "$TAG" -R "$REPO" --verify-tag --title "Clean Your TV $VERSION" ${PRERELEASE[@]+"${PRERELEASE[@]}"} \
    --notes-file "$NOTES" "$APK" "$APK.sha256"
fi
if [ "$SKIP_PLAY" = 0 ]; then
  echo "==> 2. Google Play ($TRACK)"
  python3 scripts/play-upload.py "${PLAY_ARGS[@]}"
fi
if [ "$SKIP_VERIFY" = 0 ] && [ "$SKIP_GITHUB" = 0 ]; then
  echo "==> 3. the release workflow's verify job"
  gh workflow run release.yml -R "$REPO" --ref "$TAG" -f release_tag="$TAG"
fi
echo
echo "==> published $TAG"
[ "$SKIP_GITHUB" = 0 ] && echo "GitHub: https://github.com/$REPO/releases/tag/$TAG"
[ "$SKIP_PLAY" = 0 ] && echo "Google Play: the $TRACK track (Play Console, Test and release)"
echo "F-Droid: nothing to do; it picks up $TAG on its next build cycle"
