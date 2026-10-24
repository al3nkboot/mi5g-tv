#!/usr/bin/env python3
"""Dependency update report for nightly.yml (ARCHITECTURE 6.8): every pinned version in
gradle/libs.versions.toml against the newest stable release on Google Maven or Maven Central.
Report only; pins change by a reviewed pull request that also regenerates
gradle/verification-metadata.xml (scripts/update-verification-metadata.sh).

  dependency-report.py [gradle/libs.versions.toml] > report.md

Stable means no alpha, beta, rc, dev, eap, snapshot or milestone suffix (D34). Stdlib only.
"""
import re
import sys
import urllib.request

REPOS = ["https://dl.google.com/android/maven2", "https://repo1.maven.org/maven2", "https://plugins.gradle.org/m2"]
UNSTABLE = re.compile(r"(?i)(alpha|beta|rc|dev|eap|snapshot|m\d+|preview|-\w*\d*$)")
PLUGIN_ARTIFACTS = {
    "com.android.application": "com.android.tools.build:gradle",
    "com.android.library": "com.android.tools.build:gradle",
    "com.android.test": "com.android.tools.build:gradle",
    "org.jetbrains.kotlin.jvm": "org.jetbrains.kotlin:kotlin-gradle-plugin",
    "org.jetbrains.kotlin.plugin.compose": "org.jetbrains.kotlin:compose-compiler-gradle-plugin",
    "org.jetbrains.kotlin.plugin.serialization": "org.jetbrains.kotlin:kotlin-serialization",
    "androidx.baselineprofile": "androidx.benchmark:benchmark-baseline-profile-gradle-plugin",
    "io.github.takahirom.roborazzi": "io.github.takahirom.roborazzi:roborazzi-gradle-plugin",
}


def parse_toml(text):
    """Just enough TOML for a version catalog: [versions], and module/id + version.ref tables."""
    versions, coords, section = {}, {}, None
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        m = re.match(r"^\[(\w+)\]$", line)
        if m:
            section = m.group(1)
            continue
        m = re.match(r'^([\w.-]+)\s*=\s*"([^"]+)"$', line)
        if m and section == "versions":
            versions[m.group(1)] = m.group(2)
            continue
        m = re.match(r"^([\w.-]+)\s*=\s*\{(.*)\}$", line)
        if m and section in ("libraries", "plugins"):
            body = m.group(2)
            ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', body)
            if not ref:
                continue
            mod = re.search(r'module\s*=\s*"([^"]+)"', body)
            pid = re.search(r'id\s*=\s*"([^"]+)"', body)
            coord = mod.group(1) if mod else PLUGIN_ARTIFACTS.get(pid.group(1)) if pid else None
            if coord:
                coords.setdefault(ref.group(1), coord)
    return versions, coords


def key(v):
    return [int(p) if p.isdigit() else p for p in re.split(r"[.-]", v)]


def latest_stable(coord):
    group, name = coord.split(":")
    for repo in REPOS:
        url = f"{repo}/{group.replace('.', '/')}/{name}/maven-metadata.xml"
        try:
            with urllib.request.urlopen(url, timeout=20) as r:
                xml = r.read().decode()
        except Exception:
            continue
        stable = [v for v in re.findall(r"<version>([^<]+)</version>", xml) if not UNSTABLE.search(v)]
        if stable:
            return max(stable, key=key)
    return None


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "gradle/libs.versions.toml"
    with open(path) as f:
        versions, coords = parse_toml(f.read())
    rows, behind = [], 0
    for ref, pinned in sorted(versions.items()):
        coord = coords.get(ref)
        if not coord:
            rows.append(f"| {ref} | {pinned} | (no artifact to look up) | |")
            continue
        newest = latest_stable(coord)
        state = "unknown" if newest is None else ("up to date" if newest == pinned else f"update to {newest}")
        if newest and newest != pinned and key(newest) > key(pinned):
            behind += 1
        rows.append(f"| {ref} | {pinned} | {coord} | {state} |")
    print("## Dependency report\n")
    print(f"{behind} pin(s) behind the newest stable release. Updating a pin is a reviewed PR that also runs "
          "`scripts/update-verification-metadata.sh`.\n")
    print("| Version ref | Pinned | Artifact | Newest stable |")
    print("| :- | :- | :- | :- |")
    print("\n".join(rows))
    return 0


if __name__ == "__main__":
    sys.exit(main())
