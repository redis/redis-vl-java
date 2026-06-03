# Contributing to RedisVL Java

Thank you for your interest in contributing to the Redis Vector Library for Java (RedisVL)! We welcome contributions from the community — bug fixes, new features, documentation improvements, increased test coverage, and demo additions are all appreciated.

## Table of Contents

- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Project Architecture](#project-architecture)
- [Development Workflow](#development-workflow)
- [Testing](#testing)
- [Code Style](#code-style)
- [Adding a Demo](#adding-a-demo)
- [Submitting Changes](#submitting-changes)
- [Reporting Issues](#reporting-issues)
- [Additional Resources](#additional-resources)
- [Getting Help](#getting-help)
- [License](#license)

## Getting Started

Before contributing, please:

1. Read the [README.md](README.md) to understand the project and its public API.
2. Check existing [issues](https://github.com/redis/redis-vl-java/issues) and [pull requests](https://github.com/redis/redis-vl-java/pulls) to avoid duplicate work.
3. For significant changes, open an issue first to discuss your approach.

## Development Setup

### Prerequisites

- **JDK 21** — the build uses a Gradle toolchain that compiles with JDK 21 while targeting Java 17 bytecode. (CI uses the Zulu distribution.)
- **Docker** — required to run the test suite (Testcontainers spins up Redis Stack).
- **Gradle wrapper** — no global Gradle install needed; always use `./gradlew`.

### Clone and Build

```bash
git clone https://github.com/redis/redis-vl-java.git
cd redis-vl-java
./gradlew build
```

### Start Redis

Most tests use [Testcontainers](https://testcontainers.com/) and start Redis Stack automatically — you just need Docker running. To run a Redis instance manually (for demos, notebooks, or ad-hoc testing):

```bash
docker run -d --name redis -p 6379:6379 -p 8001:8001 redis:latest
```

RedisVL requires **Redis 8.0+** (built-in search and vector capabilities). Native `FT.HYBRID` support requires **Redis 8.4+**.

## Project Architecture

This is a multi-module Gradle (Kotlin DSL) project:

```
redis-vl-java/
├── core/          # The main library, published as com.redis:redisvl (the deliverable)
├── docs/          # Antora documentation site + generated Javadocs
├── demos/         # Runnable example applications
│   ├── rag-multimodal/    # JavaFX multimodal RAG demo
│   ├── langchain4j-vcr/   # Record/replay testing with LangChain4J
│   └── spring-ai-vcr/     # Record/replay testing with Spring AI
└── notebooks/     # Jupyter notebooks for interactive walkthroughs
```

### Core Library

Key packages inside `core/src/main/java/com/redis/vl/`:

- **`index/`** — index creation, loading, querying, and fetch operations (`SearchIndex` is the main entry point)
- **`schema/`** — schema and field definitions
- **`query/`** — vector, text, count, aggregation, and hybrid query objects
- **`storage/`** — Redis Hash and Redis JSON storage behavior
- **`langchain4j/`** — LangChain4J adapters (embedding store, retriever, chat memory, document store)
- **`extensions/cache/`** — semantic cache and embeddings cache
- **`extensions/router/`** — semantic router
- **`extensions/messagehistory/`** — message history and semantic message history
- **`utils/vectorize/`** — vectorizer integrations (LangChain4J and local ONNX models)
- **`utils/rerank/`** — reranking utilities
- **`test/vcr/`** — VCR-style record/replay support for LLM-related tests

The library is built on [Jedis](https://github.com/redis/jedis) (the public Redis client dependency). LangChain4J and Spring AI integrations are optional compile-time dependencies — consumers include only what they need.

## Development Workflow

### 1. Create a Branch

```bash
git checkout -b fix/short-description
# or
git checkout -b feat/short-description
```

### 2. Make Your Changes

Edit source files under `core/src/`. Build the module:

```bash
./gradlew :core:build
```

The build is strict: compilation uses `-Xlint:all -Werror`, so warnings fail the build.

### 3. Format Code

Apply the project's code style (Spotless + google-java-format) before committing:

```bash
./gradlew spotlessApply
```

To check without modifying files:

```bash
./gradlew spotlessCheck
```

### 4. Run Tests

```bash
# Default suite (requires Docker; excludes slow + integration tests)
./gradlew :core:test

# A specific test class
./gradlew :core:test --tests "com.redis.vl.index.SearchIndexTest"

# Tests with verbose output
./gradlew :core:test --info
```

### 5. Common Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Compile, run code-style check, tests, and SpotBugs across modules |
| `./gradlew :core:test` | Run the default core test suite (excludes `slow`/`integration`) |
| `./gradlew :core:integrationTest` | Run tests tagged `integration` |
| `./gradlew :core:slowTest` | Run tests tagged `slow` |
| `./gradlew spotlessApply` | Auto-format all source files |
| `./gradlew spotlessCheck` | Check formatting without modifying (what CI runs) |
| `./gradlew :core:jacocoTestReport` | Generate a coverage report |
| `./gradlew publishToMavenLocal` | Publish a snapshot to `~/.m2` for local testing |

## Testing

The test stack uses **JUnit 5**, **AssertJ**, **Mockito**, and **Testcontainers** (with `testcontainers-redis`). Make sure Docker is running before invoking the tests.

### Running Tests

```bash
# Default suite (unit + Testcontainers; excludes slow + integration)
./gradlew :core:test

# One test class
./gradlew :core:test --tests "*.VectorQueryTest"

# Tagged suites
./gradlew :core:integrationTest
./gradlew :core:slowTest
```

> **Note:** the default `test` task **excludes** tests tagged `slow` and `integration` (configured in `core/build.gradle.kts`). The PR build does not cover every behavior path — run `integrationTest`/`slowTest` explicitly when your change touches those areas.

### Writing Tests

New features and bug fixes **must** include tests. Place test classes under:

```
core/src/test/java/com/redis/vl/
```

Guidelines:

- Use JUnit 5 + AssertJ assertions; use Mockito for collaborators.
- Use Testcontainers for tests that require a live Redis — don't assume a locally running instance.
- Name test methods clearly (a descriptive name or `givenX_whenY_thenZ`).
- Test both the happy path and edge cases (empty results, null fields, large payloads).
- Tag long-running tests `@Tag("slow")` and live-service/integration tests `@Tag("integration")` so they're excluded from the default build.

### VCR Testing (AI model calls)

Tests that exercise embedding/chat models use a VCR (record/replay) mechanism so they can run without live API keys. Control the mode with the `VCR_MODE` environment variable:

```bash
# Replay recorded cassettes only — no API key needed (this is what CI uses)
VCR_MODE=PLAYBACK ./gradlew test

# Record new cassettes (requires a real API key)
VCR_MODE=RECORD OPENAI_API_KEY=your-key ./gradlew test
```

Cassettes are stored under `core/src/test/resources/vcr-data/`. If you add a test that calls an external model, record its cassette and commit it so CI can replay it. See the [VCR testing docs](https://redis.github.io/redis-vl-java/redisvl/current/vcr-testing.html). Note that VCR is currently labeled **experimental**.

### Test Coverage

The project is configured with [JaCoCo](https://www.jacoco.org/), including an 80% coverage verification rule in the root build. Generate a report locally with:

```bash
./gradlew :core:jacocoTestReport
# HTML report: core/build/reports/jacoco/test/html/index.html
```

New public APIs should have corresponding tests.

## Code Style

This project uses the **Spotless** Gradle plugin with [google-java-format](https://github.com/google/google-java-format). Key conventions (enforced automatically by the formatter):

- **2-space indentation**
- **100-character line length**
- **No wildcard imports**; imports are sorted and unused imports are removed
- Trailing whitespace trimmed; files end with a newline

Always run `./gradlew spotlessApply` before pushing — CI runs `spotlessCheck` and will fail on formatting violations.

Additional guidelines:

- The build compiles with `-Xlint:all -Werror`; fix warnings rather than suppressing them.
- Add Javadoc to all public types and methods — at minimum a one-line summary (the build runs doclint).
- [SpotBugs](https://spotbugs.github.io/) runs on main code (exclusions in `spotbugs-exclude.xml`) and is disabled for test code.
- Avoid raw types and unchecked casts; use `@SuppressWarnings` only when genuinely necessary and add a comment explaining why.
- Keep the **public API stable** — this is a published library; flag breaking signature changes rather than making them silently.

## Adding a Demo

Demos live in the `demos/` directory as independent modules.

1. Create a new subdirectory: `demos/<your-demo>/`.
2. Add a `build.gradle.kts` — copy an existing one (e.g., `demos/langchain4j-vcr/build.gradle.kts`) as a starting point.
3. Register it in the root `settings.gradle.kts`:
   ```kotlin
   include("demos:<your-demo>")
   project(":demos:<your-demo>").projectDir = file("demos/<your-demo>")
   ```
4. Add a `README.md` inside the demo directory describing what it demonstrates, prerequisites (env vars, data files), and how to run it.
5. Demos are not held to the same strict build standards as `core` — but keep them runnable and documented.

## Submitting Changes

### Pull Request Checklist

- [ ] Branch is based on `main`
- [ ] All tests pass: `./gradlew :core:test`
- [ ] Code is formatted: `./gradlew spotlessApply`
- [ ] New/changed public APIs have Javadoc
- [ ] Tests added for new behavior (with appropriate `@Tag`s)
- [ ] VCR cassettes committed for any new external-model tests

### Pull Request Description

- Use a clear, descriptive title referencing the issue if applicable (e.g., `fix(query): preserve maxDistance precision in VECTOR_RANGE query (#123)`).
- Describe **what** changed and **why**.
- Include before/after examples for API changes.

### Commit Messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) style:

```
<type>(<optional scope>): <short summary>

[optional body]
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

Examples:
```
feat(query): add MultiVectorQuery support
fix(index): preserve maxDistance precision in VECTOR_RANGE query
docs: document count(), ChatRole, and MultiVectorQuery
test: add integration tests for message history
refactor(messagehistory): deduplicate count() into base class
```

Keep the first line under 72 characters. Reference issues with `(#123)` at the end. Commit messages feed the release changelog (via JReleaser), so keep them clean.

## Reporting Issues

### Bug Reports

Please include:

1. **Environment**: Java version, RedisVL version, Redis server version, OS
2. **Minimal reproducible example** — the smaller, the better
3. **Expected vs. actual behavior**
4. **Full stack trace**

### Feature Requests

Describe:

- The use case you're solving
- How you envision the API looking (code example)
- Any alternatives you considered

## Additional Resources

- [RedisVL Java Documentation](https://redis.github.io/redis-vl-java/redisvl/current/)
- [API Reference (Javadoc)](https://redis.github.io/redis-vl-java/redisvl/current/_attachments/javadoc/aggregate/index.html)
- [Redis Vector / Search Documentation](https://redis.io/docs/latest/develop/interact/search-and-query/)
- [Jedis](https://github.com/redis/jedis)
- [LangChain4J](https://github.com/langchain4j/langchain4j)
- [Testcontainers](https://testcontainers.com/)
- [Redis AI Recipes](https://github.com/redis-developer/redis-ai-resources)

## Getting Help

- **GitHub Issues**: [redis/redis-vl-java/issues](https://github.com/redis/redis-vl-java/issues)
- **Discord**: [Redis Discord Server](https://discord.gg/redis)

## License

By contributing to RedisVL Java you agree that your contributions will be licensed under the [MIT License](https://opensource.org/licenses/MIT).

Thank you for helping make RedisVL Java better for everyone!
