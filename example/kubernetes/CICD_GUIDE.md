# CI/CD Pipeline Guide

Guide for setting up continuous integration and deployment for Spring Boot Starter Actor applications.

## Table of Contents

1. [Overview](#overview)
2. [GitHub Actions](#github-actions)
3. [GitLab CI/CD](#gitlab-cicd)
4. [Jenkins](#jenkins)
5. [ArgoCD (GitOps)](#argocd-gitops)
6. [Best Practices](#best-practices)

## Overview

### Pipeline Stages

A typical CI/CD pipeline for this application includes:

```
┌─────────┐   ┌──────┐   ┌──────┐   ┌────────┐   ┌────────┐   ┌────────┐
│ Commit  │──▶│ Build│──▶│ Test │──▶│Security│──▶│ Deploy │──▶│ Verify │
│         │   │      │   │      │   │ Scan   │   │  Dev   │   │        │
└─────────┘   └──────┘   └──────┘   └────────┘   └────────┘   └────────┘
                                                        │
                                                        ▼
                                                   ┌────────┐
                                                   │ Deploy │
                                                   │  Prod  │
                                                   └────────┘
```

**Stages:**
1. **Build**: Compile code, build JAR
2. **Test**: Run unit and integration tests
3. **Security Scan**: Vulnerability scanning
4. **Build Image**: Create Docker image
5. **Push Image**: Push to container registry
6. **Deploy Dev**: Deploy to development cluster
7. **Deploy Staging**: Deploy to staging (manual approval)
8. **Deploy Prod**: Deploy to production (manual approval)
9. **Verify**: Run smoke tests

## GitHub Actions

### Complete Workflow

Create `.github/workflows/ci-cd.yml`:

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
    tags: ['v*']
  pull_request:
    branches: [main, develop]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}/spring-actor-app

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle
    
    - name: Build with Gradle
      run: |
        cd example/chat
        ./gradlew build -x test
    
    - name: Run tests
      run: |
        cd example/chat
        ./gradlew test
    
    - name: Publish test results
      uses: EnricoMi/publish-unit-test-result-action@v2
      if: always()
      with:
        files: |
          example/chat/build/test-results/**/*.xml
    
    - name: Upload test coverage
      uses: codecov/codecov-action@v3
      with:
        files: example/chat/build/reports/jacoco/test/jacocoTestReport.xml
        flags: unittests
        name: codecov-umbrella
    
    - name: Upload JAR artifact
      uses: actions/upload-artifact@v3
      with:
        name: spring-actor-jar
        path: example/chat/build/libs/*.jar

  security-scan:
    runs-on: ubuntu-latest
    needs: build-and-test
    permissions:
      security-events: write
      contents: read
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
    
    - name: Run Trivy vulnerability scanner
      uses: aquasecurity/trivy-action@master
      with:
        scan-type: 'fs'
        scan-ref: 'example/chat'
        format: 'sarif'
        output: 'trivy-results.sarif'
    
    - name: Upload Trivy results to GitHub Security
      uses: github/codeql-action/upload-sarif@v2
      with:
        sarif_file: 'trivy-results.sarif'
    
    - name: OWASP Dependency Check
      uses: dependency-check/Dependency-Check_Action@main
      with:
        project: 'spring-actor'
        path: 'example/chat'
        format: 'HTML'
        args: >
          --enableRetired
          --failOnCVSS 7

  build-image:
    runs-on: ubuntu-latest
    needs: [build-and-test, security-scan]
    if: github.event_name == 'push' || github.event_name == 'tag'
    permissions:
      contents: read
      packages: write
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
    
    - name: Download JAR artifact
      uses: actions/download-artifact@v3
      with:
        name: spring-actor-jar
        path: example/chat/build/libs
    
    - name: Set up Docker Buildx
      uses: docker/setup-buildx-action@v3
    
    - name: Log in to Container Registry
      uses: docker/login-action@v3
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
    
    - name: Extract metadata
      id: meta
      uses: docker/metadata-action@v5
      with:
        images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
        tags: |
          type=ref,event=branch
          type=ref,event=pr
          type=semver,pattern={{version}}
          type=semver,pattern={{major}}.{{minor}}
          type=sha,prefix={{branch}}-
    
    - name: Build and push Docker image
      uses: docker/build-push-action@v5
      with:
        context: example/chat
        file: example/chat/Dockerfile.kubernetes
        push: true
        tags: ${{ steps.meta.outputs.tags }}
        labels: ${{ steps.meta.outputs.labels }}
        cache-from: type=gha
        cache-to: type=gha,mode=max
    
    - name: Scan image with Trivy
      uses: aquasecurity/trivy-action@master
      with:
        image-ref: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ steps.meta.outputs.version }}
        format: 'sarif'
        output: 'trivy-image-results.sarif'
    
    - name: Upload Trivy image results
      uses: github/codeql-action/upload-sarif@v2
      with:
        sarif_file: 'trivy-image-results.sarif'

  deploy-dev:
    runs-on: ubuntu-latest
    needs: build-image
    if: github.ref == 'refs/heads/develop'
    environment:
      name: development
      url: https://dev.spring-actor.example.com
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
    
    - name: Configure kubectl
      uses: azure/setup-kubectl@v3
      with:
        version: 'v1.28.0'
    
    - name: Set up kubeconfig
      run: |
        mkdir -p $HOME/.kube
        echo "${{ secrets.KUBECONFIG_DEV }}" | base64 -d > $HOME/.kube/config
    
    - name: Update image tag
      run: |
        cd example/kubernetes/overlays/dev
        kustomize edit set image \
          your-registry/spring-actor-app=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
    
    - name: Deploy to Dev
      run: |
        kubectl apply -k example/kubernetes/overlays/dev
        kubectl rollout status deployment/spring-actor-dev -n spring-actor-dev --timeout=5m
    
    - name: Run smoke tests
      run: |
        # Wait for service to be ready
        sleep 30
        
        # Test health endpoint
        HEALTH_URL="https://dev.spring-actor.example.com/actuator/health"
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_URL)
        
        if [ $STATUS -eq 200 ]; then
          echo "✅ Health check passed"
        else
          echo "❌ Health check failed with status $STATUS"
          exit 1
        fi

  deploy-staging:
    runs-on: ubuntu-latest
    needs: deploy-dev
    if: github.ref == 'refs/heads/main'
    environment:
      name: staging
      url: https://staging.spring-actor.example.com
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
    
    - name: Configure kubectl
      uses: azure/setup-kubectl@v3
    
    - name: Set up kubeconfig
      run: |
        mkdir -p $HOME/.kube
        echo "${{ secrets.KUBECONFIG_STAGING }}" | base64 -d > $HOME/.kube/config
    
    - name: Deploy to Staging
      run: |
        cd example/kubernetes/overlays/staging
        kustomize edit set image \
          your-registry/spring-actor-app=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
        kubectl apply -k .
        kubectl rollout status deployment/spring-actor-staging -n spring-actor --timeout=5m
    
    - name: Run integration tests
      run: |
        # Run comprehensive test suite against staging
        ./scripts/integration-tests.sh staging

  deploy-prod:
    runs-on: ubuntu-latest
    needs: deploy-staging
    if: startsWith(github.ref, 'refs/tags/v')
    environment:
      name: production
      url: https://spring-actor.example.com
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
    
    - name: Configure kubectl
      uses: azure/setup-kubectl@v3
    
    - name: Set up kubeconfig
      run: |
        mkdir -p $HOME/.kube
        echo "${{ secrets.KUBECONFIG_PROD }}" | base64 -d > $HOME/.kube/config
    
    - name: Extract version
      id: version
      run: echo "VERSION=${GITHUB_REF#refs/tags/}" >> $GITHUB_OUTPUT
    
    - name: Deploy to Production
      run: |
        cd example/kubernetes/overlays/prod
        kustomize edit set image \
          your-registry/spring-actor-app=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ steps.version.outputs.VERSION }}
        kubectl apply -k .
    
    - name: Monitor rollout
      run: |
        kubectl rollout status deployment/spring-actor-prod -n spring-actor --timeout=10m
    
    - name: Verify cluster health
      run: |
        POD=$(kubectl get pods -n spring-actor -l app=spring-actor -o jsonpath='{.items[0].metadata.name}')
        kubectl exec -n spring-actor $POD -- curl -s localhost:8558/cluster/members | jq
    
    - name: Create GitHub Release
      uses: softprops/action-gh-release@v1
      with:
        name: Release ${{ steps.version.outputs.VERSION }}
        generate_release_notes: true
    
    - name: Notify deployment
      if: success()
      run: |
        curl -X POST ${{ secrets.SLACK_WEBHOOK_URL }} \
          -H 'Content-Type: application/json' \
          -d '{
            "text": "🚀 Deployed spring-actor ${{ steps.version.outputs.VERSION }} to production",
            "channel": "#deployments"
          }'
```

### Required Secrets

Configure these in GitHub repository settings → Secrets:

- `KUBECONFIG_DEV`: Base64-encoded kubeconfig for dev cluster
- `KUBECONFIG_STAGING`: Base64-encoded kubeconfig for staging cluster
- `KUBECONFIG_PROD`: Base64-encoded kubeconfig for prod cluster
- `SLACK_WEBHOOK_URL`: Slack webhook for notifications (optional)

To encode kubeconfig:
```bash
cat ~/.kube/config | base64 -w 0
```

## GitLab CI/CD

Create `.gitlab-ci.yml`:

```yaml
stages:
  - build
  - test
  - security
  - package
  - deploy-dev
  - deploy-staging
  - deploy-prod

variables:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false"
  IMAGE_NAME: $CI_REGISTRY_IMAGE/spring-actor-app
  KUBECONFIG: /tmp/kubeconfig

before_script:
  - export GRADLE_USER_HOME=`pwd`/.gradle

build:
  stage: build
  image: openjdk:17-jdk
  script:
    - cd example/chat
    - ./gradlew build -x test
  artifacts:
    paths:
      - example/chat/build/libs/*.jar
    expire_in: 1 day
  cache:
    key: ${CI_COMMIT_REF_SLUG}
    paths:
      - .gradle/wrapper
      - .gradle/caches

test:
  stage: test
  image: openjdk:17-jdk
  script:
    - cd example/chat
    - ./gradlew test
  artifacts:
    when: always
    reports:
      junit: example/chat/build/test-results/test/**/TEST-*.xml
  coverage: '/Total.*?([0-9]{1,3})%/'

security:scan:
  stage: security
  image:
    name: aquasec/trivy:latest
    entrypoint: [""]
  script:
    - trivy fs --exit-code 0 --no-progress --format template --template "@/contrib/gitlab.tpl" -o gl-container-scanning-report.json example/chat
  artifacts:
    reports:
      container_scanning: gl-container-scanning-report.json

package:
  stage: package
  image: docker:latest
  services:
    - docker:dind
  script:
    - cd example/chat
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - docker build -t $IMAGE_NAME:$CI_COMMIT_SHA -f Dockerfile.kubernetes .
    - docker tag $IMAGE_NAME:$CI_COMMIT_SHA $IMAGE_NAME:latest
    - docker push $IMAGE_NAME:$CI_COMMIT_SHA
    - docker push $IMAGE_NAME:latest
  only:
    - main
    - develop
    - tags

deploy:dev:
  stage: deploy-dev
  image: bitnami/kubectl:latest
  script:
    - echo $KUBECONFIG_DEV | base64 -d > $KUBECONFIG
    - cd example/kubernetes/overlays/dev
    - kubectl apply -k .
    - kubectl set image deployment/spring-actor-dev spring-actor=$IMAGE_NAME:$CI_COMMIT_SHA -n spring-actor-dev
    - kubectl rollout status deployment/spring-actor-dev -n spring-actor-dev
  environment:
    name: development
    url: https://dev.spring-actor.example.com
  only:
    - develop

deploy:staging:
  stage: deploy-staging
  image: bitnami/kubectl:latest
  script:
    - echo $KUBECONFIG_STAGING | base64 -d > $KUBECONFIG
    - cd example/kubernetes/overlays/staging
    - kubectl apply -k .
    - kubectl set image deployment/spring-actor-staging spring-actor=$IMAGE_NAME:$CI_COMMIT_SHA -n spring-actor
    - kubectl rollout status deployment/spring-actor-staging -n spring-actor
  environment:
    name: staging
    url: https://staging.spring-actor.example.com
  when: manual
  only:
    - main

deploy:prod:
  stage: deploy-prod
  image: bitnami/kubectl:latest
  script:
    - echo $KUBECONFIG_PROD | base64 -d > $KUBECONFIG
    - cd example/kubernetes/overlays/prod
    - kubectl apply -k .
    - kubectl set image deployment/spring-actor-prod spring-actor=$IMAGE_NAME:$CI_COMMIT_TAG -n spring-actor
    - kubectl rollout status deployment/spring-actor-prod -n spring-actor
  environment:
    name: production
    url: https://spring-actor.example.com
  when: manual
  only:
    - tags
```

## Jenkins

Create `Jenkinsfile`:

```groovy
pipeline {
    agent any
    
    environment {
        REGISTRY = 'your-registry.io'
        IMAGE_NAME = 'spring-actor-app'
        GRADLE_HOME = tool 'Gradle 8'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Build') {
            steps {
                dir('example/chat') {
                    sh './gradlew clean build -x test'
                }
            }
        }
        
        stage('Test') {
            steps {
                dir('example/chat') {
                    sh './gradlew test'
                }
            }
            post {
                always {
                    junit 'example/chat/build/test-results/test/**/*.xml'
                }
            }
        }
        
        stage('Security Scan') {
            steps {
                sh 'trivy fs --exit-code 0 --severity HIGH,CRITICAL example/chat'
            }
        }
        
        stage('Build Image') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
                }
            }
            steps {
                script {
                    dir('example/chat') {
                        def imageTag = env.BRANCH_NAME == 'main' ? 'latest' : env.BRANCH_NAME
                        if (env.TAG_NAME) {
                            imageTag = env.TAG_NAME
                        }
                        
                        docker.build("${REGISTRY}/${IMAGE_NAME}:${imageTag}", "-f Dockerfile.kubernetes .")
                        
                        docker.withRegistry("https://${REGISTRY}", 'registry-credentials') {
                            docker.image("${REGISTRY}/${IMAGE_NAME}:${imageTag}").push()
                        }
                    }
                }
            }
        }
        
        stage('Deploy to Dev') {
            when {
                branch 'develop'
            }
            steps {
                script {
                    withCredentials([file(credentialsId: 'kubeconfig-dev', variable: 'KUBECONFIG')]) {
                        sh '''
                            cd example/kubernetes/overlays/dev
                            kubectl apply -k .
                            kubectl rollout status deployment/spring-actor-dev -n spring-actor-dev
                        '''
                    }
                }
            }
        }
        
        stage('Deploy to Prod') {
            when {
                tag pattern: 'v\\d+\\.\\d+\\.\\d+', comparator: 'REGEXP'
            }
            steps {
                input message: 'Deploy to production?', ok: 'Deploy'
                
                script {
                    withCredentials([file(credentialsId: 'kubeconfig-prod', variable: 'KUBECONFIG')]) {
                        sh '''
                            cd example/kubernetes/overlays/prod
                            kubectl apply -k .
                            kubectl rollout status deployment/spring-actor-prod -n spring-actor
                        '''
                    }
                }
            }
        }
    }
    
    post {
        success {
            slackSend(
                color: 'good',
                message: "✅ Build ${env.JOB_NAME} #${env.BUILD_NUMBER} succeeded"
            )
        }
        failure {
            slackSend(
                color: 'danger',
                message: "❌ Build ${env.JOB_NAME} #${env.BUILD_NUMBER} failed"
            )
        }
    }
}
```

## ArgoCD (GitOps)

### Install ArgoCD

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

### Create Application

Create `argocd/application.yaml`:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: spring-actor-prod
  namespace: argocd
spec:
  project: default
  
  source:
    repoURL: https://github.com/your-org/spring-boot-starter-actor.git
    targetRevision: main
    path: example/kubernetes/overlays/prod
  
  destination:
    server: https://kubernetes.default.svc
    namespace: spring-actor
  
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
      allowEmpty: false
    syncOptions:
    - CreateNamespace=true
    
    retry:
      limit: 5
      backoff:
        duration: 5s
        factor: 2
        maxDuration: 3m
  
  revisionHistoryLimit: 10
```

Apply:
```bash
kubectl apply -f argocd/application.yaml
```

### Image Updater

Install ArgoCD Image Updater:

```bash
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj-labs/argocd-image-updater/stable/manifests/install.yaml
```

Configure in `example/kubernetes/overlays/prod/kustomization.yaml`:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: spring-actor

bases:
- ../../base

images:
- name: your-registry/spring-actor-app
  newName: your-registry.io/spring-actor-app
  newTag: v1.0.0

# ArgoCD annotations
commonAnnotations:
  argocd-image-updater.argoproj.io/image-list: spring-actor=your-registry.io/spring-actor-app
  argocd-image-updater.argoproj.io/spring-actor.update-strategy: semver
  argocd-image-updater.argoproj.io/spring-actor.allow-tags: regexp:^v[0-9]+\.[0-9]+\.[0-9]+$
```

## Best Practices

### 1. Immutable Tags

Always use immutable tags (SHA or version):
```yaml
# ❌ Bad - mutable
image: spring-actor-app:latest

# ✅ Good - immutable
image: spring-actor-app:v1.2.3
image: spring-actor-app:sha-abc123
```

### 2. Multi-Stage Deployments

Always deploy through multiple environments:
```
dev → staging → production
```

### 3. Automated Rollback

Configure automatic rollback on failure:

```yaml
# In GitHub Actions
- name: Rollback on failure
  if: failure()
  run: |
    kubectl rollout undo deployment/spring-actor-prod -n spring-actor
```

### 4. Smoke Tests

Always run smoke tests after deployment:

```bash
#!/bin/bash
# smoke-test.sh

ENDPOINT="https://spring-actor.example.com"

# Test health endpoint
if ! curl -sf "$ENDPOINT/actuator/health" > /dev/null; then
  echo "Health check failed"
  exit 1
fi

# Test API endpoint
if ! curl -sf "$ENDPOINT/api/hello" | grep -q "Hello"; then
  echo "API test failed"
  exit 1
fi

# Check cluster members
POD=$(kubectl get pods -n spring-actor -l app=spring-actor -o jsonpath='{.items[0].metadata.name}')
MEMBERS=$(kubectl exec -n spring-actor $POD -- curl -s localhost:8558/cluster/members | jq '.members | length')

if [ "$MEMBERS" -lt 3 ]; then
  echo "Cluster has only $MEMBERS members (expected: 3+)"
  exit 1
fi

echo "✅ All smoke tests passed"
```

### 5. Notification

Always notify on deployment events:

```yaml
- name: Notify Slack
  if: always()
  uses: 8398a7/action-slack@v3
  with:
    status: ${{ job.status }}
    text: |
      Deployment to production ${{ job.status }}
      Version: ${{ github.ref }}
      Author: ${{ github.actor }}
    webhook_url: ${{ secrets.SLACK_WEBHOOK }}
```

### 6. Canary Releases

For production, consider canary deployments:

```yaml
# Deploy canary (10% traffic)
kubectl apply -k overlays/canary

# Wait and monitor
sleep 300

# If successful, promote to prod
kubectl apply -k overlays/prod
```

### 7. Feature Flags

Use feature flags for risky changes:

```yaml
env:
- name: FEATURE_NEW_API
  value: "false"  # Enable after deployment
```

### 8. Monitoring Integration

Integrate monitoring into CI/CD:

```yaml
- name: Check Prometheus alerts
  run: |
    ALERTS=$(curl -s http://prometheus:9090/api/v1/alerts | jq '.data.alerts | length')
    if [ "$ALERTS" -gt 0 ]; then
      echo "⚠️  Found $ALERTS active alerts"
      exit 1
    fi
```

## Troubleshooting

### Pipeline Failing at Build

```bash
# Check Gradle logs
./gradlew build --stacktrace

# Clear cache
./gradlew clean --no-daemon
```

### Image Push Failing

```bash
# Verify credentials
docker login your-registry.io

# Check quota
docker system df
```

### Deployment Hanging

```bash
# Check pod events
kubectl describe pod <pod-name> -n spring-actor

# Check rollout status
kubectl rollout status deployment/spring-actor-prod -n spring-actor --watch
```

---

For more information, see:
- [Production Guide](PRODUCTION_GUIDE.md)
- [Operations Runbook](OPERATIONS_RUNBOOK.md)
