#!/bin/bash
set -e

# Navigate to the chat application directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CHAT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$CHAT_DIR"

echo "Building application..."
../../gradlew build

echo "Building Docker image..."
docker build -t chat-app:latest .

# Uncomment the appropriate line based on your Kubernetes setup
# For Minikube
# echo "Loading image into Minikube..."
# minikube image load chat-app:latest

# For remote registry
# echo "Pushing image to registry..."
# docker tag chat-app:latest your-registry/chat-app:latest
# docker push your-registry/chat-app:latest

echo "Deploying to Kubernetes..."
kubectl apply -f kubernetes/configmap.yaml
kubectl apply -f kubernetes/headless-service.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/deployment.yaml

echo "Waiting for pods to be ready..."
kubectl wait --for=condition=ready pod -l app=chat-app --timeout=120s

echo "Deployment complete!"
echo "Services:"
kubectl get services -l app=chat-app

echo ""
echo "To access the application:"
echo "kubectl get service chat-app-external"
echo ""
echo "For Minikube, use:"
echo "minikube service chat-app-external --url"
