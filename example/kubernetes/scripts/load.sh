#!/bin/bash
# Sends sharded ask requests to the chat app for a while and counts the ones that failed.
# Usage: load.sh <seconds> [log-file]   (URL: $LOAD_URL, default http://localhost:8080)
# Prints "total=N ok=N failed=N" and exits 1 if any request failed.
set -uo pipefail

SECONDS_TO_RUN=${1:?usage: load.sh <seconds> [log-file]}
LOG=${2:-$(mktemp)}
URL=${LOAD_URL:-http://localhost:8080}
WORKERS=${LOAD_WORKERS:-4}
END=$(( $(date +%s) + SECONDS_TO_RUN ))
: > "$LOG"

worker() {
    while [ "$(date +%s)" -lt "$END" ]; do
        code=$(curl -s -o /dev/null -w '%{http_code}' -m 5 "$URL/hello?message=hi&entityId=e$(( RANDOM % 50 ))")
        echo "$(date +%s) $code" >> "$LOG"
    done
}

for _ in $(seq 1 "$WORKERS"); do worker & done
wait

awk '{ n++; if ($2 == "200") ok++; else bad++ } END { printf "total=%d ok=%d failed=%d\n", n, ok, bad; exit (bad > 0 || n == 0) }' "$LOG"
