"""Tests for check-tag.sh. Run: python3 -B -m unittest discover -s scripts/release"""
import os
import subprocess
import unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "check-tag.sh")


def check(tag):
    return subprocess.run(["bash", SCRIPT, tag], capture_output=True, text=True)


class CheckTagTest(unittest.TestCase):
    def test_accepts_a_release_tag(self):
        for tag in ["v1.0.0", "v0.10.12"]:
            with self.subTest(tag=tag):
                self.assertEqual(check(tag).returncode, 0)

    def test_rejects_anything_else_without_echoing_it(self):
        for tag in ["v1.0.0; echo x", "v1.0.0\necho x", "$(echo x)", "1.0.0", "v1.0", "v1.0.0-rc1", ""]:
            with self.subTest(tag=tag):
                result = check(tag)
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn("echo x", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
