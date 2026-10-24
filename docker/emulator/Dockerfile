# TV emulator harness image (W9, ARCHITECTURE 6.6). The JDK 21 build image plus the shared
# libraries the headless Android emulator needs. The SDK itself (emulator, platform-tools, the
# Android TV system images) lives in the cyt-emulator-sdk volume, not in the image, so the image
# stays small and the 500 MB system images are downloaded once.
#
# Built and run by scripts/emulator-on-forge.sh; nightly.yml uses the GitHub runner directly.
FROM eclipse-temurin:21-jdk@sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      unzip python3 procps iproute2 \
      libpulse0 libnss3 libxcomposite1 libxcursor1 libxi6 libxtst6 libxdamage1 libxkbfile1 \
      libxrandr2 libxkbcommon0 libx11-6 libxext6 libxrender1 libxfixes3 libx11-xcb1 libxcb1 \
      libgl1 libegl1 libgbm1 libdrm2 libfontconfig1 libdbus-1-3 libbsd0 libasound2t64 libxshmfence1 \
 && rm -rf /var/lib/apt/lists/*

# SDK paths are set per command by scripts/emulator-session.sh: the emulator SDK is /emu/sdk (its
# own volume) while Gradle keeps using the shared build SDK at /cache/sdk.
ENV ANDROID_AVD_HOME=/tmp/avd \
    ANDROID_EMULATOR_HOME=/tmp/emuhome
