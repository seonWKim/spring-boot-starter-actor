"""MkDocs hook: writes llms-full.txt to the site root after each build.

llms-full.txt is the Getting Started page and every guide in nav order, as one
Markdown file for AI assistants. Code snippets (`--8<-- "path:section"`) are
inlined, so the file holds the real code. It is generated, never committed.
"""

import os
import re

OUTPUT = "llms-full.txt"
# The snippet line may be indented, for example inside a content tab; the inlined code gets the same indent.
SNIPPET = re.compile(r'^([ \t]*)--8<-- "([^"]+)"\s*$', re.MULTILINE)


def is_guide(path):
    return path == "getting-started.md" or path.startswith("guides/")


def nav_pages(nav):
    """Yield every Markdown path in the nav config, in order."""
    for item in nav or []:
        if isinstance(item, str):
            yield item
        elif isinstance(item, dict):
            for value in item.values():
                if isinstance(value, str):
                    yield value
                else:
                    yield from nav_pages(value)


def read_snippet(spec, base_paths):
    path, _, section = spec.partition(":")
    for base in base_paths:
        full = os.path.join(base, path)
        if os.path.isfile(full):
            with open(full, encoding="utf-8") as f:
                text = f.read()
            if not section:
                return text.rstrip("\n")
            match = re.search(
                r"--8<-- \[start:%s\][^\n]*\n(.*?)\n[^\n]*--8<-- \[end:%s\]" % (re.escape(section), re.escape(section)),
                text,
                re.DOTALL,
            )
            if match:
                return match.group(1)
    raise FileNotFoundError("snippet not found: " + spec)


def indent(code, prefix):
    return "\n".join(prefix + line if line else line for line in code.split("\n"))


def build_llms_full(docs_dir, nav, site_url, base_paths):
    site_url = (site_url or "").rstrip("/") + "/"
    parts = []
    for page in nav_pages(nav):
        if not is_guide(page):
            continue
        with open(os.path.join(docs_dir, page), encoding="utf-8") as f:
            text = f.read()
        text = SNIPPET.sub(lambda m: indent(read_snippet(m.group(2), base_paths), m.group(1)), text)
        url = site_url + page[: -len(".md")] + "/"
        parts.append("<!-- Source: %s -->\n\n%s" % (url, text.strip()))
    return "\n\n---\n\n".join(parts) + "\n"


def on_post_build(config, **kwargs):
    root = os.path.dirname(os.path.abspath(config["config_file_path"]))
    base_paths = [os.getcwd(), os.path.dirname(root), root]
    text = build_llms_full(config["docs_dir"], config["nav"], config["site_url"], base_paths)
    with open(os.path.join(config["site_dir"], OUTPUT), "w", encoding="utf-8") as f:
        f.write(text)
