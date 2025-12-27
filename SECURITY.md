# Security Policy

## Supported Versions

We release patches for security vulnerabilities for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 0.8.x   | :white_check_mark: |
| 0.7.x   | :white_check_mark: |
| 0.6.x   | :x:                |
| < 0.6   | :x:                |

## Reporting a Vulnerability

We take the security of spring-boot-starter-actor seriously. If you discover a security vulnerability, please follow these steps:

### How to Report

**Please DO NOT report security vulnerabilities through public GitHub issues.**

Instead, please report them through one of the following channels:

1. **GitHub Security Advisories** (Preferred)
   - Go to the [Security tab](https://github.com/seonwkim/spring-boot-starter-actor/security/advisories)
   - Click "Report a vulnerability"
   - Fill out the form with details

2. **Direct Contact**
   - Create a private issue and request contact from maintainers
   - We will respond with a secure channel for communication

### What to Include

Please include the following information in your report:

- **Type of vulnerability** (e.g., remote code execution, denial of service)
- **Full paths** of source file(s) related to the vulnerability
- **Location** of the affected source code (tag/branch/commit or URL)
- **Step-by-step instructions** to reproduce the issue
- **Proof-of-concept or exploit code** (if possible)
- **Impact** of the issue, including how an attacker might exploit it
- **Affected versions** of the library
- **Your suggested fix** (if any)

### What to Expect

After you report a vulnerability:

1. **Acknowledgment**: We will acknowledge receipt within **48 hours**
2. **Initial Assessment**: We will assess the vulnerability within **5 business days**
3. **Communication**: We will keep you informed about our progress
4. **Resolution**: We will work on a fix and coordinate disclosure
5. **Credit**: We will credit you in the security advisory (unless you prefer to remain anonymous)

### Disclosure Policy

- **Embargo Period**: We request that you do not publicly disclose the vulnerability until we have released a fix
- **Coordinated Disclosure**: We aim to release a fix within **90 days** of acknowledgment
- **Public Disclosure**: After the fix is released, we will publish a security advisory
- **CVE Assignment**: For high-severity issues, we will request a CVE identifier

## Security Best Practices

When using spring-boot-starter-actor in production:

### Cluster Security

1. **Enable TLS/SSL** for cluster communication:
   ```yaml
   spring:
     actor:
       pekko:
         remote:
           artery:
             transport: tls-tcp
   ```

2. **Use authentication** for cluster nodes
3. **Restrict network access** to cluster ports (default 2551)
4. **Use private networks** for cluster communication

### Message Security

1. **Validate input** in actor message handlers
2. **Sanitize data** before processing
3. **Use type-safe messages** to prevent injection attacks
4. **Implement timeouts** for actor operations

### Serialization Security

1. **Use allowlists** for deserializable classes
2. **Avoid deserializing untrusted data**
3. **Keep Jackson dependencies up-to-date** (Pekko requires 2.17.3+)

### Configuration Security

1. **Secure sensitive configuration** (passwords, keys)
2. **Use environment variables** for secrets
3. **Don't commit credentials** to version control
4. **Rotate credentials regularly**

### Dependency Security

1. **Keep dependencies updated**
2. **Monitor security advisories** for Pekko and Spring Boot
3. **Use dependency scanning tools** (e.g., Dependabot, Snyk)
4. **Review transitive dependencies**

## Known Security Considerations

### Actor Isolation

- Actors provide isolation but are not security boundaries
- Use proper authentication and authorization
- Don't rely solely on actor paths for access control

### Remote Actors

- Remote actor communication requires proper security
- Enable TLS/SSL for production clusters
- Validate all remote messages

### Serialization

- Jackson serialization can be vulnerable to deserialization attacks
- Keep Jackson updated to the latest version
- Configure serialization allowlists when possible

### Dead Letter Monitoring

- Dead letters may contain sensitive information
- Implement proper logging controls
- Avoid logging sensitive data in messages

## Security Updates

We will announce security updates through:

1. **GitHub Security Advisories**
2. **Release Notes**
3. **Discord Community** (for critical issues)

Subscribe to repository notifications to stay informed.

## Compliance

This project follows:

- OWASP Top 10 security guidelines
- CWE/SANS Top 25 Most Dangerous Software Errors
- Apache Software Foundation security policies

## Contact

For security-related questions that are not vulnerabilities:

- Open a [GitHub Discussion](https://github.com/seonwkim/spring-boot-starter-actor/discussions)
- Ask in our [Discord community](https://discord.com/channels/1439734161614045205/1439734162100846655)

For urgent security matters, use the private reporting channels mentioned above.

## Acknowledgments

We appreciate the security research community and thank all researchers who responsibly disclose vulnerabilities to us.

---

**Last Updated**: December 2025
