import os
import re
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SCRIPT = os.path.join(HERE, "check_llms_links.py")
LLMS_TXT = os.path.join(ROOT, "mkdocs", "docs", "llms.txt")
DOCS_DIR = os.path.join(ROOT, "mkdocs", "docs")
MKDOCS_YML = os.path.join(ROOT, "mkdocs", "mkdocs.yml")


def run_check(*args):
    return subprocess.run([sys.executable, SCRIPT, *args], capture_output=True, text=True)


class CheckLlmsLinksTest(unittest.TestCase):
    def test_passes_on_the_real_file(self):
        result = run_check()
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_fails_on_a_planted_dead_link(self):
        with open(LLMS_TXT, encoding="utf-8") as f:
            text = f.read()
        dead = "https://seonwkim.github.io/actor-spring-boot-starter/guides/no-such-page/"
        with tempfile.TemporaryDirectory() as tmp:
            planted = os.path.join(tmp, "llms.txt")
            with open(planted, "w", encoding="utf-8") as f:
                f.write(text + "\n- [Missing](%s): a page that does not exist\n" % dead)
            result = run_check(planted, DOCS_DIR, MKDOCS_YML)
        self.assertEqual(result.returncode, 1)
        self.assertIn(dead, result.stderr)

    def test_fails_on_a_relative_dead_link(self):
        with tempfile.TemporaryDirectory() as tmp:
            planted = os.path.join(tmp, "llms.txt")
            with open(planted, "w", encoding="utf-8") as f:
                f.write("# T\n\n- [Ok](getting-started.md): fine\n- [Gone](guides/gone.md): missing\n")
            result = run_check(planted, DOCS_DIR, MKDOCS_YML)
        self.assertEqual(result.returncode, 1)
        self.assertIn("guides/gone.md", result.stderr)
        self.assertNotIn("getting-started.md", result.stderr)


class LlmsTxtFormatTest(unittest.TestCase):
    def setUp(self):
        with open(LLMS_TXT, encoding="utf-8") as f:
            self.text = f.read()

    def test_has_title_summary_and_sections_in_order(self):
        lines = self.text.splitlines()
        self.assertTrue(lines[0].startswith("# "))
        self.assertTrue(any(line.startswith("> ") for line in lines[1:4]))
        headings = [line[3:] for line in lines if line.startswith("## ")]
        expected = ["Getting started", "Guides", "Examples", "Reference"]
        self.assertEqual([h for h in headings if h in expected], expected)

    def test_every_link_has_a_one_line_description(self):
        link_lines = [line for line in self.text.splitlines() if "](" in line and line.startswith("- ")]
        self.assertTrue(link_lines)
        for line in link_lines:
            self.assertRegex(line, r"^- \[[^\]]+\]\([^)]+\): \S")

    def test_every_guide_in_the_nav_is_listed(self):
        with open(MKDOCS_YML, encoding="utf-8") as f:
            nav = f.read().split("\nnav:", 1)[1].split("\nplugins:", 1)[0]
        guides = re.findall(r"(guides/[\w/-]+)\.md", nav)
        self.assertTrue(guides)
        for guide in guides:
            self.assertIn("/%s/)" % guide, self.text)


if __name__ == "__main__":
    unittest.main()
