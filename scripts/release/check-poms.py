#!/usr/bin/env python3
"""Checks the generated POMs before a Maven Central deployment.

- Every dependency, and every managed dependency in the BOM, has a version (Central rejects one without).
- The BOM (actor-bom) lists every published module (packaging jar) at that module's version.

Run after `./gradlew generatePomFileForMavenPublication`:
    python3 scripts/release/check-poms.py [root]
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
BOM_ARTIFACT_ID = "actor-bom"
DEPENDENCY_PATHS = ("m:dependencies/m:dependency", "m:dependencyManagement/m:dependencies/m:dependency")


def coordinates(element):
    return element.findtext("m:groupId", "", NS), element.findtext("m:artifactId", "", NS)


def missing_versions(root):
    missing = []
    for path in DEPENDENCY_PATHS:
        for dep in root.findall(path, NS):
            if dep.find("m:version", NS) is None:
                missing.append("%s:%s" % coordinates(dep))
    return missing


def bom_problems(bom, modules):
    """Published modules the BOM leaves out or lists at another version."""
    managed = {
        coordinates(dep): dep.findtext("m:version", "", NS)
        for dep in bom.findall("m:dependencyManagement/m:dependencies/m:dependency", NS)
    }
    problems = []
    for module in modules:
        key = coordinates(module)
        version = module.findtext("m:version", "", NS)
        if key not in managed:
            problems.append("%s:%s is published but not in %s" % (key + (BOM_ARTIFACT_ID,)))
        elif managed[key] != version:
            problems.append("%s lists %s:%s at %s, not %s" % ((BOM_ARTIFACT_ID,) + key + (managed[key], version)))
    return problems


def main(argv):
    root = argv[0] if argv else "."
    poms = sorted(glob.glob(os.path.join(root, "**", "build", "publications", "*", "pom-default.xml"), recursive=True))
    if not poms:
        print("no generated POMs under %s: run generatePomFileForMavenPublication first" % root, file=sys.stderr)
        return 2
    failed = False
    parsed = {pom: ET.parse(pom).getroot() for pom in poms}
    for pom, project in parsed.items():
        for dep in missing_versions(project):
            print("%s: no version for %s" % (pom, dep), file=sys.stderr)
            failed = True

    boms = [p for p in parsed.values() if p.findtext("m:artifactId", "", NS) == BOM_ARTIFACT_ID]
    modules = [p for p in parsed.values() if p.findtext("m:packaging", "jar", NS) == "jar"]
    if len(boms) != 1:
        print("expected one %s POM, found %d" % (BOM_ARTIFACT_ID, len(boms)), file=sys.stderr)
        failed = True
    else:
        for problem in bom_problems(boms[0], modules):
            print(problem, file=sys.stderr)
            failed = True
    print("checked %d POMs" % len(poms))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
