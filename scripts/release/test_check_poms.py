"""Tests for check-poms.py. Run: python3 -B -m unittest discover -s scripts/release"""
import contextlib
import importlib.util
import io
import os
import tempfile
import unittest

_spec = importlib.util.spec_from_file_location(
    "check_poms", os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-poms.py")
)
check_poms = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(check_poms)

GROUP = "io.github.seonwkim"


def module_pom(artifact, version="1.1.0"):
    return """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version><packaging>jar</packaging>
</project>""" % (GROUP, artifact, version)


def bom_pom(managed, version="1.1.0"):
    deps = "".join(
        "<dependency><groupId>%s</groupId><artifactId>%s</artifactId>%s</dependency>"
        % (GROUP, artifact, "" if v is None else "<version>%s</version>" % v)
        for artifact, v in managed
    )
    return """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <groupId>%s</groupId><artifactId>actor-bom</artifactId><version>%s</version><packaging>pom</packaging>
  <dependencyManagement><dependencies>%s</dependencies></dependencyManagement>
</project>""" % (GROUP, version, deps)


class CheckPomsTest(unittest.TestCase):
    def run_check(self, poms):
        with tempfile.TemporaryDirectory() as root:
            for name, content in poms.items():
                directory = os.path.join(root, name, "build", "publications", "maven")
                os.makedirs(directory)
                with open(os.path.join(directory, "pom-default.xml"), "w") as f:
                    f.write(content)
            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr), contextlib.redirect_stdout(io.StringIO()):
                code = check_poms.main([root])
            return code, stderr.getvalue()

    def test_passes_when_the_bom_lists_every_module_at_its_version(self):
        code, err = self.run_check({
            "actor-api": module_pom("actor-api"),
            "actor-core": module_pom("actor-core"),
            "actor-bom": bom_pom([("actor-api", "1.1.0"), ("actor-core", "1.1.0")]),
        })
        self.assertEqual(0, code, err)

    def test_fails_when_a_published_module_is_missing_from_the_bom(self):
        code, err = self.run_check({
            "actor-api": module_pom("actor-api"),
            "actor-kotlin": module_pom("actor-kotlin"),
            "actor-bom": bom_pom([("actor-api", "1.1.0")]),
        })
        self.assertEqual(1, code)
        self.assertIn("actor-kotlin is published but not in actor-bom", err)

    def test_fails_when_the_bom_lists_a_module_at_another_version(self):
        code, err = self.run_check({
            "actor-api": module_pom("actor-api"),
            "actor-bom": bom_pom([("actor-api", "1.0.0")]),
        })
        self.assertEqual(1, code)
        self.assertIn("actor-api at 1.0.0, not 1.1.0", err)

    def test_fails_when_a_managed_dependency_has_no_version(self):
        code, err = self.run_check({
            "actor-api": module_pom("actor-api"),
            "actor-bom": bom_pom([("actor-api", "1.1.0"), ("actor-extra", None)]),
        })
        self.assertEqual(1, code)
        self.assertIn("no version for %s:actor-extra" % GROUP, err)


if __name__ == "__main__":
    unittest.main()
