# Agent instructions

Rules for anyone, human or agent, changing this repository. `CLAUDE.md` is a symlink to this file.

## Read first

- [`ARCHITECTURE.md`](ARCHITECTURE.md): the modules, the layers, and the rule that no Pekko type appears in a public signature. Follow it. If a change has to depart from it, update it in the same change and say why.

## Testing rules

A test earns its place by failing when a real bug is introduced. Before you add a test, ask: **"Which plausible bug in our code makes this test fail?"** If you can't name one, don't write the test.

### Meaningless tests: never write these

| Kind | Example | Why it's meaningless |
|---|---|---|
| **Can't fail** | No assertion; `assertNotNull(new Foo())`; `assertTrue(true)`; catching an exception and passing | No bug makes it fail |
| **Tests the framework, not us** | Spring injects a bean; Jackson round-trips a plain record; Pekko delivers a `tell`; a record's `equals` works | It checks a library we didn't write. Test our use of it (our configuration, our mapping), not the library |
| **Restates the implementation** | Every collaborator mocked, then `verify(...)` that the same calls happened in the same order as the code | It breaks on every refactor and passes on every behavior bug |
| **Trivial code** | Getters, setters, constructors, builders that only store fields, `toString` | No logic, so no bug to catch |
| **Duplicate** | A second test of the same behavior at the same level with different literals | Adds run time and maintenance, not coverage. Use a parameterized test when the inputs really differ in kind |
| **Coverage filler** | A test written to raise a coverage number, with nothing said about behavior | Coverage is not the goal |
| **Unspecified snapshot** | Asserting a large serialized output or log text verbatim when that format isn't a documented contract | It fails on harmless changes and says nothing about what matters |

### What a good test looks like

- **It checks behavior or a contract** a user or another module relies on: an API's result, an emitted event, a meter's value, a config property's effect, a failure mode, an architecture rule.
- **Its name states the behavior:** `entityIsRecreatedOnAnotherNodeAfterCrash`, not `testSharding2`.
- **It has one reason to fail.** Its assertions are about that one behavior.
- **It's at the cheapest level that can catch the bug.** Use a unit test for logic, a Spring context test for wiring and properties, and a multi-node test only for cluster behavior.
- **It's deterministic:**
  - Wait for a condition with Awaitility, never `Thread.sleep`.
  - Use fixed seeds and a fake or controllable clock.
  - Use free ports, never fixed ones.
  - Each cluster test owns its actor system and shuts it down.
- **External services:** Testcontainers for Redis and databases, and the test is skipped (not failed) when Docker isn't available. Use H2 when SQL dialect doesn't matter.
- **Every SPI has one contract test suite** that each implementation runs: built-in stores, the in-memory fake, and users' own.

### When you change tests

- **Delete** a meaningless test you come across in code you touch, and say so in the change.
- **Fix a flaky test at its cause**, usually a sleep, a fixed port or shared state. Don't retry it or add `@Disabled`. If it can't be fixed in the change, file an issue and name it there.
- **A bug fix starts with a test that fails without the fix.**

## Build and check

```bash
./gradlew build          # compiles, formats (spotless), runs every test
./gradlew spotlessApply  # fix formatting
```

The build must pass before a change is done.
