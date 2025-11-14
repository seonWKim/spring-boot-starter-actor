package io.github.seonwkim.core.security.tls;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for TLS/SSL settings in actor cluster communication.
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * spring:
 *   actor:
 *     pekko:
 *       remote:
 *         artery:
 *           ssl:
 *             enabled: true
 *             key-store: classpath:keystore.jks
 *             key-store-password: ${KEYSTORE_PASSWORD}
 *             trust-store: classpath:truststore.jks
 *             trust-store-password: ${TRUSTSTORE_PASSWORD}
 *             protocol: TLSv1.3
 * }</pre>
 */
@ConfigurationProperties(prefix = "spring.actor.pekko.remote.artery.ssl")
public class TlsConfigurationProperties {

    /** Whether TLS/SSL is enabled for actor communication */
    private boolean enabled = false;

    /** Location of the keystore (supports classpath: and file: prefixes) */
    @Nullable private String keyStore;

    /** Keystore type (JKS or PKCS12) */
    private String keyStoreType = "JKS";

    /** Password for the keystore */
    @Nullable private String keyStorePassword;

    /** Password for the private key (defaults to keystore password if not specified) */
    @Nullable private String keyPassword;

    /** Location of the truststore (supports classpath: and file: prefixes) */
    @Nullable private String trustStore;

    /** Truststore type (JKS or PKCS12) */
    private String trustStoreType = "JKS";

    /** Password for the truststore */
    @Nullable private String trustStorePassword;

    /** TLS protocol version (TLSv1.2 or TLSv1.3) */
    private String protocol = "TLSv1.3";

    /** Enabled cipher suites */
    private List<String> enabledAlgorithms = Arrays.asList("TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256");

    /** Whether mutual TLS authentication is required */
    private boolean requireMutualAuthentication = false;

    /** Certificate rotation configuration */
    private RotationConfig rotation = new RotationConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setKeyStore(@Nullable String keyStore) {
        this.keyStore = keyStore;
    }

    @Nullable public String getKeyStore() {
        return keyStore;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public void setKeyStorePassword(@Nullable String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    @Nullable public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyPassword(@Nullable String keyPassword) {
        this.keyPassword = keyPassword;
    }

    @Nullable public String getKeyPassword() {
        return keyPassword != null ? keyPassword : keyStorePassword;
    }

    public void setTrustStore(@Nullable String trustStore) {
        this.trustStore = trustStore;
    }

    @Nullable public String getTrustStore() {
        return trustStore;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    public void setTrustStorePassword(@Nullable String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    @Nullable public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public List<String> getEnabledAlgorithms() {
        return enabledAlgorithms;
    }

    public void setEnabledAlgorithms(List<String> enabledAlgorithms) {
        this.enabledAlgorithms = enabledAlgorithms;
    }

    public boolean isRequireMutualAuthentication() {
        return requireMutualAuthentication;
    }

    public void setRequireMutualAuthentication(boolean requireMutualAuthentication) {
        this.requireMutualAuthentication = requireMutualAuthentication;
    }

    public RotationConfig getRotation() {
        return rotation;
    }

    public void setRotation(RotationConfig rotation) {
        this.rotation = rotation;
    }

    /** Configuration for certificate rotation */
    public static class RotationConfig {
        /** Whether certificate rotation is enabled */
        private boolean enabled = false;

        /** Grace period for accepting both old and new certificates */
        private String gracePeriod = "5m";

        /** Whether to watch certificate files for changes */
        private boolean watchFiles = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getGracePeriod() {
            return gracePeriod;
        }

        public void setGracePeriod(String gracePeriod) {
            this.gracePeriod = gracePeriod;
        }

        public boolean isWatchFiles() {
            return watchFiles;
        }

        public void setWatchFiles(boolean watchFiles) {
            this.watchFiles = watchFiles;
        }
    }
}
