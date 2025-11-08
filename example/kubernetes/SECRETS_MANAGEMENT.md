# Secrets Management Guide

Guide for managing secrets in production Kubernetes deployments.

## Table of Contents

1. [Kubernetes Secrets](#kubernetes-secrets)
2. [External Secrets Operator](#external-secrets-operator)
3. [HashiCorp Vault](#hashicorp-vault)
4. [Cloud Provider Secrets](#cloud-provider-secrets)
5. [Best Practices](#best-practices)

## Kubernetes Secrets

### Basic Secret Creation

**From literals:**
```bash
kubectl create secret generic spring-actor-secrets \
  --from-literal=database-password=supersecret \
  --from-literal=api-key=abc123 \
  -n spring-actor
```

**From files:**
```bash
kubectl create secret generic spring-actor-tls \
  --from-file=tls.crt=./cert.pem \
  --from-file=tls.key=./key.pem \
  -n spring-actor
```

**From YAML (base64 encoded):**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: spring-actor-secrets
  namespace: spring-actor
type: Opaque
data:
  database-password: c3VwZXJzZWNyZXQ=  # base64 encoded
  api-key: YWJjMTIz                    # base64 encoded
```

### Using Secrets in Deployment

**As environment variables:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-actor
spec:
  template:
    spec:
      containers:
      - name: spring-actor
        env:
        # Single secret value
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: spring-actor-secrets
              key: database-password
        
        # All secrets as env vars
        envFrom:
        - secretRef:
            name: spring-actor-secrets
```

**As mounted files:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-actor
spec:
  template:
    spec:
      containers:
      - name: spring-actor
        volumeMounts:
        - name: secrets
          mountPath: /app/secrets
          readOnly: true
      
      volumes:
      - name: secrets
        secret:
          secretName: spring-actor-secrets
```

### Encrypting Secrets at Rest

**Enable encryption in Kubernetes:**

1. Create encryption config:
```yaml
# encryption-config.yaml
apiVersion: apiserver.config.k8s.io/v1
kind: EncryptionConfiguration
resources:
- resources:
  - secrets
  providers:
  - aescbc:
      keys:
      - name: key1
        secret: <BASE64_ENCODED_32_BYTE_KEY>
  - identity: {}
```

2. Update API server:
```bash
# Add to API server flags
--encryption-provider-config=/etc/kubernetes/encryption-config.yaml
```

3. Encrypt existing secrets:
```bash
kubectl get secrets --all-namespaces -o json | kubectl replace -f -
```

## External Secrets Operator

External Secrets Operator syncs secrets from external secret managers into Kubernetes.

### Installation

```bash
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets \
  external-secrets/external-secrets \
  -n external-secrets-system \
  --create-namespace
```

### AWS Secrets Manager

**1. Create IAM role for IRSA (IAM Roles for Service Accounts):**
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:us-east-1:123456789012:secret:spring-actor/*"
    }
  ]
}
```

**2. Create SecretStore:**
```yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secrets-manager
  namespace: spring-actor
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
      auth:
        jwt:
          serviceAccountRef:
            name: spring-actor-sa
```

**3. Create ExternalSecret:**
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: spring-actor-secrets
  namespace: spring-actor
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  
  target:
    name: spring-actor-secrets
    creationPolicy: Owner
  
  data:
  - secretKey: database-password
    remoteRef:
      key: spring-actor/database
      property: password
  
  - secretKey: api-key
    remoteRef:
      key: spring-actor/api
      property: key
```

### Google Secret Manager

**1. Create SecretStore:**
```yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: gcpsm-secret-store
  namespace: spring-actor
spec:
  provider:
    gcpsm:
      projectID: "my-gcp-project"
      auth:
        workloadIdentity:
          clusterLocation: us-central1-c
          clusterName: my-cluster
          serviceAccountRef:
            name: spring-actor-sa
```

**2. Create ExternalSecret:**
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: spring-actor-secrets
  namespace: spring-actor
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: gcpsm-secret-store
    kind: SecretStore
  
  target:
    name: spring-actor-secrets
  
  data:
  - secretKey: database-password
    remoteRef:
      key: spring-actor-database-password
  
  - secretKey: api-key
    remoteRef:
      key: spring-actor-api-key
```

### Azure Key Vault

**1. Create SecretStore:**
```yaml
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: azure-keyvault
  namespace: spring-actor
spec:
  provider:
    azurekv:
      vaultUrl: "https://my-vault.vault.azure.net"
      authType: WorkloadIdentity
      serviceAccountRef:
        name: spring-actor-sa
```

**2. Create ExternalSecret:**
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: spring-actor-secrets
  namespace: spring-actor
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: azure-keyvault
    kind: SecretStore
  
  target:
    name: spring-actor-secrets
  
  data:
  - secretKey: database-password
    remoteRef:
      key: spring-actor-database-password
  
  - secretKey: api-key
    remoteRef:
      key: spring-actor-api-key
```

## HashiCorp Vault

### Vault Agent Injector

**1. Install Vault:**
```bash
helm repo add hashicorp https://helm.releases.hashicorp.com
helm install vault hashicorp/vault \
  --namespace vault \
  --create-namespace \
  --set "injector.enabled=true"
```

**2. Configure Kubernetes auth:**
```bash
# Enable Kubernetes auth
vault auth enable kubernetes

# Configure
vault write auth/kubernetes/config \
  kubernetes_host="https://$KUBERNETES_PORT_443_TCP_ADDR:443" \
  token_reviewer_jwt="$(cat /var/run/secrets/kubernetes.io/serviceaccount/token)" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt

# Create policy
vault policy write spring-actor - <<EOF
path "secret/data/spring-actor/*" {
  capabilities = ["read"]
}
EOF

# Create role
vault write auth/kubernetes/role/spring-actor \
  bound_service_account_names=spring-actor-sa \
  bound_service_account_namespaces=spring-actor \
  policies=spring-actor \
  ttl=24h
```

**3. Create secrets in Vault:**
```bash
vault kv put secret/spring-actor/database password=supersecret
vault kv put secret/spring-actor/api key=abc123
```

**4. Annotate deployment to inject secrets:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-actor
spec:
  template:
    metadata:
      annotations:
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "spring-actor"
        vault.hashicorp.com/agent-inject-secret-database: "secret/data/spring-actor/database"
        vault.hashicorp.com/agent-inject-template-database: |
          {{- with secret "secret/data/spring-actor/database" -}}
          export DATABASE_PASSWORD="{{ .Data.data.password }}"
          {{- end -}}
    spec:
      serviceAccountName: spring-actor-sa
      containers:
      - name: spring-actor
        env:
        - name: DATABASE_PASSWORD
          value: "file:///vault/secrets/database"
```

### Vault CSI Driver

**1. Install CSI driver:**
```bash
helm install vault-csi-provider hashicorp/vault-csi-provider \
  --namespace vault \
  --create-namespace
```

**2. Create SecretProviderClass:**
```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: spring-actor-vault
  namespace: spring-actor
spec:
  provider: vault
  parameters:
    vaultAddress: "http://vault.vault:8200"
    roleName: "spring-actor"
    objects: |
      - objectName: "database-password"
        secretPath: "secret/data/spring-actor/database"
        secretKey: "password"
      - objectName: "api-key"
        secretPath: "secret/data/spring-actor/api"
        secretKey: "key"
  secretObjects:
  - secretName: spring-actor-secrets
    type: Opaque
    data:
    - objectName: database-password
      key: database-password
    - objectName: api-key
      key: api-key
```

**3. Mount in deployment:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-actor
spec:
  template:
    spec:
      serviceAccountName: spring-actor-sa
      containers:
      - name: spring-actor
        volumeMounts:
        - name: secrets-store
          mountPath: "/mnt/secrets-store"
          readOnly: true
        env:
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: spring-actor-secrets
              key: database-password
      
      volumes:
      - name: secrets-store
        csi:
          driver: secrets-store.csi.k8s.io
          readOnly: true
          volumeAttributes:
            secretProviderClass: "spring-actor-vault"
```

## Cloud Provider Secrets

### AWS Secrets Manager (Native)

Using AWS SDK directly in application:

**1. Add IAM permissions to pod:**
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spring-actor-sa
  namespace: spring-actor
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789012:role/spring-actor-role
```

**2. Application code:**
```java
@Configuration
public class SecretsConfig {
    
    @Bean
    public SecretsManagerClient secretsClient() {
        return SecretsManagerClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }
    
    @Bean
    public String databasePassword(SecretsManagerClient client) {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
            .secretId("spring-actor/database/password")
            .build();
        
        GetSecretValueResponse response = client.getSecretValue(request);
        return response.secretString();
    }
}
```

### GCP Secret Manager (Native)

**1. Add workload identity to service account:**
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spring-actor-sa
  namespace: spring-actor
  annotations:
    iam.gke.io/gcp-service-account: spring-actor@my-project.iam.gserviceaccount.com
```

**2. Application code:**
```java
@Configuration
public class SecretsConfig {
    
    @Bean
    public SecretManagerServiceClient secretsClient() throws IOException {
        return SecretManagerServiceClient.create();
    }
    
    @Bean
    public String databasePassword(SecretManagerServiceClient client) {
        SecretVersionName name = SecretVersionName.of(
            "my-project", 
            "spring-actor-database-password", 
            "latest"
        );
        
        AccessSecretVersionResponse response = client.accessSecretVersion(name);
        return response.getPayload().getData().toStringUtf8();
    }
}
```

## Best Practices

### 1. Never Commit Secrets

**Use .gitignore:**
```gitignore
# Secrets
*.key
*.pem
*.p12
secrets/
.env
```

**Pre-commit hook:**
```bash
#!/bin/bash
# .git/hooks/pre-commit

if git diff --cached --name-only | xargs grep -l "password\|secret\|key" > /dev/null; then
  echo "⚠️  Warning: Potential secret detected in commit"
  echo "Please review before committing"
  exit 1
fi
```

### 2. Rotate Secrets Regularly

**Automated rotation script:**
```bash
#!/bin/bash
# rotate-secrets.sh

# Generate new password
NEW_PASSWORD=$(openssl rand -base64 32)

# Update in secret manager
aws secretsmanager update-secret \
  --secret-id spring-actor/database/password \
  --secret-string "$NEW_PASSWORD"

# Trigger pod restart to pick up new secret
kubectl rollout restart deployment/spring-actor-prod -n spring-actor
```

### 3. Use Different Secrets Per Environment

```
secrets/
├── dev/
│   └── spring-actor-secrets.yaml
├── staging/
│   └── spring-actor-secrets.yaml
└── prod/
    └── spring-actor-secrets.yaml
```

### 4. Limit Secret Access

**RBAC for secrets:**
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: secret-reader
  namespace: spring-actor
rules:
- apiGroups: [""]
  resources: ["secrets"]
  resourceNames: ["spring-actor-secrets"]  # Only specific secrets
  verbs: ["get"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: spring-actor-secret-reader
  namespace: spring-actor
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: secret-reader
subjects:
- kind: ServiceAccount
  name: spring-actor-sa
  namespace: spring-actor
```

### 5. Audit Secret Access

**Enable audit logging:**
```yaml
apiVersion: audit.k8s.io/v1
kind: Policy
rules:
- level: RequestResponse
  resources:
  - group: ""
    resources: ["secrets"]
  namespaces: ["spring-actor"]
```

### 6. Use Sealed Secrets for GitOps

For storing encrypted secrets in Git:

**1. Install Sealed Secrets controller:**
```bash
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/controller.yaml
```

**2. Install kubeseal CLI:**
```bash
brew install kubeseal
```

**3. Create sealed secret:**
```bash
# Create secret
kubectl create secret generic spring-actor-secrets \
  --from-literal=password=supersecret \
  --dry-run=client -o yaml | \
kubeseal -o yaml > sealed-secret.yaml

# Commit sealed-secret.yaml to Git
git add sealed-secret.yaml
git commit -m "Add sealed secret"
```

**4. Apply sealed secret:**
```bash
kubectl apply -f sealed-secret.yaml
# Controller will decrypt and create the actual secret
```

### 7. Scan for Leaked Secrets

**Use git-secrets:**
```bash
# Install
brew install git-secrets

# Set up
cd your-repo
git secrets --install
git secrets --register-aws

# Scan
git secrets --scan
```

**Use gitleaks:**
```bash
# Install
brew install gitleaks

# Scan
gitleaks detect --source . --verbose
```

### 8. Use Temporary Credentials

Prefer short-lived credentials over long-lived secrets:
- IRSA (AWS)
- Workload Identity (GCP)
- Managed Identity (Azure)
- Service Account tokens (Kubernetes)

## Example: Complete Secret Setup

**Complete example using External Secrets Operator with AWS:**

```yaml
# 1. Service account with IRSA
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spring-actor-sa
  namespace: spring-actor
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789012:role/spring-actor-role

---
# 2. Secret store
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secrets
  namespace: spring-actor
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
      auth:
        jwt:
          serviceAccountRef:
            name: spring-actor-sa

---
# 3. External secret
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: spring-actor-secrets
  namespace: spring-actor
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets
    kind: SecretStore
  target:
    name: spring-actor-secrets
    creationPolicy: Owner
  dataFrom:
  - extract:
      key: spring-actor/prod

---
# 4. Use in deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-actor-prod
  namespace: spring-actor
spec:
  template:
    spec:
      serviceAccountName: spring-actor-sa
      containers:
      - name: spring-actor
        envFrom:
        - secretRef:
            name: spring-actor-secrets
```

## Troubleshooting

### Secret not found

```bash
# Check secret exists
kubectl get secrets -n spring-actor

# Check secret content
kubectl get secret spring-actor-secrets -n spring-actor -o yaml

# Decode secret
kubectl get secret spring-actor-secrets -n spring-actor -o jsonpath='{.data.password}' | base64 -d
```

### External Secrets not syncing

```bash
# Check External Secret status
kubectl describe externalsecret spring-actor-secrets -n spring-actor

# Check SecretStore
kubectl describe secretstore aws-secrets -n spring-actor

# Check ESO logs
kubectl logs -n external-secrets-system -l app.kubernetes.io/name=external-secrets
```

### Permission denied

```bash
# Check service account
kubectl describe sa spring-actor-sa -n spring-actor

# Check RBAC
kubectl auth can-i get secrets --as=system:serviceaccount:spring-actor:spring-actor-sa -n spring-actor
```

---

For more information, see:
- [Production Guide](PRODUCTION_GUIDE.md)
- [Operations Runbook](OPERATIONS_RUNBOOK.md)
