# Contributing

## Bugs, questions and proposals

Use [GitHub Issues](https://github.com/seonWKim/actor-spring-boot-starter/issues) for all of them:

- **Bug**: what you did, what you expected, what happened, and the versions (starter, Spring Boot, Java).
  A small project or test that reproduces it helps most.
- **Feature or API change**: the problem first (what you're trying to do and why the current API makes it
  hard), then the change you'd like. Discuss it in the issue before opening a large PR.
- **Question**: [Discussions](https://github.com/seonWKim/actor-spring-boot-starter/discussions) or
  [Discord](https://discord.com/channels/1439734161614045205/1439734162100846655) work too.

Search [existing issues](https://github.com/seonWKim/actor-spring-boot-starter/issues?q=is%3Aissue) first;
add to one that already covers your case.

## Pull requests

1. Open or pick an issue, and say in it that you're working on it.
2. Branch from `main`, and run `./gradlew spotlessApply build` before pushing.
3. Link the issue in the PR (`Closes #123`).

[ARCHITECTURE.md](https://github.com/seonWKim/actor-spring-boot-starter/blob/main/ARCHITECTURE.md) explains
the modules, and [AGENTS.md](https://github.com/seonWKim/actor-spring-boot-starter/blob/main/AGENTS.md) the
testing rules.

## More

- [Spring conventions](spring-conventions.md): how the starter compares with other Spring projects
