#!/usr/bin/env python3
"""Fails when a generated POM has a dependency without a version (Maven Central rejects it).

Run after `./gradlew generatePomFileForMavenPublication`:
    python3 scripts/release/check-poms.py [root]
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def missing_versions(pom_path):
    root = ET.parse(pom_path).getroot()
    missing = []
    for dep in root.findall("m:dependencies/m:dependency", NS):
        if dep.find("m:version", NS) is None:
            group = dep.findtext("m:groupId", "", NS)
            artifact = dep.findtext("m:artifactId", "", NS)
            missing.append("%s:%s" % (group, artifact))
    return missing


def main(argv):
    root = argv[0] if argv else "."
    poms = glob.glob(os.path.join(root, "**", "build", "publications", "*", "pom-default.xml"), recursive=True)
    if not poms:
        print("no generated POMs under %s: run generatePomFileForMavenPublication first" % root, file=sys.stderr)
        return 2
    failed = False
    for pom in sorted(poms):
        for dep in missing_versions(pom):
            print("%s: no version for %s" % (pom, dep), file=sys.stderr)
            failed = True
    print("checked %d POMs" % len(poms))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
