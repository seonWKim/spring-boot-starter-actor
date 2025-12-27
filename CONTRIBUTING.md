# Contributing to Spring Boot Starter Actor

Thank you for your interest in contributing to spring-boot-starter-actor! We welcome contributions from the community.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Submitting Changes](#submitting-changes)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Features](#suggesting-features)

## Code of Conduct

This project adheres to the Contributor Covenant [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/spring-boot-starter-actor.git
   cd spring-boot-starter-actor
   ```
3. **Add upstream remote**:
   ```bash
   git remote add upstream https://github.com/seonwkim/spring-boot-starter-actor.git
   ```

## How to Contribute

### Types of Contributions

- **Bug fixes**: Fix issues found in the codebase
- **Features**: Implement new features from the [roadmap](roadmap/ROADMAP.md)
- **Documentation**: Improve docs, examples, or tutorials
- **Tests**: Add or improve test coverage
- **Code quality**: Refactoring, performance improvements

### Before You Start

1. **Check existing issues**: Look for related issues or discussions
2. **Create an issue**: If none exists, create one describing your contribution
3. **Discuss your approach**: Wait for maintainer feedback before significant work
4. **Check the roadmap**: See [roadmap/ROADMAP.md](roadmap/ROADMAP.md) for planned features

## Development Setup

### Prerequisites

- **Java 11 or higher** (Java 17 recommended)
- **Gradle 8.x** (included via wrapper)
- **Git**

### Building the Project

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :core:build
./gradlew :metrics:build

# Skip tests for faster build
./gradlew build -x test
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :core:test
./gradlew :metrics:test

# Run test with parallel execution
./gradlew runTest

# Run tests with output
./gradlew test --info
```

### Running Examples

```bash
# Chat application (cluster mode)
sh cluster-start.sh chat io.github.seonwkim.example.SpringPekkoApplication 8080 2551 3

# Stop cluster
sh cluster-stop.sh
```

## Coding Standards

### Code Style

We use **Spotless** for code formatting. Please format your code before committing:

```bash
# Check formatting
./gradlew spotlessCheck

# Apply formatting
./gradlew spotlessApply
```

**Style guidelines:**

- Use **Palantir Java Format**
- **4 spaces** for indentation (no tabs)
- Remove unused imports
- Format annotations consistently
- Trim trailing whitespace
- End files with newline

### Code Quality

We use **Error Prone** with **NullAway** for static analysis:

- All public methods should handle `@Nullable` parameters appropriately
- Use `@Nonnull` annotations where applicable
- Fix all Error Prone warnings before submitting

### Naming Conventions

- **Classes**: `PascalCase` (e.g., `SpringActorSystem`)
- **Methods**: `camelCase` (e.g., `getOrSpawn()`)
- **Constants**: `UPPER_SNAKE_CASE` (e.g., `TYPE_KEY`)
- **Packages**: lowercase (e.g., `io.github.seonwkim.actor`)

### Documentation

- Add **JavaDoc** for all public APIs
- Include usage examples in JavaDoc where helpful
- Document non-obvious implementation details
- Keep documentation up-to-date with code changes

## Testing Guidelines

### Test Requirements

- **Unit tests** for all new functionality
- **Integration tests** for actor interactions
- **Test names** should clearly describe what is being tested
- Use **Pekko TestKit** for actor testing

### Test Structure

```java
@Test
void shouldProcessMessageSuccessfully() {
    // Given
    ActorRef<Command> actor = spawn(...);
    
    // When
    actor.tell(new ProcessMessage("test"));
    
    // Then
    assertThat(result).isEqualTo(expected);
}
```

### Test Coverage

- Aim for **>80% code coverage** for core modules
- Test both success and failure scenarios
- Test edge cases and boundary conditions
- Verify error handling and recovery

### Running Tests

```bash
# Run with coverage
./gradlew test jacocoTestReport

# Run specific test
./gradlew test --tests "ClassName.testMethod"
```

## Submitting Changes

### Creating a Pull Request

1. **Create a feature branch**:
   ```bash
   git checkout -b feature/my-awesome-feature
   ```

2. **Make your changes** following coding standards

3. **Add tests** for your changes

4. **Run quality checks**:
   ```bash
   ./gradlew spotlessApply
   ./gradlew build
   ```

5. **Commit your changes** with clear messages:
   ```bash
   git add .
   git commit -m "feat: add support for X"
   ```

6. **Push to your fork**:
   ```bash
   git push origin feature/my-awesome-feature
   ```

7. **Open a Pull Request** on GitHub

### Commit Message Format

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

**Examples:**
```
feat(actor): add support for routers
fix(metrics): correct mailbox size calculation
docs(readme): update installation instructions
test(cluster): add split-brain resolver tests
```

### Pull Request Checklist

Before submitting, ensure:

- [ ] Code follows the project's style guidelines (`./gradlew spotlessApply`)
- [ ] All tests pass (`./gradlew test`)
- [ ] New tests are added for new functionality
- [ ] Documentation is updated if needed
- [ ] Commit messages follow conventional commits format
- [ ] PR description clearly explains the changes
- [ ] Related issue is linked (if applicable)

## Reporting Bugs

### Before Reporting

1. **Check existing issues** to avoid duplicates
2. **Try the latest version** to see if it's already fixed
3. **Gather information** about your environment

### Bug Report Template

Create an issue with:

- **Clear title** describing the problem
- **Steps to reproduce** the issue
- **Expected behavior** vs actual behavior
- **Environment details**: Java version, Spring Boot version, library version
- **Stack traces** or error messages
- **Minimal reproducible example** if possible

## Suggesting Features

### Feature Request Guidelines

1. **Check the roadmap** ([roadmap/ROADMAP.md](roadmap/ROADMAP.md)) first
2. **Search existing issues** for similar requests
3. **Describe the use case** and why it's valuable
4. **Propose an approach** if you have ideas

### Feature Request Template

Include:

- **Problem statement**: What problem does this solve?
- **Proposed solution**: How should it work?
- **Alternatives considered**: Other approaches you've thought about
- **Use cases**: Real-world scenarios where this is needed
- **Implementation ideas**: Technical approach (optional)

## Development Workflow

### Sync with Upstream

Keep your fork up-to-date:

```bash
git checkout main
git fetch upstream
git merge upstream/main
git push origin main
```

### Working on Multiple Features

Create separate branches for each feature:

```bash
git checkout main
git pull upstream main
git checkout -b feature/feature-name
```

## Community

- **Discord**: [Join our server](https://discord.com/channels/1439734161614045205/1439734162100846655) for discussions
- **Issues**: [GitHub Issues](https://github.com/seonwkim/spring-boot-starter-actor/issues) for bugs and features
- **Discussions**: [GitHub Discussions](https://github.com/seonwkim/spring-boot-starter-actor/discussions) for Q&A

## Recognition

Contributors will be recognized in:
- Project README
- Release notes
- NOTICE.md file

## Getting Help

If you need help contributing:

1. Check this guide and other documentation
2. Ask in [GitHub Discussions](https://github.com/seonwkim/spring-boot-starter-actor/discussions)
3. Join our [Discord server](https://discord.com/channels/1439734161614045205/1439734162100846655)
4. Comment on the relevant issue

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0, the same license as the project.

---

Thank you for contributing to spring-boot-starter-actor! 🎉
