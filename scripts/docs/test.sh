#!/usr/bin/env sh
# Run the docs tooling tests (llms.txt link check, llms-full.txt hook). Python 3 stdlib only.
# Pass a test module name to run one, e.g. scripts/docs/test.sh test_check_llms_links
set -e
cd "$(dirname "$0")"
if [ "$#" -gt 0 ]; then
  exec python3 -B -m unittest -v "$@"
fi
exec python3 -B -m unittest discover -v -s . -p 'test_*.py'
