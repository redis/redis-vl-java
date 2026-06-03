# RedisVL Java Agent Guide

**Last updated**: 2026-06-03

Guidance for AI coding agents (Claude Code, Cursor, Copilot, etc.) working in the
**redis-vl-java** repository. Humans should also read [CONTRIBUTING.md](CONTRIBUTING.md);
this file gives agents the fast path to building, testing, and conforming to project
conventions.

## Project Overview

RedisVL Java is a Gradle multi-module Java library — the AI-native Redis vector library —
published to Maven Central as `com.redis:redisvl` and consumed as a dependency by Java
applications. It is not a deployable service. It is closer to an SDK plus patterns,
examples, and docs for AI-native Redis usage than a thin client wrapper.

Built on [Jedis](https://github.com/redis/jedis), it provides index schemas, vector /
hybrid / text queries, storage, vectorizers, rerankers, and extensions (LLM cache, message
history, semantic router, summarization). Public API lives under `com.redis.vl.*`.

## What the Library Does

Major capabilities:

- Define Redis search/index schemas in YAML or Java
- Create and manage Redis-backed search indices (`SearchIndex`)
- Store documents, metadata, and embeddings (Redis Hash or Redis JSON)
- Run vector, text, filter, hybrid, count, and aggregation queries
- LangChain4J adapters for embedding store, retriever, chat memory, and document store
- LLM semantic caching and embeddings cache
- Semantic routing
- LLM chat / message history persistence
- Vectorizers (LangChain4J providers + local ONNX models) and rerankers
- VCR-style record/replay testing for deterministic LLM/embedding flows

## How Customers Use It

1. Run Redis 8+ or Redis Stack (search + vector capabilities required).
2. Add `com.redis:redisvl` to a Java 17+ application; connect to Redis via Jedis.
3. Define an `IndexSchema` (YAML or Java) and create a `SearchIndex`.
4. Load data and embeddings into Redis.
5. Query with `VectorQuery`, `HybridQuery`, `FilterQuery`, and related types.

Adoption tends to fall into three buckets: teams that want a Java vector-search library on
Redis (basic `schema`/`index`/`query` APIs); teams building RAG/chat apps with LangChain4J
(the `langchain4j` adapters); and teams wanting reusable AI infra patterns (the
`extensions`).

## Supported Redis Targets

- **Redis 8.0+** (built-in search and vector capabilities)
- **Redis 8.4+** for native `FT.HYBRID` support
- Redis Stack, Redis Enterprise, Redis Cloud

Plain Redis OSS without the search/vector capabilities does not support the full feature
set.

## Source of Truth

Read in this order:

1. The active feature spec in `specs/<slug>/` (local-only — see Spec Workflow)
2. `README.md` — user-facing project overview and public API examples
3. The Antora docs under `docs/`
4. This file

## Before Coding

1. If a feature spec exists, read its spec, plan, and tasks.
2. Read `README.md` for the public API surface and user expectations.
3. Look at neighboring code before introducing new patterns.

Do not implement behavior changes that contradict an approved spec or existing
conventions.

## Module Map

| Module | Purpose |
|---|---|
| `core/` | Core library — schema, index, query, storage, extensions, vectorizers, rerankers, VCR. Published as `com.redis:redisvl`. **Tests live here** in `core/src/test/`. |
| `docs/` | Antora documentation site + Javadoc generation |
| `demos/` | Sample applications (`rag-multimodal`, `langchain4j-vcr`, `spring-ai-vcr`) |
| `notebooks/` | Jupyter notebooks for interactive walkthroughs |
| `specs/` | Feature specs, one folder per feature (gitignored / local-only) |

Key config files: `gradle.properties`, `build.gradle.kts`, `core/build.gradle.kts`,
`settings.gradle.kts`, `jreleaser.yml`.

## Best Demos to Run First

```bash
./gradlew :demos:langchain4j-vcr:test    # Record/replay testing with LangChain4J
./gradlew :demos:spring-ai-vcr:test      # Record/replay testing with Spring AI
```

The VCR demos run with no API key (pre-recorded cassettes). `demos:rag-multimodal` is a
JavaFX multimodal RAG demo.

## New Feature Gate

For non-trivial features, work from an approved spec. A feature spec lives in
`specs/<slug>/` and consists of:

1. `specs/<slug>/spec.md` — requirements and acceptance scenarios
2. `specs/<slug>/plan.md` — implementation design
3. `specs/<slug>/tasks.md` — execution checklist

Prefer not to write implementation code for a sizeable feature until the spec is agreed.
Bug fixes and small changes don't require a spec.

Branch names must be **under 50 characters**: a short type prefix, a `/`, then the slug.

```
feat/848-agents-contributing       ✓
fix/maxdistance-precision          ✓
docs/document-count-chatrole       ✓
chore/upgrade-jedis                ✓
feat/add-a-very-long-description-that-keeps-going   ✗  too long
```

Common prefixes: `feat/`, `fix/`, `docs/`, `chore/`, `refactor/`, `test/`.

## Working Rules

- Keep changes focused and reviewable; do not refactor unrelated code.
- Add or update tests when behavior changes. **Tests go in `core/src/test/java/com/redis/vl/`** (this repo colocates tests inside `core` — there is no separate `tests/` module).
- Tag long-running tests `@Tag("slow")` and live-service tests `@Tag("integration")` so they stay out of the default build.
- Do not add dependencies without justification; LangChain4J/Spring AI providers are `compileOnly` (optional) — keep them optional.
- Do not hard-code environment-specific values.
- Do not weaken, skip, or delete tests to force a change through.
- Keep the **public API stable** — this is a published library (`com.redis:redisvl`). Flag breaking signature changes rather than making them silently.
- Add Javadoc to public types and methods (the build runs doclint).
- Update docs under `docs/` when changing user-visible behavior.
- **This repo has no Maven wrapper.** Use `./gradlew` only.
- Do not commit, push, or open PRs unless explicitly asked.

## Known Inconsistencies to Avoid Repeating

- **Java version**: source/target compatibility is **Java 17** and consumers need Java 17+, but the Gradle toolchain and CI build with **JDK 21**. Build with JDK 21; do not use APIs newer than Java 17.
- **VCR is experimental** (so labeled in the README) — treat its surface as unstable unless told otherwise.
- Demo modules are intentionally held to **relaxed** build standards (the root build skips strict rules for `rag-` demos). Don't copy demo-grade patterns into `core`.
- Feature maturity varies; docs still mention planned gaps in advanced topics.

## When To Ask

Ask for clarification when:

- a requirement is ambiguous on a critical point
- a decision affects scope, security, or public API / user experience
- multiple valid approaches have different architectural consequences
- the proposed change conflicts with the spec or existing conventions

## Verification

Run before every commit or push that touches source code:

```bash
./gradlew spotlessApply              # fix formatting
./gradlew spotlessCheck build -S     # must be fully green
```

Do not commit or push if tests are failing or formatting is dirty. If verification cannot
run (e.g., no Docker available for Testcontainers), say so explicitly — do not skip
silently.

## Build Essentials

```bash
# Fix formatting (ALWAYS before finishing)
./gradlew spotlessApply

# Full build + tests + SpotBugs
./gradlew build -S

# Run a specific test class
./gradlew :core:test --tests "com.redis.vl.<Pkg>.<ClassName>"

# Tagged suites (excluded from the default build)
./gradlew :core:integrationTest
./gradlew :core:slowTest

# Replay AI-model tests without an API key
VCR_MODE=PLAYBACK ./gradlew test

# Build without tests
./gradlew assemble -S
```

The build is strict: `-Xlint:all -Werror`, plus Spotless (google-java-format), SpotBugs
(main code only), and a JaCoCo 80% coverage rule.

## Current Technical Baseline

- Language: **Java 17** source/target; **JDK 21** toolchain (`build.gradle.kts`)
- Redis client: **Jedis 7.3.0** (public `api` dependency)
- AI integrations (optional, `compileOnly`): **LangChain4J 0.36.2**, **Spring AI 1.1.0**
- Local models: **ONNX Runtime 1.16.3**, DJL HuggingFace tokenizers 0.30.0
- Build: Gradle (Kotlin DSL) — wrapper version and library versions in build files / `gradle.properties`
- Current version: see `gradle.properties`

## Test Strategy

- Unit tests, Testcontainers-backed Redis tests, integration-style tests, slow tests, and
  VCR-based deterministic tests for LLM/embedding flows.
- **All tests live in `core/src/test/`** (colocated with the library, not a separate
  module).
- The default `:core:test` task **excludes** `slow` and `integration` tags; dedicated
  `integrationTest` and `slowTest` Gradle tasks run them. The PR build therefore does not
  exercise every path — run the tagged suites when relevant.
- VCR cassettes live in `core/src/test/resources/vcr-data/`; `VCR_MODE=PLAYBACK` replays
  them with no API key. Use `VCR_MODE=RECORD` (with a real key) only when you intentionally
  need new cassettes, and commit the result.

## Documentation System

Built with [Antora](https://antora.org/). Source lives under `docs/`.

- Javadocs are generated during the docs build (`aggregateJavadoc`) and included in the
  Antora site.
- Published to GitHub Pages via `.github/workflows/docs.yml`.
- Docs are a first-class deliverable — update them when changing user-visible behavior.

## CI Workflows

| File | Trigger | Purpose |
|---|---|---|
| `.github/workflows/build.yml` | Pull request | `spotlessCheck` + `build` (JDK 21); uploads test reports on failure |
| `.github/workflows/early-access.yml` | Push to `main` | Snapshot publish via JReleaser (appends `-SNAPSHOT` unless already prerelease/tagged) |
| `.github/workflows/release.yml` | Manual `workflow_dispatch` | Official release: bumps `gradle.properties`, publishes to Maven Central, triggers docs |
| `.github/workflows/docs.yml` | Manual / dispatch | Docs site publish to GitHub Pages |

Releasing is operator-driven via `workflow_dispatch` and depends on secrets (GitHub token,
GPG signing keys, Maven Central/Sonatype credentials) — **do not bump the
`gradle.properties` version or trigger releases unless explicitly asked.**

## Spec Workflow

Use `specs/` for feature work. Each feature gets its own folder, named after the tracker
slug (or a plain slug if none):

```text
specs/848-add-agents-contributing/   # Jira/GH ticket slug
specs/maxdistance-precision/          # no tracker slug
└── each folder contains: spec.md, plan.md, tasks.md
```

The slug is the branch name minus the `feat/` or `fix/` prefix. `specs/` is **gitignored**
(local-only working material), so spec files are not committed.

## Important Links

- GitHub: https://github.com/redis/redis-vl-java
- Docs: https://redis.github.io/redis-vl-java/redisvl/current/
- Maven Central: https://central.sonatype.com/artifact/com.redis/redisvl
- Issues: https://github.com/redis/redis-vl-java/issues

## Recent Changes

<!-- Append a one-liner per merged spec/feature as work lands -->
<!-- Format: - <slug>: what shipped -->
- add-agents-contributing: added AGENTS.md and CONTRIBUTING.md; documented architecture, build, test, and release model
