#!/usr/bin/env bash
# Checks that a workflow's tag input is a release tag, vMAJOR.MINOR.PATCH, before anything uses it.
# The input is never echoed: it could hold a workflow command (::...) or other text for the log.
# Usage: scripts/release/check-tag.sh <tag>
set -euo pipefail

if [[ ! "${1-}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "❌ The tag must look like v1.2.3." >&2
    exit 1
fi
