#!/usr/bin/env python3
"""CycloneDX 1.5 SBOM of what is inside the APK (release.yml, ARCHITECTURE 6.8).

Reads the output of
  ./gradlew :app:dependencies --configuration fullReleaseRuntimeClasspath
(the resolved runtime classpath of the release variant, which is what R8 packs into the APK) and
adds the SHA-256 of each artifact from gradle/verification-metadata.xml. No Gradle plugin, so
the build itself and its verification metadata stay unchanged.

  sbom.py deps.txt --verification gradle/verification-metadata.xml --version 0.2.0 \
      --apk-sha256 HEX -o cleanyourtv-0.2.0.cdx.json

Deterministic: components sorted, the timestamp taken from SOURCE_DATE_EPOCH. Stdlib only.
"""
import argparse
import datetime
import json
import os
import re
import sys
import uuid
import xml.etree.ElementTree as ET

LINE = re.compile(r"^[| +\\-]*([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+):(?:[^ ]+ -> )?([A-Za-z0-9_.\-]+)(?: \([*c]\))?\s*$")
ARROW = re.compile(r"^[| +\\-]*([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+)(?::([^ ]+))? -> ([A-Za-z0-9_.\-]+)")


def parse_deps(text):
    found = set()
    for raw in text.splitlines():
        line = raw.rstrip()
        if "project :" in line or not re.match(r"^[| +\\-]", line):
            continue
        m = ARROW.match(line)
        if m:
            found.add((m.group(1), m.group(2), m.group(4)))
            continue
        m = LINE.match(line)
        if m:
            found.add((m.group(1), m.group(2), m.group(3)))
    return sorted(found)


def hashes(path):
    out = {}
    if not path or not os.path.exists(path):
        return out
    ns = {"v": "https://schema.gradle.org/dependency-verification"}
    root = ET.parse(path).getroot()
    for comp in root.iter("{%s}component" % ns["v"]):
        g, n, v = comp.get("group"), comp.get("name"), comp.get("version")
        for art in comp.findall("v:artifact", ns):
            name = art.get("name", "")
            if not (name.endswith(".jar") or name.endswith(".aar")):
                continue
            sha = art.find("v:sha256", ns)
            if sha is not None:
                out[(g, n, v)] = sha.get("value")
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("deps", help="output of :app:dependencies --configuration fullReleaseRuntimeClasspath")
    ap.add_argument("--verification", default="gradle/verification-metadata.xml")
    ap.add_argument("--version", required=True)
    ap.add_argument("--apk-sha256")
    ap.add_argument("-o", "--output", required=True)
    a = ap.parse_args()

    with open(a.deps) as f:
        deps = parse_deps(f.read())
    if not deps:
        print("error: no dependencies parsed; pass the :app:dependencies output", file=sys.stderr)
        return 2
    sums = hashes(a.verification)
    epoch = int(os.environ.get("SOURCE_DATE_EPOCH", "0"))
    stamp = datetime.datetime.fromtimestamp(epoch, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    serial = uuid.uuid5(uuid.NAMESPACE_URL, f"tv.cleanyour.app/{a.version}/{a.apk_sha256 or ''}")

    components = []
    for g, n, v in deps:
        c = {
            "type": "library",
            "bom-ref": f"pkg:maven/{g}/{n}@{v}",
            "group": g,
            "name": n,
            "version": v,
            "purl": f"pkg:maven/{g}/{n}@{v}",
        }
        if (g, n, v) in sums:
            c["hashes"] = [{"alg": "SHA-256", "content": sums[(g, n, v)]}]
        components.append(c)

    app = {"type": "application", "bom-ref": "tv.cleanyour.app", "name": "Clean Your TV", "group": "tv.cleanyour",
           "version": a.version}
    if a.apk_sha256:
        app["hashes"] = [{"alg": "SHA-256", "content": a.apk_sha256}]
    bom = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "serialNumber": f"urn:uuid:{serial}",
        "version": 1,
        "metadata": {"timestamp": stamp, "component": app},
        "components": components,
        "dependencies": [{"ref": "tv.cleanyour.app", "dependsOn": [c["bom-ref"] for c in components]}],
    }
    with open(a.output, "w") as f:
        json.dump(bom, f, indent=2, sort_keys=True)
        f.write("\n")
    print(f"sbom: {len(components)} components, {sum(1 for c in components if 'hashes' in c)} with SHA-256, written to {a.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
