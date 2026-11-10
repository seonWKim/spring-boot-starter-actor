import importlib.util
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
HOOK = os.path.join(ROOT, "mkdocs", "hooks", "llms_full.py")

spec = importlib.util.spec_from_file_location("llms_full", HOOK)
llms_full = importlib.util.module_from_spec(spec)
spec.loader.exec_module(llms_full)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


class LlmsFullHookTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp)
        self.docs = os.path.join(self.tmp, "mkdocs", "docs")
        self.site = os.path.join(self.tmp, "site")
        os.makedirs(self.site)
        write(os.path.join(self.docs, "index.md"), "# Home\n")
        write(os.path.join(self.docs, "getting-started.md"), "# Getting Started\n\nInstall it.\n")
        write(os.path.join(self.docs, "guides", "b.md"), "# B\n\n```java\n--8<-- \"src/B.java:body\"\n```\n")
        write(os.path.join(self.docs, "guides", "a.md"), "# A\n\nGuide A.\n")
        write(os.path.join(self.docs, "examples", "x.md"), "# Example X\n")
        write(
            os.path.join(self.tmp, "src", "B.java"),
            "class B {\n// --8<-- [start:body]\nint answer = 42;\n// --8<-- [end:body]\n}\n",
        )
        self.config = {
            "config_file_path": os.path.join(self.tmp, "mkdocs", "mkdocs.yml"),
            "docs_dir": self.docs,
            "site_dir": self.site,
            "site_url": "https://example.org/docs",
            "nav": [
                {"Home": ["index.md", "getting-started.md"]},
                {"Guides": [{"B": "guides/b.md"}, {"A": "guides/a.md"}]},
                {"Examples": [{"X": "examples/x.md"}]},
            ],
        }

    def build(self):
        llms_full.on_post_build(self.config)
        with open(os.path.join(self.site, "llms-full.txt"), encoding="utf-8") as f:
            return f.read()

    def test_writes_getting_started_and_guides_in_nav_order(self):
        text = self.build()
        order = [text.index(t) for t in ("# Getting Started", "# B", "# A")]
        self.assertEqual(order, sorted(order))
        self.assertIn("<!-- Source: https://example.org/docs/guides/a/ -->", text)

    def test_leaves_out_pages_that_are_not_guides(self):
        text = self.build()
        self.assertNotIn("# Home", text)
        self.assertNotIn("# Example X", text)

    def test_inlines_code_snippets(self):
        text = self.build()
        self.assertIn("int answer = 42;", text)
        self.assertNotIn("--8<--", text)

    def test_inlines_an_indented_snippet_with_its_indent(self):
        write(os.path.join(self.docs, "guides", "a.md"), '=== "Java"\n\n    ```java\n    --8<-- "src/B.java:body"\n    ```\n')
        text = self.build()
        self.assertIn("    ```java\n    int answer = 42;\n    ```", text)
        self.assertNotIn("--8<--", text)

    def test_fails_on_a_missing_snippet(self):
        write(os.path.join(self.docs, "guides", "a.md"), '--8<-- "src/Nope.java:x"\n')
        with self.assertRaises(FileNotFoundError):
            self.build()


class RealDocsTest(unittest.TestCase):
    def test_real_guides_build_without_leftover_snippets(self):
        with open(os.path.join(ROOT, "mkdocs", "mkdocs.yml"), encoding="utf-8") as f:
            pages = [line.split(":")[-1].strip() for line in f if line.strip().startswith("-") and ".md" in line]
        pages = [p.lstrip("- ").strip() for p in pages]
        text = llms_full.build_llms_full(os.path.join(ROOT, "mkdocs", "docs"), pages, "https://x/", [ROOT])
        self.assertIn("# Getting Started", text)
        self.assertIn("# AI Agents", text)
        self.assertNotIn("--8<--", text)

    def test_mkdocs_yml_registers_the_hook(self):
        with open(os.path.join(ROOT, "mkdocs", "mkdocs.yml"), encoding="utf-8") as f:
            self.assertRegex(f.read(), r"(?m)^hooks:\n  - hooks/llms_full\.py$")

    @unittest.skipUnless(shutil.which("mkdocs"), "mkdocs is not installed")
    def test_mkdocs_build_writes_llms_files_to_site_root(self):
        with tempfile.TemporaryDirectory() as site:
            subprocess.run(
                ["mkdocs", "build", "--config-file", "mkdocs/mkdocs.yml", "--site-dir", site],
                cwd=ROOT,
                check=True,
            )
            self.assertTrue(os.path.isfile(os.path.join(site, "llms.txt")))
            self.assertTrue(os.path.isfile(os.path.join(site, "llms-full.txt")))


if __name__ == "__main__":
    unittest.main()
