package io.github.seonwkim.core.security.tls;

import java.security.KeyStore;
import java.security.SecureRandom;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for TLS/SSL support in actor cluster communication.
 *
 * <p>This configuration is activated when {@code spring.actor.pekko.remote.artery.ssl.enabled} is
 * set to {@code true}.
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.actor.pekko.remote.artery.ssl", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TlsConfigurationProperties.class)
public class TlsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TlsAutoConfiguration.class);

    @Bean
    public KeystoreManager keystoreManager() {
        return new KeystoreManager();
    }

    @Bean
    public SSLContext actorSystemSSLContext(TlsConfigurationProperties props, KeystoreManager keystoreManager)
            throws Exception {

        log.info("Initializing TLS/SSL for actor system");
        log.info("  Protocol: {}", props.getProtocol());
        log.info("  Enabled algorithms: {}", props.getEnabledAlgorithms());
        log.info("  Mutual authentication: {}", props.isRequireMutualAuthentication());

        // Validate configuration
        if (props.getKeyStore() == null) {
            throw new IllegalStateException("keyStore must be configured when TLS is enabled");
        }
        if (props.getKeyStorePassword() == null) {
            throw new IllegalStateException("keyStorePassword must be configured when TLS is enabled");
        }
        if (props.getTrustStore() == null) {
            throw new IllegalStateException("trustStore must be configured when TLS is enabled");
        }
        if (props.getTrustStorePassword() == null) {
            throw new IllegalStateException("trustStorePassword must be configured when TLS is enabled");
        }

        // Load and validate keystore
        log.info("Loading keystore...");
        KeyStore keyStore =
                keystoreManager.loadKeyStore(props.getKeyStore(), props.getKeyStoreType(), props.getKeyStorePassword());

        keystoreManager.validateCertificateChain(keyStore);

        // Get and log certificate info
        try {
            String alias = keystoreManager.getFirstKeyAlias(keyStore);
            CertificateInfo certInfo = keystoreManager.getCertificateInfo(keyStore, alias);
            log.info("Certificate loaded:");
            log.info("  Subject: {}", certInfo.getSubject());
            log.info("  Issuer: {}", certInfo.getIssuer());
            log.info("  Valid from: {}", certInfo.getNotBefore());
            log.info("  Valid until: {}", certInfo.getNotAfter());
        } catch (Exception e) {
            log.warn("Could not retrieve certificate information", e);
        }

        // Load truststore
        log.info("Loading truststore...");
        KeyStore trustStore = keystoreManager.loadKeyStore(
                props.getTrustStore(), props.getTrustStoreType(), props.getTrustStorePassword());

        // Initialize KeyManagerFactory
        String keyPassword = props.getKeyPassword();
        if (keyPassword == null) {
            keyPassword = props.getKeyStorePassword();
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword.toCharArray());

        // Initialize TrustManagerFactory
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        // Create SSLContext
        SSLContext sslContext = SSLContext.getInstance(props.getProtocol());
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        log.info("TLS/SSL configuration completed successfully");

        return sslContext;
    }

    @Bean
    public TlsConfigurationLogger tlsConfigurationLogger() {
        return new TlsConfigurationLogger();
    }
}
