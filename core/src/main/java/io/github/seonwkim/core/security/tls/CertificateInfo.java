package io.github.seonwkim.core.security.tls;

import java.time.Instant;
import java.util.Objects;

/** Information about a certificate for logging and monitoring purposes. */
public class CertificateInfo {

    private final String subject;
    private final String issuer;
    private final Instant notBefore;
    private final Instant notAfter;
    private final String serialNumber;

    public CertificateInfo(String subject, String issuer, Instant notBefore, Instant notAfter, String serialNumber) {
        this.subject = Objects.requireNonNull(subject, "subject cannot be null");
        this.issuer = Objects.requireNonNull(issuer, "issuer cannot be null");
        this.notBefore = Objects.requireNonNull(notBefore, "notBefore cannot be null");
        this.notAfter = Objects.requireNonNull(notAfter, "notAfter cannot be null");
        this.serialNumber = Objects.requireNonNull(serialNumber, "serialNumber cannot be null");
    }

    public String getSubject() {
        return subject;
    }

    public String getIssuer() {
        return issuer;
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    public Instant getNotAfter() {
        return notAfter;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CertificateInfo that = (CertificateInfo) o;
        return Objects.equals(subject, that.subject)
                && Objects.equals(issuer, that.issuer)
                && Objects.equals(notBefore, that.notBefore)
                && Objects.equals(notAfter, that.notAfter)
                && Objects.equals(serialNumber, that.serialNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, issuer, notBefore, notAfter, serialNumber);
    }

    @Override
    public String toString() {
        return "CertificateInfo{"
                + "subject='"
                + subject
                + '\''
                + ", issuer='"
                + issuer
                + '\''
                + ", notBefore="
                + notBefore
                + ", notAfter="
                + notAfter
                + ", serialNumber='"
                + serialNumber
                + '\''
                + '}';
    }
}
