#!/usr/bin/env python3
"""Fail when a link in llms.txt points to a docs page that does not exist.

Usage: check_llms_links.py [LLMS_TXT] [DOCS_DIR] [MKDOCS_YML]

Links under the site URL (or relative links) must map to a page in DOCS_DIR
(`guides/routers/` -> `guides/routers.md` or `guides/routers/index.md`), or to a
file in DOCS_DIR, or to a file the build writes (llms-full.txt). Other links are
not checked: the check runs offline.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GENERATED = {"llms-full.txt"}
LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")


def site_url(mkdocs_yml):
    with open(mkdocs_yml, encoding="utf-8") as f:
        match = re.search(r"^site_url:\s*(\S+)", f.read(), re.MULTILINE)
    return match.group(1).rstrip("/") + "/"


def target_exists(path, docs_dir):
    path = path.split("#")[0].strip("/")
    if path in GENERATED:
        return True
    candidates = [path] if os.path.splitext(path)[1] else [path + ".md", os.path.join(path, "index.md")]
    return any(os.path.isfile(os.path.join(docs_dir, c)) for c in candidates)


def dead_links(llms_txt, docs_dir, base_url):
    with open(llms_txt, encoding="utf-8") as f:
        links = LINK.findall(f.read())
    dead = []
    for link in links:
        if link.startswith(base_url):
            path = link[len(base_url):]
        elif "://" in link:
            continue
        else:
            path = link
        if not target_exists(path, docs_dir):
            dead.append(link)
    return dead


def main(argv):
    llms_txt = argv[0] if len(argv) > 0 else os.path.join(ROOT, "mkdocs", "docs", "llms.txt")
    docs_dir = argv[1] if len(argv) > 1 else os.path.join(ROOT, "mkdocs", "docs")
    mkdocs_yml = argv[2] if len(argv) > 2 else os.path.join(ROOT, "mkdocs", "mkdocs.yml")
    dead = dead_links(llms_txt, docs_dir, site_url(mkdocs_yml))
    for link in dead:
        print("dead link in %s: %s" % (llms_txt, link), file=sys.stderr)
    return 1 if dead else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
