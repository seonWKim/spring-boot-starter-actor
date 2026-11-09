#!/usr/bin/env bash
# Builds the monitoring files of one release into OUT_DIR, with the version in their names:
#   actor-grafana-dashboard-<version>.json   the dashboard, tagged v<version>
#   actor-prometheus-alerts-<version>.yaml   the alert rules (PrometheusRule)
#   actor-monitoring-<version>.zip           monitoring/ with that dashboard: provisioning and docker-compose.yml
# Usage: scripts/release/monitoring-assets.sh <version> <out-dir>   (needs jq and zip)
set -euo pipefail

VERSION="${1:?usage: $0 <version> <out-dir>}"
OUT="${2:?usage: $0 <version> <out-dir>}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

jq --arg v "$VERSION" '.tags += ["v" + $v] | .description += " Version " + $v + "."' \
    "$ROOT/monitoring/grafana/actor-dashboard.json" > "$OUT/actor-grafana-dashboard-$VERSION.json"
cp "$ROOT/monitoring/prometheus/actor-alerts.yaml" "$OUT/actor-prometheus-alerts-$VERSION.yaml"

BUNDLE="actor-monitoring-$VERSION"
cp -R "$ROOT/monitoring" "$STAGE/$BUNDLE"
cp "$OUT/actor-grafana-dashboard-$VERSION.json" "$STAGE/$BUNDLE/grafana/actor-dashboard.json"
rm -f "$OUT/$BUNDLE.zip"
(cd "$STAGE" && zip -qr "$OUT/$BUNDLE.zip" "$BUNDLE")

ls -1 "$OUT"
