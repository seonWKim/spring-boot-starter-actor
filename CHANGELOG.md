# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- CONTRIBUTING.md with comprehensive contribution guidelines
- CODE_OF_CONDUCT.md for community standards
- SECURITY.md with vulnerability reporting process
- CHANGELOG.md for tracking version history
- Enhanced issue templates and PR template
- .editorconfig for consistent code formatting

## [0.8.0] - 2025-01-XX

### Added
- Support for routers with multiple routing strategies (Round Robin, Smallest Mailbox, etc.)
- MDC (Mapped Diagnostic Context) logging support for better traceability
- Enhanced dispatcher configuration options
- Pub/Sub topics for event-driven communication within actors
- Comprehensive routing guide in documentation
- Logging guide with MDC and tags examples

### Changed
- Improved error messages with more context
- Enhanced documentation structure with better navigation
- Updated examples with router patterns

### Fixed
- Various bug fixes and stability improvements

## [0.7.x] - 2024-XX-XX

### Added
- Sharded actors support for distributed workload
- Cluster configuration improvements
- Persistence patterns with Spring Boot integration guide

### Changed
- Enhanced cluster configuration options
- Improved actor lifecycle management

### Fixed
- Cluster formation edge cases
- Serialization issues with complex message types

## [0.6.x] - 2024-XX-XX

### Added
- Initial cluster support
- Sharding capabilities
- Basic metrics collection with ByteBuddy instrumentation
- `actor.processing-time` metric

### Changed
- Simplified actor creation API
- Enhanced Spring Boot integration

## [0.5.x] - 2024-XX-XX

### Added
- Supervision strategies (restart, stop, resume)
- Child actor management
- Error handling improvements

### Changed
- Improved actor behavior builder API
- Enhanced type safety for messages

## [0.4.x] - 2024-XX-XX

### Added
- Ask pattern with timeout support
- CompletionStage-based async operations
- Actor lifecycle hooks

### Changed
- Simplified configuration structure

## [0.3.0] - 2024-XX-XX

### Added
- Spring Boot 3.x support (separate artifact)
- `@EnableActorSupport` annotation
- Auto-configuration for ActorSystem
- Dependency injection for actors

### Changed
- Split into separate modules for Boot 2 and Boot 3
- Improved Spring integration

## [0.2.x] - 2024-XX-XX

### Added
- Basic actor spawning and messaging
- Tell and Ask communication patterns
- Actor handle API

### Changed
- Refined actor creation API

## [0.1.0] - 2024-XX-XX

### Added
- Initial release
- Core actor model integration with Spring Boot
- Basic ActorSystem configuration
- Simple actor creation and messaging

---

## Version Support

- **0.8.x**: Current stable release (Spring Boot 2.7.x & 3.2.x)
- **0.7.x**: Previous stable release (maintenance mode)
- **< 0.6.x**: No longer supported

## Migration Guides

### Migrating to 0.8.x from 0.7.x

No breaking changes. New features:
- Router support (opt-in)
- MDC logging (opt-in)
- Enhanced dispatcher configuration

### Migrating to 0.7.x from 0.6.x

- Update sharding configuration format (see docs)
- Review cluster configuration for new options

## Links

- [Documentation](https://seonwkim.github.io/spring-boot-starter-actor/)
- [GitHub Repository](https://github.com/seonwkim/spring-boot-starter-actor)
- [Issue Tracker](https://github.com/seonwkim/spring-boot-starter-actor/issues)
- [Maven Central - Boot 2](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor)
- [Maven Central - Boot 3](https://central.sonatype.com/artifact/io.github.seonwkim/spring-boot-starter-actor_3)

---

**Note**: Dates are placeholders and should be updated with actual release dates.
