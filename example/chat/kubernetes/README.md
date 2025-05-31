# Kubernetes Deployment for Chat Application

This directory contains Kubernetes manifests for deploying the Chat application in a Kubernetes cluster.

## Prerequisites

- Kubernetes cluster (local like Minikube/Kind or cloud-based)
- kubectl CLI tool installed and configured
- Docker (for building the image)

## Files

- `deployment.yaml`: Defines the deployment with 3 replicas of the chat application
- `service.yaml`: Defines both internal and external services for the application
- `configmap.yaml`: Contains configuration for the application
- `headless-service.yaml`: Provides a headless service for Pekko cluster discovery

## Building the Docker Image

Before deploying to Kubernetes, you need to build the Docker image:

```bash
# Navigate to the chat application directory
cd /path/to/spring-boot-starter-actor/example/chat

# Build the application
../../gradlew build

# Build the Docker image
docker build -t chat-app:latest .

# If using Minikube, make the image available to Minikube
# minikube image load chat-app:latest

# If using a remote registry, tag and push the image
# docker tag chat-app:latest your-registry/chat-app:latest
# docker push your-registry/chat-app:latest
```

## Deploying to Kubernetes

```bash
# Apply the Kubernetes manifests
kubectl apply -f kubernetes/configmap.yaml
kubectl apply -f kubernetes/headless-service.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/deployment.yaml

# Check the status of the deployment
kubectl get pods -l app=chat-app
kubectl get services -l app=chat-app
```

## Accessing the Application

The application is exposed through a LoadBalancer service. You can access it using:

```bash
# Get the external IP (may take a minute to provision)
kubectl get service chat-app-external

# For Minikube, you may need to use:
# minikube service chat-app-external --url
```

## Scaling the Application

You can scale the application by changing the number of replicas:

```bash
kubectl scale deployment chat-app --replicas=5
```

## Monitoring and Logs

```bash
# View logs for a specific pod
kubectl logs -f <pod-name>

# View logs for all pods with the app=chat-app label
kubectl logs -f -l app=chat-app
```

## Cleanup

To remove the deployment:

```bash
kubectl delete -f kubernetes/deployment.yaml
kubectl delete -f kubernetes/service.yaml
kubectl delete -f kubernetes/headless-service.yaml
kubectl delete -f kubernetes/configmap.yaml
```

## Notes on Pekko Clustering

This deployment uses a headless service for Pekko cluster discovery, which is a common pattern for stateful applications in Kubernetes. The pods will form a cluster using the headless service DNS name as the seed node.
