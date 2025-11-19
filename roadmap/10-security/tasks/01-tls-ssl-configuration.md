# Task 1.1: TLS/SSL Configuration

**Priority:** HIGH (Critical for production clusters)
**Estimated Effort:** 1 week
**Dependencies:** None

---

## Overview

Implement TLS/SSL encryption for all cluster communication in Apache Pekko actor systems. This is critical for production deployments to ensure secure actor-to-actor communication across nodes.

---

## Requirements

### 1. Spring Boot YAML Configuration

Create comprehensive YAML configuration support for TLS/SSL settings:

```yaml
spring:
  actor:
    pekko:
      remote:
        artery:
          transport: tls-tcp  # Options: tcp, tls-tcp
          ssl:
            enabled: true
            
            # Keystore configuration
            key-store: classpath:keystore.jks
            key-store-type: JKS  # JKS, PKCS12
            key-store-password: ${KEYSTORE_PASSWORD}
            key-password: ${KEY_PASSWORD:${KEYSTORE_PASSWORD}}
            
            # Truststore configuration
            trust-store: classpath:truststore.jks
            trust-store-type: JKS  # JKS, PKCS12
            trust-store-password: ${TRUSTSTORE_PASSWORD}
            
            # TLS protocol and cipher configuration
            protocol: TLSv1.3  # TLSv1.2, TLSv1.3
            enabled-algorithms:
              - TLS_AES_256_GCM_SHA384
              - TLS_AES_128_GCM_SHA256
              - TLS_CHACHA20_POLY1305_SHA256
            
            # Client authentication
            require-mutual-authentication: false  # true for mTLS
```

### 2. Configuration Properties Class

Create a type-safe configuration properties class:

```java
@ConfigurationProperties(prefix = "spring.actor.pekko.remote.artery.ssl")
public class TlsConfigurationProperties {
    private boolean enabled = false;
    
    // Keystore
    private String keyStore;
    private String keyStoreType = "JKS";
    private String keyStorePassword;
    private String keyPassword;
    
    // Truststore
    private String trustStore;
    private String trustStoreType = "JKS";
    private String trustStorePassword;
    
    // Protocol
    private String protocol = "TLSv1.3";
    private List<String> enabledAlgorithms = Arrays.asList(
        "TLS_AES_256_GCM_SHA384",
        "TLS_AES_128_GCM_SHA256"
    );
    
    private boolean requireMutualAuthentication = false;
    
    // Getters and setters
}
```

### 3. Keystore and Truststore Management

Implement management utilities:

```java
public class KeystoreManager {
    
    /**
     * Load keystore from classpath or file system
     */
    public KeyStore loadKeyStore(String location, String type, String password) 
        throws Exception {
        // Support classpath: and file: prefixes
        // Validate keystore format and password
        // Return loaded KeyStore
    }
    
    /**
     * Validate certificate chain in keystore
     */
    public void validateCertificateChain(KeyStore keyStore) 
        throws CertificateException {
        // Check certificate validity dates
        // Verify certificate chain
        // Log warnings for certificates expiring soon
    }
    
    /**
     * Get certificate info for logging/monitoring
     */
    public CertificateInfo getCertificateInfo(KeyStore keyStore, String alias) {
        // Extract certificate details
        // Return structured info (subject, issuer, validity, etc.)
    }
}
```

### 4. SSL Context Configuration

Create SSL context with proper configuration:

```java
@Configuration
@EnableConfigurationProperties(TlsConfigurationProperties.class)
public class TlsAutoConfiguration {
    
    @Bean
    @ConditionalOnProperty(
        prefix = "spring.actor.pekko.remote.artery.ssl",
        name = "enabled",
        havingValue = "true"
    )
    public SSLContext actorSystemSSLContext(TlsConfigurationProperties props) 
        throws Exception {
        // Load keystore and truststore
        KeyStore keyStore = keystoreManager.loadKeyStore(
            props.getKeyStore(),
            props.getKeyStoreType(),
            props.getKeyStorePassword()
        );
        
        KeyStore trustStore = keystoreManager.loadKeyStore(
            props.getTrustStore(),
            props.getTrustStoreType(),
            props.getTrustStorePassword()
        );
        
        // Validate certificates
        keystoreManager.validateCertificateChain(keyStore);
        
        // Create SSL context
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        );
        kmf.init(keyStore, props.getKeyPassword().toCharArray());
        
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        );
        tmf.init(trustStore);
        
        SSLContext sslContext = SSLContext.getInstance(props.getProtocol());
        sslContext.init(
            kmf.getKeyManagers(),
            tmf.getTrustManagers(),
            new SecureRandom()
        );
        
        return sslContext;
    }
}
```

### 5. Pekko Configuration Integration

Integrate with Pekko's configuration system:

```java
public class PekkoTlsConfigurer {
    
    public Config buildTlsConfig(
        TlsConfigurationProperties props,
        SSLContext sslContext
    ) {
        if (!props.isEnabled()) {
            return ConfigFactory.empty();
        }
        
        return ConfigFactory.parseString(
            "pekko.remote.artery {\n" +
            "  transport = tls-tcp\n" +
            "  ssl {\n" +
            "    enabled-algorithms = [" + 
                String.join(", ", props.getEnabledAlgorithms()) + "]\n" +
            "    protocol = \"" + props.getProtocol() + "\"\n" +
            "    require-mutual-authentication = " + 
                props.isRequireMutualAuthentication() + "\n" +
            "  }\n" +
            "}"
        );
    }
}
```

### 6. Environment Variable Support

Support for passwords from environment variables:

- Use `${KEYSTORE_PASSWORD}` syntax in YAML
- Support default values: `${KEY_PASSWORD:${KEYSTORE_PASSWORD}}`
- Validate that passwords are not empty when TLS is enabled

### 7. Logging and Monitoring

Add comprehensive logging:

```java
@Slf4j
public class TlsConfigurationLogger {
    
    public void logConfiguration(TlsConfigurationProperties props) {
        if (props.isEnabled()) {
            log.info("TLS/SSL enabled for actor system");
            log.info("  Protocol: {}", props.getProtocol());
            log.info("  Enabled algorithms: {}", props.getEnabledAlgorithms());
            log.info("  Mutual authentication: {}", 
                props.isRequireMutualAuthentication());
            log.info("  Keystore: {}", maskPath(props.getKeyStore()));
            log.info("  Truststore: {}", maskPath(props.getTrustStore()));
        } else {
            log.warn("TLS/SSL is DISABLED for actor system - " +
                "NOT recommended for production");
        }
    }
    
    public void logCertificateInfo(CertificateInfo info) {
        log.info("Certificate loaded:");
        log.info("  Subject: {}", info.getSubject());
        log.info("  Issuer: {}", info.getIssuer());
        log.info("  Valid from: {}", info.getNotBefore());
        log.info("  Valid until: {}", info.getNotAfter());
        
        long daysUntilExpiry = ChronoUnit.DAYS.between(
            LocalDate.now(), 
            info.getNotAfter()
        );
        
        if (daysUntilExpiry < 30) {
            log.warn("Certificate expires in {} days - " +
                "renewal required soon!", daysUntilExpiry);
        }
    }
}
```

---

## Deliverables

1. ✅ `TlsConfigurationProperties` class
2. ✅ `KeystoreManager` utility class
3. ✅ `TlsAutoConfiguration` with SSLContext bean
4. ✅ `PekkoTlsConfigurer` for Pekko integration
5. ✅ `TlsConfigurationLogger` for logging
6. ✅ YAML configuration examples
7. ✅ Unit tests for all components
8. ✅ Integration tests for TLS cluster formation

---

## Success Criteria

- [ ] TLS/SSL can be enabled via YAML configuration
- [ ] Keystore and truststore are loaded correctly from classpath or filesystem
- [ ] Environment variables work for sensitive passwords
- [ ] Certificate validation catches invalid/expired certificates
- [ ] SSL context is properly configured with specified algorithms
- [ ] Configuration is logged clearly at startup
- [ ] Warnings appear for certificates expiring soon
- [ ] All tests pass

---

## Testing Strategy

### Unit Tests
- Test keystore loading from classpath
- Test keystore loading from filesystem
- Test certificate validation
- Test environment variable substitution
- Test configuration properties binding

### Integration Tests
- Test cluster formation with TLS enabled
- Test that unencrypted communication fails when TLS is enabled
- Test mutual authentication (mTLS)

---

## Notes

- Follow secure-by-default principles
- Never log sensitive passwords or keys
- Support both JKS and PKCS12 keystore formats
- Provide clear error messages for configuration issues
- Consider certificate expiry monitoring
