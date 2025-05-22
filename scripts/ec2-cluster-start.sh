#!/bin/bash

# Script to start the chat application on EC2 instances
# Example: Let's say you have 3 EC2 hosts — HOST1, HOST2, and HOST3
# Replace HOST1/HOST2/HOST3 with the actual public DNS names or IP addresses of your EC2 instances

# On HOST1
# bash example/chat/ec2-cluster-start.sh spring-pekko-example HOST1 2551 8080 HOST1:2551 HOST2:2551 HOST3:2551

# On HOST2
# bash example/chat/ec2-cluster-start.sh spring-pekko-example HOST2 2551 8080 HOST1:2551 HOST2:2551 HOST3:2551

# On HOST3
# bash example/chat/ec2-cluster-start.sh spring-pekko-example HOST3 2551 8080 HOST1:2551 HOST2:2551 HOST3:2551

# Check if required arguments are provided
if [ $# -lt 7 ]; then
  echo "Usage: $0 <system-name> <current-ip> <pekko-port> <server-port> <ec2-1> <ec2-2> <ec2-3>"
  echo "Example: $0 spring-pekko-example 10.0.0.0.1 2551 8080 10.0.0.1:2551 10.0.0.2:2552 10.0.0.3:2553"
  exit 1
fi

SYSTEM_NAME=$1
CURRENT_IP=$2
PEKKO_PORT=$3
SERVER_PORT=$4
EC2_INSTANCE_1_IP_PEKKO_PORT=$5
EC2_INSTANCE_2_IP_PEKKO_PORT=$6
EC2_INSTANCE_3_IP_PEKKO_PORT=$7

# --- Seed Node List ---
SEED_NODES=(
  "pekko://$SYSTEM_NAME@$EC2_INSTANCE_1_IP_PEKKO_PORT"
  "pekko://$SYSTEM_NAME@$EC2_INSTANCE_2_IP_PEKKO_PORT"
  "pekko://$SYSTEM_NAME@$EC2_INSTANCE_3_IP_PEKKO_PORT"
)

# --- Convert to Spring Boot CLI Format ---
SEED_NODE_ARGS=""
for i in "${!SEED_NODES[@]}"; do
  SEED_NODE_ARGS+=" --spring.actor.pekko.cluster.seed-nodes[$i]=${SEED_NODES[$i]}"
done

# --- Log info ---
echo "Starting $SYSTEM_NAME application on $CURRENT_IP"
echo "Pekko port: $PEKKO_PORT"
echo "HTTP port: $SERVER_PORT"
echo "Seed nodes:"
for node in "${SEED_NODES[@]}"; do
  echo "  $node"
done

# --- Final Run Command ---
CMD="sh ./gradlew example:chat:bootRun -PmainClass=io.github.seonwkim.example.ChatApplication --args='--server.port=$SERVER_PORT --spring.actor.pekko.remote.artery.canonical.hostname=$CURRENT_IP --spring.actor.pekko.remote.artery.canonical.port=$PEKKO_PORT $SEED_NODE_ARGS'"

echo "Executing:"
echo "$CMD"

# --- Run ---
eval $CMD
