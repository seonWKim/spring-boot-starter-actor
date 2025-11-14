# Task 1.2: Certificate Rotation Support

**Priority:** HIGH (Critical for production uptime)
**Estimated Effort:** 1 week
**Dependencies:** Task 1.1 (TLS/SSL Configuration)

---

## Overview

Implement hot certificate rotation to allow updating TLS certificates without cluster downtime. This is essential for production systems that need to rotate certificates periodically without service interruption.

---

## Requirements

### 1. File Watching for Certificate Updates

Implement a certificate file watcher:

```java
@Slf4j
public class CertificateFileWatcher {
    
    private final WatchService watchService;
    private final Map<Path, Consumer<Path>> watchers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    
    public CertificateFileWatcher() throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat("cert-watcher-%d")
                .setDaemon(true)
                .build()
        );
    }
    
    /**
     * Watch a keystore/truststore file for changes
     */
    public void watchFile(Path file, Consumer<Path> onChange) {
        Path directory = file.getParent();
        
        try {
            directory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE
            );
            
            watchers.put(file, onChange);
            log.info("Watching certificate file: {}", file);
            
            // Start watching in background
            executor.submit(this::processEvents);
            
        } catch (IOException e) {
            log.error("Failed to watch certificate file: {}", file, e);
        }
    }
    
    private void processEvents() {
        while (true) {
            WatchKey key;
            try {
                key = watchService.take(); // Wait for events
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            
            for (WatchEvent<?> event : key.pollEvents()) {
                Path changed = (Path) event.context();
                
                // Check if any watched file changed
                watchers.forEach((watchedFile, callback) -> {
                    if (watchedFile.getFileName().equals(changed)) {
                        log.info("Certificate file changed: {}", changed);
                        callback.accept(watchedFile);
                    }
                });
            }
            
            if (!key.reset()) {
                break;
            }
        }
    }
    
    public void stop() {
        executor.shutdownNow();
        try {
            watchService.close();
        } catch (IOException e) {
            log.error("Error closing watch service", e);
        }
    }
}
```

### 2. Certificate Reload Manager

Manage certificate reloading:

```java
@Slf4j
public class CertificateReloadManager {
    
    private final KeystoreManager keystoreManager;
    private final TlsConfigurationProperties props;
    private final CertificateFileWatcher fileWatcher;
    private final List<CertificateReloadListener> listeners = new CopyOnWriteArrayList<>();
    
    private volatile SSLContext currentSslContext;
    private volatile CertificateInfo currentCertInfo;
    
    public void initialize() {
        // Load initial certificates
        reloadCertificates();
        
        // Watch keystore file
        if (props.getKeyStore().startsWith("file:")) {
            Path keystorePath = Paths.get(
                props.getKeyStore().substring("file:".length())
            );
            fileWatcher.watchFile(keystorePath, path -> {
                log.info("Keystore file changed, initiating reload...");
                reloadCertificates();
            });
        }
        
        // Watch truststore file
        if (props.getTrustStore().startsWith("file:")) {
            Path truststorePath = Paths.get(
                props.getTrustStore().substring("file:".length())
            );
            fileWatcher.watchFile(truststorePath, path -> {
                log.info("Truststore file changed, initiating reload...");
                reloadCertificates();
            });
        }
    }
    
    public synchronized void reloadCertificates() {
        try {
            log.info("Reloading certificates...");
            
            // Load new keystore
            KeyStore newKeyStore = keystoreManager.loadKeyStore(
                props.getKeyStore(),
                props.getKeyStoreType(),
                props.getKeyStorePassword()
            );
            
            // Validate new certificates
            keystoreManager.validateCertificateChain(newKeyStore);
            
            // Load new truststore
            KeyStore newTrustStore = keystoreManager.loadKeyStore(
                props.getTrustStore(),
                props.getTrustStoreType(),
                props.getTrustStorePassword()
            );
            
            // Create new SSL context
            SSLContext newSslContext = createSslContext(
                newKeyStore,
                newTrustStore
            );
            
            // Get certificate info for logging
            CertificateInfo newCertInfo = keystoreManager.getCertificateInfo(
                newKeyStore,
                getKeyAlias(newKeyStore)
            );
            
            // Atomically update
            SSLContext oldContext = this.currentSslContext;
            this.currentSslContext = newSslContext;
            this.currentCertInfo = newCertInfo;
            
            // Notify listeners
            notifyListeners(oldContext, newSslContext, newCertInfo);
            
            log.info("Successfully reloaded certificates");
            log.info("New certificate valid until: {}", 
                newCertInfo.getNotAfter());
            
        } catch (Exception e) {
            log.error("Failed to reload certificates - " +
                "continuing with current certificates", e);
        }
    }
    
    public void addListener(CertificateReloadListener listener) {
        listeners.add(listener);
    }
    
    private void notifyListeners(
        SSLContext oldContext,
        SSLContext newContext,
        CertificateInfo certInfo
    ) {
        for (CertificateReloadListener listener : listeners) {
            try {
                listener.onCertificateReload(oldContext, newContext, certInfo);
            } catch (Exception e) {
                log.error("Listener failed during certificate reload", e);
            }
        }
    }
}
```

### 3. Certificate Reload Listener

Define listener interface:

```java
public interface CertificateReloadListener {
    
    /**
     * Called when certificates are reloaded
     *
     * @param oldContext Previous SSL context (may be null on first load)
     * @param newContext New SSL context
     * @param certInfo Information about the new certificate
     */
    void onCertificateReload(
        SSLContext oldContext,
        SSLContext newContext,
        CertificateInfo certInfo
    );
}
```

### 4. Graceful Rotation Without Downtime

Implement graceful rotation strategy:

```java
@Slf4j
public class GracefulCertificateRotator implements CertificateReloadListener {
    
    private final Duration gracePeriod;
    private final ScheduledExecutorService scheduler;
    
    @Override
    public void onCertificateReload(
        SSLContext oldContext,
        SSLContext newContext,
        CertificateInfo certInfo
    ) {
        log.info("Starting graceful certificate rotation...");
        
        // Phase 1: Accept both old and new certificates (grace period)
        log.info("Grace period: accepting both old and new certificates for {}",
            gracePeriod);
        
        // Schedule transition to new certificate only
        scheduler.schedule(() -> {
            log.info("Grace period ended - using only new certificate");
            completeRotation(newContext);
        }, gracePeriod.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    private void completeRotation(SSLContext newContext) {
        // Update actor system to use only new certificate
        // This is where we integrate with Pekko's SSL configuration
        log.info("Certificate rotation completed successfully");
    }
}
```

### 5. Integration with Kubernetes cert-manager

Support automatic certificate renewal from cert-manager:

```java
@Configuration
@ConditionalOnProperty(
    prefix = "spring.actor.pekko.remote.artery.ssl",
    name = "cert-manager.enabled",
    havingValue = "true"
)
public class CertManagerIntegration {
    
    /**
     * Watch Kubernetes secrets for certificate updates
     */
    @Bean
    public CertManagerWatcher certManagerWatcher(
        TlsConfigurationProperties props
    ) {
        return new CertManagerWatcher(
            props.getCertManager().getSecretName(),
            props.getCertManager().getNamespace(),
            props.getCertManager().getTlsSecretKey(),
            props.getCertManager().getTlsCertKey()
        );
    }
}

@Slf4j
public class CertManagerWatcher {
    
    private final String secretName;
    private final String namespace;
    
    /**
     * Watch cert-manager secret for updates
     */
    public void watchSecret(Consumer<CertificateUpdate> onUpdate) {
        // Use Kubernetes client to watch secret
        // When secret changes, extract tls.crt and tls.key
        // Trigger certificate reload
        
        log.info("Watching cert-manager secret: {}/{}", namespace, secretName);
    }
}
```

YAML configuration for cert-manager:

```yaml
spring:
  actor:
    pekko:
      remote:
        artery:
          ssl:
            enabled: true
            cert-manager:
              enabled: true
              secret-name: actor-tls-cert
              namespace: default
              tls-cert-key: tls.crt
              tls-key-key: tls.key
            
            # Rotation settings
            rotation:
              enabled: true
              grace-period: 5m  # Accept both old and new certs
              watch-files: true
```

### 6. Rotation Logging and Events

Comprehensive logging for rotation events:

```java
@Slf4j
public class CertificateRotationLogger {
    
    public void logRotationStarted() {
        log.info("=".repeat(80));
        log.info("Certificate rotation STARTED");
        log.info("=".repeat(80));
    }
    
    public void logRotationCompleted(CertificateInfo newCert) {
        log.info("=".repeat(80));
        log.info("Certificate rotation COMPLETED");
        log.info("New certificate details:");
        log.info("  Subject: {}", newCert.getSubject());
        log.info("  Valid until: {}", newCert.getNotAfter());
        log.info("=".repeat(80));
    }
    
    public void logRotationFailed(Exception e) {
        log.error("=".repeat(80));
        log.error("Certificate rotation FAILED - continuing with old certificate");
        log.error("Error: {}", e.getMessage());
        log.error("=".repeat(80));
    }
    
    public void logGracePeriodStart(Duration duration) {
        log.info("Grace period started - accepting both old and new " +
            "certificates for {}", duration);
    }
    
    public void logGracePeriodEnd() {
        log.info("Grace period ended - now using only new certificate");
    }
}
```

---

## Deliverables

1. ✅ `CertificateFileWatcher` for file system monitoring
2. ✅ `CertificateReloadManager` for managing reloads
3. ✅ `CertificateReloadListener` interface
4. ✅ `GracefulCertificateRotator` implementation
5. ✅ `CertManagerIntegration` for Kubernetes
6. ✅ `CertManagerWatcher` for secret monitoring
7. ✅ `CertificateRotationLogger` for events
8. ✅ YAML configuration examples
9. ✅ Kubernetes setup documentation

---

## Success Criteria

- [ ] Certificates can be rotated without cluster restart
- [ ] File changes are detected within 5 seconds
- [ ] Grace period allows gradual transition
- [ ] No connection errors during rotation
- [ ] Kubernetes cert-manager integration works
- [ ] Rotation events are logged clearly
- [ ] Failed rotations don't break the cluster
- [ ] All tests pass

---

## Testing Strategy

### Unit Tests
- Test file watcher detects changes
- Test certificate reload logic
- Test grace period scheduling
- Test listener notifications

### Integration Tests
- Test live rotation in a running cluster
- Test that existing connections continue during rotation
- Test new connections use new certificate after rotation
- Test cert-manager secret updates

### Manual Testing
- Deploy to Kubernetes with cert-manager
- Trigger certificate renewal
- Verify cluster continues functioning
- Verify logs show rotation events

---

## Documentation Requirements

Create Kubernetes setup guide:

```markdown
# Setting up TLS with cert-manager

## Prerequisites
- Kubernetes cluster
- cert-manager installed

## Steps

1. Create Certificate resource:
```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: actor-tls-cert
spec:
  secretName: actor-tls-cert
  duration: 2160h # 90 days
  renewBefore: 360h # 15 days
  issuerRef:
    name: letsencrypt-prod
    kind: ClusterIssuer
  dnsNames:
    - actor.example.com
```

2. Configure application:
```yaml
spring:
  actor:
    pekko:
      remote:
        artery:
          ssl:
            enabled: true
            cert-manager:
              enabled: true
              secret-name: actor-tls-cert
```

3. Deploy and verify automatic renewal
```

---

## Notes

- File watching uses Java NIO WatchService
- Grace period default: 5 minutes
- Rotation should be transparent to client code
- Consider metrics for rotation success/failure
- Log rotation events prominently for auditing
