#!/bin/bash

# Usage: sh cluster-start.sh <module> <mainClass> <basePort> <basePekkoPort> <instanceCount>
# e.g. sh cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3

# building metrics jar first
./gradlew :metrics:agentJar

set -e

MODULE=$1
MAIN_CLASS=$2
BASE_PORT=$3
BASE_PEKKO_PORT=$4
INSTANCE_COUNT=$5

if [[ -z "$MODULE" || -z "$MAIN_CLASS" || -z "$BASE_PORT" || -z "$BASE_PEKKO_PORT" || -z "$INSTANCE_COUNT" ]]; then
  echo "Usage: $0 <module> <mainClass> <basePort> <basePekkoPort> <instanceCount>"
  exit 1
fi

run_application() {
  local instance=$1
  local port=$((BASE_PORT + instance))
  local pekko_port=$((BASE_PEKKO_PORT + instance))

  echo "Starting instance $instance: port=${port}, pekko_port=${pekko_port}"

  ./gradlew example:${MODULE}:bootRun \
    --args="--server.port=${port} \
            --spring.actor.pekko.remote.artery.canonical.port=${pekko_port}" \
    -PmainClass=${MAIN_CLASS} \
    > "log_${MODULE}_${port}.txt" 2>&1 &
}

for ((i=0; i<INSTANCE_COUNT; i++)); do
  run_application $i
done
