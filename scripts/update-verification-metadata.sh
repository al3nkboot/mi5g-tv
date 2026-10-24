#!/usr/bin/env bash
# Regenerates gradle/verification-metadata.xml (Gradle dependency verification, SHA-256) on forge
# and copies it back. Run it in the same change that adds or bumps a dependency; review the diff
# like code (every new hash is a new artifact the build will trust). SECURITY 6.4 item 8, T-S4.
#
#   scripts/update-verification-metadata.sh
#
# Resolves what every build path touches: check, the debug, release and benchmark APKs of both
# store flavours, the Play bundle, the benchmark test APK, the e2e scenario classes, the release
# SBOM's classpath, and every library in the version catalog
# (scripts/verification/resolve-catalog.init.gradle.kts).
set -euo pipefail
cd "$(dirname "$0")/.."
FETCH_BACK="gradle/verification-metadata.xml" scripts/build-on-forge.sh \
  --write-verification-metadata sha256 --refresh-dependencies --no-configuration-cache --no-build-cache --continue \
  --init-script scripts/verification/resolve-catalog.init.gradle.kts \
  check assembleDebug :app:assembleRelease :app:bundlePlayRelease :app:assembleFullBenchmark :benchmark:assembleBenchmark \
  :e2e:emulatorTestClasses resolveCatalogForVerification \
  :app:dependencies --configuration fullReleaseRuntimeClasspath
git diff --stat -- gradle/verification-metadata.xml
