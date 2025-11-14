package io.github.seonwkim.core.security.tls;

import java.time.temporal.ChronoUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logger for TLS/SSL configuration and certificate rotation events. */
public class TlsConfigurationLogger {

    private static final Logger log = LoggerFactory.getLogger(TlsConfigurationLogger.class);

    /**
     * Log TLS configuration details at startup.
     *
     * @param props TLS configuration properties
     */
    public void logConfiguration(TlsConfigurationProperties props) {
        if (props.isEnabled()) {
            log.info("TLS/SSL enabled for actor system");
            log.info("  Protocol: {}", props.getProtocol());
            log.info("  Enabled algorithms: {}", props.getEnabledAlgorithms());
            log.info("  Mutual authentication: {}", props.isRequireMutualAuthentication());
            log.info("  Keystore: {}", maskPath(props.getKeyStore()));
            log.info("  Truststore: {}", maskPath(props.getTrustStore()));

            if (props.getRotation().isEnabled()) {
                log.info("  Certificate rotation: ENABLED");
                log.info("    Grace period: {}", props.getRotation().getGracePeriod());
                log.info("    Watch files: {}", props.getRotation().isWatchFiles());
            }
        } else {
            log.warn("TLS/SSL is DISABLED for actor system - NOT recommended for production");
        }
    }

    /**
     * Log certificate information.
     *
     * @param info Certificate information
     */
    public void logCertificateInfo(CertificateInfo info) {
        log.info("Certificate loaded:");
        log.info("  Subject: {}", info.getSubject());
        log.info("  Issuer: {}", info.getIssuer());
        log.info("  Valid from: {}", info.getNotBefore());
        log.info("  Valid until: {}", info.getNotAfter());
        log.info("  Serial number: {}", info.getSerialNumber());

        long daysUntilExpiry = ChronoUnit.DAYS.between(java.time.Instant.now(), info.getNotAfter());

        if (daysUntilExpiry < 30) {
            log.warn("Certificate expires in {} days - renewal required soon!", daysUntilExpiry);
        } else if (daysUntilExpiry < 7) {
            log.error("Certificate expires in {} days - URGENT renewal required!", daysUntilExpiry);
        }
    }

    /**
     * Log certificate rotation started event.
     */
    public void logRotationStarted() {
        log.info("================================================================================");
        log.info("Certificate rotation STARTED");
        log.info("================================================================================");
    }

    /**
     * Log certificate rotation completed event.
     *
     * @param newCert New certificate information
     */
    public void logRotationCompleted(CertificateInfo newCert) {
        log.info("================================================================================");
        log.info("Certificate rotation COMPLETED");
        log.info("New certificate details:");
        log.info("  Subject: {}", newCert.getSubject());
        log.info("  Valid until: {}", newCert.getNotAfter());
        log.info("================================================================================");
    }

    /**
     * Log certificate rotation failed event.
     *
     * @param e Exception that caused the failure
     */
    public void logRotationFailed(Exception e) {
        log.error("================================================================================");
        log.error("Certificate rotation FAILED - continuing with old certificate");
        log.error("Error: {}", e.getMessage());
        log.error("================================================================================");
    }

    /**
     * Mask sensitive parts of a file path for logging.
     *
     * @param path Path to mask
     * @return Masked path
     */
    @Nullable private String maskPath(@Nullable String path) {
        if (path == null) {
            return "null";
        }
        // Only show the filename, not the full path for security
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return "***/" + path.substring(lastSlash + 1);
        }
        return path;
    }
}
