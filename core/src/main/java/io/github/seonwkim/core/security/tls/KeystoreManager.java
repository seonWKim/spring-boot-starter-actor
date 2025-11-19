package io.github.seonwkim.core.security.tls;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/** Manager for loading and validating keystores and certificates. */
public class KeystoreManager {

    private static final Logger log = LoggerFactory.getLogger(KeystoreManager.class);

    /**
     * Load a keystore from the specified location.
     *
     * @param location Location of the keystore (supports classpath: and file: prefixes)
     * @param type Keystore type (JKS or PKCS12)
     * @param password Keystore password
     * @return Loaded KeyStore
     * @throws Exception if the keystore cannot be loaded
     */
    public KeyStore loadKeyStore(String location, String type, String password) throws Exception {
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Keystore location cannot be null or empty");
        }

        if (password == null) {
            throw new IllegalArgumentException("Keystore password cannot be null");
        }

        KeyStore keyStore = KeyStore.getInstance(type);

        try (InputStream inputStream = openStream(location)) {
            keyStore.load(inputStream, password.toCharArray());
            log.info("Successfully loaded keystore from: {}", maskPath(location));
            return keyStore;
        } catch (IOException e) {
            log.error("Failed to load keystore from: {}", location, e);
            throw new IOException("Failed to load keystore from: " + location, e);
        }
    }

    /**
     * Validate the certificate chain in a keystore.
     *
     * @param keyStore KeyStore to validate
     * @throws CertificateException if validation fails
     */
    public void validateCertificateChain(KeyStore keyStore) throws CertificateException {
        try {
            Enumeration<String> aliases = keyStore.aliases();

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();

                if (keyStore.isCertificateEntry(alias) || keyStore.isKeyEntry(alias)) {
                    Certificate cert = keyStore.getCertificate(alias);

                    if (cert instanceof X509Certificate) {
                        X509Certificate x509Cert = (X509Certificate) cert;

                        try {
                            x509Cert.checkValidity();
                            log.debug("Certificate '{}' is valid", alias);
                        } catch (CertificateExpiredException e) {
                            String message =
                                    String.format("Certificate '%s' has expired on %s", alias, x509Cert.getNotAfter());
                            log.error(message);
                            throw new CertificateException(message, e);
                        } catch (CertificateNotYetValidException e) {
                            String message = String.format(
                                    "Certificate '%s' is not yet valid until %s", alias, x509Cert.getNotBefore());
                            log.error(message);
                            throw new CertificateException(message, e);
                        }

                        // Check for expiring soon (within 30 days)
                        long daysUntilExpiry =
                                (x509Cert.getNotAfter().getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);

                        if (daysUntilExpiry < 30) {
                            log.warn(
                                    "Certificate '{}' expires in {} days on {} - renewal required soon!",
                                    alias,
                                    daysUntilExpiry,
                                    x509Cert.getNotAfter());
                        }
                    }
                }
            }
        } catch (KeyStoreException e) {
            throw new CertificateException("Failed to validate certificate chain", e);
        }
    }

    /**
     * Get certificate information for a specific alias.
     *
     * @param keyStore KeyStore containing the certificate
     * @param alias Alias of the certificate
     * @return CertificateInfo containing certificate details
     * @throws Exception if certificate information cannot be extracted
     */
    public CertificateInfo getCertificateInfo(KeyStore keyStore, String alias) throws Exception {
        Certificate cert = keyStore.getCertificate(alias);

        if (cert == null) {
            throw new IllegalArgumentException("No certificate found for alias: " + alias);
        }

        if (!(cert instanceof X509Certificate)) {
            throw new IllegalArgumentException("Certificate is not an X509Certificate");
        }

        X509Certificate x509Cert = (X509Certificate) cert;

        return new CertificateInfo(
                x509Cert.getSubjectX500Principal().getName(),
                x509Cert.getIssuerX500Principal().getName(),
                x509Cert.getNotBefore().toInstant(),
                x509Cert.getNotAfter().toInstant(),
                x509Cert.getSerialNumber().toString(16));
    }

    /**
     * Get the first key alias in the keystore.
     *
     * @param keyStore KeyStore to search
     * @return First key alias found
     * @throws KeyStoreException if no key alias is found
     */
    public String getFirstKeyAlias(KeyStore keyStore) throws KeyStoreException {
        Enumeration<String> aliases = keyStore.aliases();

        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                return alias;
            }
        }

        throw new KeyStoreException("No key entry found in keystore");
    }

    /**
     * Open an input stream for the specified location.
     *
     * @param location Location (supports classpath: and file: prefixes)
     * @return InputStream
     * @throws IOException if the stream cannot be opened
     */
    private InputStream openStream(String location) throws IOException {
        if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                throw new IOException("Classpath resource not found: " + path);
            }
            return resource.getInputStream();
        } else if (location.startsWith("file:")) {
            String path = location.substring("file:".length());
            return new FileInputStream(path);
        } else {
            // Default to file system
            return new FileInputStream(location);
        }
    }

    /**
     * Mask sensitive parts of a file path for logging.
     *
     * @param path Path to mask
     * @return Masked path
     */
    private String maskPath(@Nullable String path) {
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
