# Reproducible release build container (W9; ARCHITECTURE 6.8, PRODUCT-SPEC 7.3 item 2).
#
# Everything that can change the APK bytes is pinned here: the JDK image by digest, the Android
# command line tools by sha256, the platform and build tools by exact package revision. Gradle
# itself is pinned by the wrapper's distributionSha256Sum, and every Maven artifact by
# gradle/verification-metadata.xml. apksigcopier (the comparison tool, not a build input) is
# pinned by version and hash.
#
# Used by .github/workflows/release.yml (two clean containers per tag) and by
# scripts/release-on-forge.sh on the build host. scripts/repro-build.sh is the entry point inside;
# scripts/verify-release.sh checks a signed APK against an unsigned build with its tools.
FROM eclipse-temurin:21-jdk@sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d

ARG CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
ARG CMDLINE_TOOLS_SHA256=2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258

RUN apt-get update \
 && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends unzip python3 python3-venv \
 && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk
RUN mkdir -p $ANDROID_HOME/cmdline-tools \
 && curl -fsSL -o /tmp/clt.zip "$CMDLINE_TOOLS_URL" \
 && echo "$CMDLINE_TOOLS_SHA256  /tmp/clt.zip" | sha256sum -c - \
 && unzip -q /tmp/clt.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm /tmp/clt.zip \
 && yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null \
 && $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install \
      "platforms;android-37.0" "build-tools;36.0.0" "platform-tools" >/dev/null \
 && $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --list_installed

# apksigcopier 1.1.1 and its one dependency, by hash.
RUN python3 -m venv /opt/apksigcopier \
 && printf '%s\n' \
      'apksigcopier==1.1.1 --hash=sha256:0834bb7d6f3607bb0994cf8cd2e26b8f0c9d481e13b75872e0fa9cec052e519b' \
      'click==8.5.0 --hash=sha256:255bc9599cf7748b4b1a446ccc735421bd08a2ae529a8b88597d3de5664ee360' \
      > /tmp/req.txt \
 && /opt/apksigcopier/bin/pip install --no-cache-dir --no-deps --require-hashes -r /tmp/req.txt \
 && rm /tmp/req.txt \
 && ln -s /opt/apksigcopier/bin/apksigcopier /usr/local/bin/apksigcopier

ENV PATH=$ANDROID_HOME/build-tools/36.0.0:$ANDROID_HOME/platform-tools:$PATH \
    GRADLE_USER_HOME=/gradle \
    TZ=UTC \
    LC_ALL=C.UTF-8
WORKDIR /src
