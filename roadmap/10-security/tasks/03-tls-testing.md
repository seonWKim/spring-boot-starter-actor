# Task 1.3: TLS Testing and Documentation

**Priority:** HIGH
**Estimated Effort:** 3-4 days
**Dependencies:** Task 1.1 (TLS Configuration), Task 1.2 (Certificate Rotation)

---

## Overview

Create comprehensive tests for TLS/SSL functionality and production setup documentation. Tests must verify encrypted communication, certificate validation, and rotation scenarios.

---

## Testing Requirements

### 1. Cluster Formation with TLS

Test that cluster can form with TLS enabled:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.actor.pekko.remote.artery.ssl.enabled=true",
    "spring.actor.pekko.remote.artery.ssl.key-store=classpath:test-keystore.jks",
    "spring.actor.pekko.remote.artery.ssl.key-store-password=changeit",
    "spring.actor.pekko.remote.artery.ssl.trust-store=classpath:test-truststore.jks",
    "spring.actor.pekko.remote.artery.ssl.trust-store-password=changeit"
})
class TlsClusterFormationTest {
    
    @Autowired
    private SpringActorSystem actorSystem;
    
    @Test
    void clusterFormsWithTlsEnabled() {
        // Start two nodes with TLS
        ActorSystem node1 = startNode("node1", 2551);
        ActorSystem node2 = startNode("node2", 2552);
        
        // Wait for cluster to form
        await().atMost(30, SECONDS)
            .until(() -> clusterMembers(node1).size() == 2);
        
        // Verify both nodes are in cluster
        assertThat(clusterMembers(node1))
            .hasSize(2)
            .extracting(Member::address)
            .contains(
                Address.apply("pekko", "actor-system", "node1", 2551),
                Address.apply("pekko", "actor-system", "node2", 2552)
            );
            
        // Send message between nodes to verify encrypted communication
        TestProbe<String> probe = TestProbe.create(node1);
        ActorRef<String> remoteActor = spawnRemoteActor(node2, "test-actor");
        remoteActor.tell("hello");
        
        probe.expectMessage(Duration.ofSeconds(5), "hello");
    }
    
    @Test
    void unencryptedConnectionFailsWhenTlsEnabled() {
        // Start one node with TLS
        ActorSystem tlsNode = startNode("tls-node", 2551);
        
        // Try to start another node WITHOUT TLS
        assertThatThrownBy(() -> {
            startNodeWithoutTls("non-tls-node", 2552);
        })
        .hasMessageContaining("SSL handshake failed")
        .hasRootCauseInstanceOf(SSLHandshakeException.class);
    }
}
```

### 2. Certificate Validation Tests

Test certificate validation:

```java
@SpringBootTest
class CertificateValidationTest {
    
    @Autowired
    private KeystoreManager keystoreManager;
    
    @Test
    void validCertificatePassesValidation() throws Exception {
        KeyStore keyStore = keystoreManager.loadKeyStore(
            "classpath:valid-keystore.jks",
            "JKS",
            "changeit"
        );
        
        assertThatCode(() -> {
            keystoreManager.validateCertificateChain(keyStore);
        }).doesNotThrowAnyException();
    }
    
    @Test
    void expiredCertificateFailsValidation() throws Exception {
        KeyStore keyStore = keystoreManager.loadKeyStore(
            "classpath:expired-keystore.jks",
            "JKS",
            "changeit"
        );
        
        assertThatThrownBy(() -> {
            keystoreManager.validateCertificateChain(keyStore);
        })
        .isInstanceOf(CertificateExpiredException.class)
        .hasMessageContaining("Certificate has expired");
    }
    
    @Test
    void selfSignedCertificateWorksWithTruststore() throws Exception {
        // Load keystore with self-signed cert
        KeyStore keyStore = keystoreManager.loadKeyStore(
            "classpath:self-signed-keystore.jks",
            "JKS",
            "changeit"
        );
        
        // Load truststore containing the self-signed cert
        KeyStore trustStore = keystoreManager.loadKeyStore(
            "classpath:self-signed-truststore.jks",
            "JKS",
            "changeit"
        );
        
        // Should not throw exception
        assertThatCode(() -> {
            keystoreManager.validateCertificateChain(keyStore);
        }).doesNotThrowAnyException();
    }
    
    @Test
    void certificateInfoIsExtractedCorrectly() throws Exception {
        KeyStore keyStore = keystoreManager.loadKeyStore(
            "classpath:test-keystore.jks",
            "JKS",
            "changeit"
        );
        
        CertificateInfo info = keystoreManager.getCertificateInfo(
            keyStore,
            "test-cert"
        );
        
        assertThat(info.getSubject()).contains("CN=Test Certificate");
        assertThat(info.getIssuer()).contains("CN=Test CA");
        assertThat(info.getNotBefore()).isBefore(LocalDate.now());
        assertThat(info.getNotAfter()).isAfter(LocalDate.now());
    }
}
```

### 3. Certificate Rotation Tests

Test live certificate rotation:

```java
@SpringBootTest
class CertificateRotationTest {
    
    @TempDir
    Path tempDir;
    
    @Autowired
    private CertificateReloadManager reloadManager;
    
    @Test
    void certificateRotationWithoutDowntime() throws Exception {
        // Copy initial keystore to temp directory
        Path keystorePath = tempDir.resolve("keystore.jks");
        Files.copy(
            getClass().getResourceAsStream("/initial-keystore.jks"),
            keystorePath
        );
        
        // Start cluster with file-based keystore
        ActorSystem node = startNodeWithFileKeystore(
            "rotating-node",
            keystorePath.toString()
        );
        
        // Verify initial certificate
        CertificateInfo initialCert = getCurrentCertInfo(node);
        assertThat(initialCert.getSubject()).contains("CN=Initial");
        
        // Set up rotation listener to capture event
        CompletableFuture<CertificateInfo> rotationFuture = new CompletableFuture<>();
        reloadManager.addListener((oldCtx, newCtx, certInfo) -> {
            rotationFuture.complete(certInfo);
        });
        
        // Replace keystore file with new certificate
        Files.copy(
            getClass().getResourceAsStream("/rotated-keystore.jks"),
            keystorePath,
            StandardCopyOption.REPLACE_EXISTING
        );
        
        // Wait for rotation to complete
        CertificateInfo newCert = rotationFuture.get(30, SECONDS);
        assertThat(newCert.getSubject()).contains("CN=Rotated");
        
        // Verify cluster still functional
        assertThat(clusterMembers(node)).isNotEmpty();
        
        // Verify new certificate is in use
        CertificateInfo currentCert = getCurrentCertInfo(node);
        assertThat(currentCert.getSubject()).isEqualTo(newCert.getSubject());
    }
    
    @Test
    void existingConnectionsContinueDuringRotation() throws Exception {
        // Start two nodes
        ActorSystem node1 = startNode("node1", 2551);
        ActorSystem node2 = startNode("node2", 2552);
        
        // Create long-running communication
        TestProbe<String> probe = TestProbe.create(node1);
        ActorRef<String> remoteActor = spawnRemoteActor(node2, "test-actor");
        
        // Start sending messages
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        List<String> receivedMessages = new CopyOnWriteArrayList<>();
        
        scheduler.scheduleAtFixedRate(() -> {
            remoteActor.tell("ping");
            receivedMessages.add("ping");
        }, 0, 100, MILLISECONDS);
        
        // Trigger rotation on node2
        Thread.sleep(1000); // Let some messages flow
        rotateNodeCertificate(node2);
        
        // Continue sending messages during and after rotation
        Thread.sleep(5000);
        scheduler.shutdown();
        
        // Verify no messages were lost
        assertThat(receivedMessages.size()).isGreaterThan(40); // ~50 expected
    }
    
    @Test
    void failedRotationDoesNotBreakCluster() throws Exception {
        ActorSystem node = startNode("node", 2551);
        
        // Capture logs
        List<String> errorLogs = new ArrayList<>();
        
        // Attempt rotation with invalid certificate
        assertThatCode(() -> {
            reloadManager.reloadWithInvalidCertificate();
        }).doesNotThrowAnyException(); // Should not throw, just log error
        
        // Verify cluster still works with old certificate
        assertThat(clusterMembers(node)).isNotEmpty();
        
        // Verify error was logged
        assertThat(errorLogs)
            .anyMatch(log -> log.contains("Failed to reload certificates"));
    }
}
```

### 4. Mutual TLS (mTLS) Tests

Test mutual authentication:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.actor.pekko.remote.artery.ssl.require-mutual-authentication=true"
})
class MutualTlsTest {
    
    @Test
    void mutualAuthenticationEnforced() {
        // Start node with mTLS
        ActorSystem node1 = startNodeWithMutualTls("node1", 2551);
        
        // Try to connect without client certificate - should fail
        assertThatThrownBy(() -> {
            startNodeWithoutClientCert("node2", 2552);
        })
        .hasMessageContaining("Client certificate required")
        .hasRootCauseInstanceOf(SSLHandshakeException.class);
    }
    
    @Test
    void mutualAuthenticationWithValidClientCert() {
        // Both nodes have valid client certificates
        ActorSystem node1 = startNodeWithMutualTls("node1", 2551);
        ActorSystem node2 = startNodeWithMutualTls("node2", 2552);
        
        // Cluster should form successfully
        await().atMost(30, SECONDS)
            .until(() -> clusterMembers(node1).size() == 2);
    }
}
```

### 5. Configuration Tests

Test configuration binding and validation:

```java
@SpringBootTest
class TlsConfigurationTest {
    
    @Autowired
    private TlsConfigurationProperties props;
    
    @Test
    void configurationPropertiesBindCorrectly() {
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getKeyStore()).isEqualTo("classpath:keystore.jks");
        assertThat(props.getProtocol()).isEqualTo("TLSv1.3");
        assertThat(props.getEnabledAlgorithms())
            .contains("TLS_AES_256_GCM_SHA384");
    }
    
    @Test
    void environmentVariablesWorkForPasswords() {
        // Set environment variable
        System.setProperty("KEYSTORE_PASSWORD", "test-password");
        
        // Reload configuration
        TlsConfigurationProperties props = loadConfiguration();
        
        assertThat(props.getKeyStorePassword()).isEqualTo("test-password");
    }
    
    @Test
    void missingKeystoreFailsStartup() {
        assertThatThrownBy(() -> {
            startWithConfig(
                "spring.actor.pekko.remote.artery.ssl.enabled=true",
                "spring.actor.pekko.remote.artery.ssl.key-store=classpath:nonexistent.jks"
            );
        })
        .hasMessageContaining("Keystore not found")
        .isInstanceOf(FileNotFoundException.class);
    }
}
```

---

## Production Setup Guide

Create comprehensive production documentation:

### Document Structure

```markdown
# Production TLS/SSL Setup Guide

## Table of Contents
1. Prerequisites
2. Certificate Generation
3. Configuration
4. Deployment
5. Verification
6. Certificate Rotation
7. Troubleshooting
8. Best Practices

## 1. Prerequisites

- Java 11+
- Spring Boot 2.7+ or 3.2+
- OpenSSL or Java keytool
- Production-ready certificates (from CA or internal PKI)

## 2. Certificate Generation

### Using OpenSSL (Recommended)

Generate CA certificate:
```bash
# Generate CA private key
openssl genrsa -out ca-key.pem 4096

# Generate CA certificate
openssl req -new -x509 -days 3650 -key ca-key.pem -out ca-cert.pem \
  -subj "/CN=Actor System CA/O=Your Organization/C=US"
```

Generate server certificate:
```bash
# Generate server private key
openssl genrsa -out server-key.pem 2048

# Generate certificate signing request
openssl req -new -key server-key.pem -out server-csr.pem \
  -subj "/CN=actor.example.com/O=Your Organization/C=US"

# Sign with CA
openssl x509 -req -days 365 -in server-csr.pem \
  -CA ca-cert.pem -CAkey ca-key.pem -CAcreateserial \
  -out server-cert.pem
```

Convert to JKS keystore:
```bash
# Create PKCS12 keystore
openssl pkcs12 -export -in server-cert.pem -inkey server-key.pem \
  -out keystore.p12 -name server -password pass:changeit

# Convert to JKS (if needed)
keytool -importkeystore -srckeystore keystore.p12 \
  -srcstoretype PKCS12 -destkeystore keystore.jks \
  -deststoretype JKS -srcstorepass changeit -deststorepass changeit
```

Create truststore:
```bash
keytool -import -trustcacerts -alias ca -file ca-cert.pem \
  -keystore truststore.jks -storepass changeit -noprompt
```

## 3. Configuration

### Kubernetes Secrets

Store certificates as secrets:
```bash
kubectl create secret generic actor-tls \
  --from-file=keystore.jks=keystore.jks \
  --from-file=truststore.jks=truststore.jks \
  --from-literal=keystore-password=your-secure-password \
  --from-literal=truststore-password=your-secure-password
```

### Application Configuration

```yaml
spring:
  actor:
    pekko:
      remote:
        artery:
          ssl:
            enabled: true
            key-store: file:/etc/tls/keystore.jks
            key-store-password: ${KEYSTORE_PASSWORD}
            trust-store: file:/etc/tls/truststore.jks
            trust-store-password: ${TRUSTSTORE_PASSWORD}
            protocol: TLSv1.3
            enabled-algorithms:
              - TLS_AES_256_GCM_SHA384
              - TLS_AES_128_GCM_SHA256
```

## 4. Deployment

### Docker

Mount certificates as volumes:
```yaml
volumes:
  - name: tls-certs
    secret:
      secretName: actor-tls
      items:
        - key: keystore.jks
          path: keystore.jks
        - key: truststore.jks
          path: truststore.jks

volumeMounts:
  - name: tls-certs
    mountPath: /etc/tls
    readOnly: true
```

Set environment variables:
```yaml
env:
  - name: KEYSTORE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: actor-tls
        key: keystore-password
  - name: TRUSTSTORE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: actor-tls
        key: truststore-password
```

## 5. Verification

Check TLS is enabled:
```bash
# Check logs for TLS enablement
kubectl logs <pod-name> | grep "TLS/SSL enabled"

# Verify cluster formation
kubectl logs <pod-name> | grep "Cluster member is Up"

# Test connection (should fail without TLS)
openssl s_client -connect actor.example.com:2551
```

## 6. Certificate Rotation

Enable automatic rotation:
```yaml
spring:
  actor:
    pekko:
      remote:
        artery:
          ssl:
            rotation:
              enabled: true
              grace-period: 5m
              watch-files: true
```

Rotate certificates:
```bash
# Update Kubernetes secret
kubectl create secret generic actor-tls \
  --from-file=keystore.jks=new-keystore.jks \
  --from-file=truststore.jks=new-truststore.jks \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart pods to pick up new certificates (if not using file watching)
kubectl rollout restart deployment actor-system
```

## 7. Troubleshooting

### Common Issues

**SSL Handshake Failed**
- Check certificate validity dates
- Verify truststore contains CA certificate
- Check certificate hostname matches

**Certificate Expired**
- Rotate certificates immediately
- Check certificate expiry monitoring

**Connection Refused**
- Verify port is open
- Check firewall rules
- Verify TLS is enabled on both nodes

### Debug Logging

Enable SSL debug logging:
```yaml
logging:
  level:
    io.github.seonwkim.core.security.tls: DEBUG
    javax.net.ssl: DEBUG
```

## 8. Best Practices

1. **Use TLSv1.3** - Most secure protocol
2. **Strong Cipher Suites** - Use AES-256-GCM
3. **Certificate Rotation** - Rotate every 90 days
4. **Monitor Expiry** - Alert 30 days before expiry
5. **Secure Storage** - Never commit passwords to git
6. **Mutual TLS** - Enable for high-security environments
7. **Certificate Validation** - Always validate certificate chains
8. **Audit Logging** - Log all TLS events

## Security Checklist

- [ ] TLS 1.3 enabled
- [ ] Strong cipher suites configured
- [ ] Certificates from trusted CA
- [ ] Passwords stored in secrets
- [ ] Certificate expiry monitoring
- [ ] Rotation process documented
- [ ] mTLS enabled (if required)
- [ ] Logs reviewed for errors
```

---

## Test Data Requirements

Create test certificates:

```bash
#!/bin/bash
# generate-test-certs.sh

# Test CA
openssl genrsa -out test-ca-key.pem 2048
openssl req -new -x509 -days 365 -key test-ca-key.pem \
  -out test-ca-cert.pem -subj "/CN=Test CA"

# Valid certificate
openssl genrsa -out test-key.pem 2048
openssl req -new -key test-key.pem -out test-csr.pem \
  -subj "/CN=test.example.com"
openssl x509 -req -days 365 -in test-csr.pem \
  -CA test-ca-cert.pem -CAkey test-ca-key.pem \
  -out test-cert.pem -CAcreateserial

# Create JKS keystore
openssl pkcs12 -export -in test-cert.pem -inkey test-key.pem \
  -out test-keystore.p12 -password pass:changeit
keytool -importkeystore -srckeystore test-keystore.p12 \
  -srcstoretype PKCS12 -destkeystore test-keystore.jks \
  -deststoretype JKS -srcstorepass changeit -deststorepass changeit

# Create truststore
keytool -import -trustcacerts -alias ca -file test-ca-cert.pem \
  -keystore test-truststore.jks -storepass changeit -noprompt

# Expired certificate (backdated)
openssl req -new -x509 -days -365 -key test-key.pem \
  -out expired-cert.pem -subj "/CN=expired.example.com"
```

---

## Deliverables

1. ✅ TLS cluster formation tests
2. ✅ Certificate validation tests
3. ✅ Certificate rotation tests
4. ✅ Mutual TLS tests
5. ✅ Configuration tests
6. ✅ Production setup guide
7. ✅ Certificate generation scripts
8. ✅ Test certificates and keystores
9. ✅ Troubleshooting documentation

---

## Success Criteria

- [ ] All TLS tests pass
- [ ] Test coverage > 80%
- [ ] Production guide is comprehensive
- [ ] Certificate generation scripts work
- [ ] Troubleshooting guide covers common issues
- [ ] Best practices documented
- [ ] Security checklist included

---

## Notes

- Use real certificate scenarios in tests
- Test both success and failure cases
- Document every configuration option
- Provide clear error messages
- Include monitoring and alerting guidance
