# Shared settings for the scripts/*-on-forge.sh helpers, which run Docker builds and emulator
# sessions on a remote Linux build host ("forge") over ssh. Sourced, not run.
#
# Nothing machine specific lives in git. Put your values in a local file (sourced when present)
# or export them:
#
#   ${CYT_FORGE_ENV:-$HOME/.config/cleanyourtv/forge.env}
#
#   FORGE        ssh destination of the build host, user@host (required)
#   SSH_KEY      ssh identity for it (default ~/.ssh/id_ed25519)
#   FORGE_ROOT   working directory on the build host (default: "build" in the remote home)
#   CYT_SECRETS  local directory with the signing keys, one subdirectory per key
#                (default ~/.config/cleanyourtv/secrets); never inside this repository
#
# After forge_init: SSH (array), FORGE_ROOT and LOCK (the host build lock every helper takes, so
# only one build or emulator session runs at a time) are set.

CYT_FORGE_ENV="${CYT_FORGE_ENV:-$HOME/.config/cleanyourtv/forge.env}"
# shellcheck disable=SC1090
[ -f "$CYT_FORGE_ENV" ] && . "$CYT_FORGE_ENV"

SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_ed25519}"
CYT_SECRETS="${CYT_SECRETS:-$HOME/.config/cleanyourtv/secrets}"

forge_init() {
  if [ -z "${FORGE:-}" ]; then
    echo "FORGE is not set: export FORGE=user@build-host, or set it in $CYT_FORGE_ENV" >&2
    exit 2
  fi
  SSH=(ssh -i "$SSH_KEY" -o ConnectTimeout=10 -o BatchMode=yes "$@" "$FORGE")
  if [ -z "${FORGE_ROOT:-}" ]; then
    FORGE_ROOT="$("${SSH[@]}" 'printf %s "$HOME"' </dev/null)/build"
  fi
  LOCK="$FORGE_ROOT/.cyt-forge.lock"
  "${SSH[@]}" "mkdir -p '$FORGE_ROOT'" </dev/null
}

# forge_extract_signature <signed APK on the build host> <local directory>: `apksigcopier extract`
# of a signed bundled APK into <local directory> (APKSigningBlock and APKSigningBlockOffset), in the
# pinned docker/repro image. scripts/fdroid-build-tv-apks.sh puts that signature back on an unsigned
# rebuild from source (the F-Droid path), so it must be refreshed whenever a bundled APK is re-signed.
# Run from the repository root, after forge_init.
forge_extract_signature() {
  local apk=$1 dir=$2 image rdir name
  image="cyt-build:$(shasum -a 256 docker/repro/Dockerfile | cut -c1-12)"
  name=$(basename "$apk" .apk)
  rdir="$(dirname "$apk")/signature-$name"
  "${SSH[@]}" "docker image inspect '$image' >/dev/null 2>&1 || docker build -q -t '$image' -" < docker/repro/Dockerfile
  "${SSH[@]}" "rm -rf '$rdir' && mkdir -p '$rdir' && docker run --rm -e OWNER=\$(id -u):\$(id -g) \
    -v '$(dirname "$apk")':/w '$image' bash -euc 'apksigcopier extract /w/$name.apk /w/signature-$name; chown -R \$OWNER /w/signature-$name'" </dev/null
  rm -rf "$dir" && mkdir -p "$dir"
  scp -q -i "$SSH_KEY" -o BatchMode=yes "$FORGE:$rdir/APKSigningBlock" "$FORGE:$rdir/APKSigningBlockOffset" "$dir/"
  echo "==> signature for the F-Droid rebuild: $dir"
}
