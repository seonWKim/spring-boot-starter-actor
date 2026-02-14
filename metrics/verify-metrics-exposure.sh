#!/usr/bin/env bash
# Verifies that intended actor metrics are exposed at /actuator/prometheus.
# Run from project root. Start metrics-example first:
#   ./gradlew :example:metrics-example:bootRun
# Then in another terminal: ./metrics/verify-metrics-exposure.sh

PORT=${1:-8080}
URL="http://localhost:${PORT}"

echo "=== Verifying actor metrics at ${URL}/actuator/prometheus ==="
echo ""

# Trigger some actor activity first
curl -s "${URL}/hello" > /dev/null
curl -s "${URL}/hello" > /dev/null
sleep 1

# Expected metrics (Prometheus uses underscores: actor.lifecycle.created -> actor_lifecycle_created_total)
# Timers have _seconds, _count, _sum; counters may have _total; gauges are as-is
EXPECTED=(
  "actor.lifecycle.active"
  "actor.lifecycle.created"
  "actor.lifecycle.terminated"
  "actor.lifecycle.restarts"
  "actor.lifecycle.resumes"
  "actor.mailbox.size"
  "actor.mailbox.size.max"
  "actor.mailbox.time"
  "actor.message.processed"
  "actor.message.processing.time"
  "actor.errors"
  "system.dead-letters"
  "system.unhandled-messages"
)

# Optional (may not appear without specific activity)
OPTIONAL=(
  "actor.stash.size"
  "actor.mailbox.overflow"
)

PROM=$(curl -s -w "\n%{http_code}" "${URL}/actuator/prometheus")
HTTP_CODE=$(echo "$PROM" | tail -n1)
PROM=$(echo "$PROM" | sed '$d')
if [ "$HTTP_CODE" != "200" ] || [ -z "$PROM" ]; then
  echo "ERROR: Prometheus endpoint not available (HTTP $HTTP_CODE). Check management.endpoints.web.exposure.include=prometheus"
  exit 1
fi

# Prometheus exports dots as underscores. Match: ^actor_lifecycle_active 0.0 or ^actor_lifecycle_created_total{...} 1
MISSING=0
for metric in "${EXPECTED[@]}"; do
  pattern=$(echo "$metric" | tr '.' '_')
  if echo "$PROM" | grep -qE "^${pattern}[_{ ]"; then
    echo "  OK    ${metric}"
  else
    echo "  MISS  ${metric}"
    MISSING=$((MISSING + 1))
  fi
done

echo ""
echo "Optional (may be zero or absent without specific activity):"
for metric in "${OPTIONAL[@]}"; do
  pattern=$(echo "$metric" | tr '.' '_')
  if echo "$PROM" | grep -qE "^${pattern}[_{ ]"; then
    echo "  OK    ${metric}"
  else
    echo "  -     ${metric} (not present)"
  fi
done

echo ""
if [ $MISSING -eq 0 ]; then
  echo "SUCCESS: All expected metrics are exposed."
  exit 0
else
  echo "FAILURE: $MISSING expected metric(s) missing."
  exit 1
fi
