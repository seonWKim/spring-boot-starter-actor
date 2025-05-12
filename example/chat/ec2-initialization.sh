#!/bin/bash

set -euo pipefail

echo "[INFO] Updating APT package index..."
sudo apt update

echo "[INFO] Installing OpenJDK 17..."
sudo apt install -y openjdk-17-jdk

echo "[INFO] Cloning spring-boot-starter-actor repository..."
git clone https://github.com/seonWKim/spring-boot-starter-actor.git

cd spring-boot-starter-actor/

echo "[INFO] Making cluster start script executable..."
chmod +x example/chat/ec2-cluster-start.sh

echo "[INFO] Project directory:"
pwd

echo "[INFO] Contents:"
ls -alh

echo "[SUCCESS] Setup complete. Ready to run:"
echo "  ./example/chat/ec2-cluster-start.sh <system-name> <hostname> <pekko-port> <server-port> <seed-node-1> <seed-node-2> <seed-node-3>"
