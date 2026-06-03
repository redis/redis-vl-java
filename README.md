<div align="center" dir="auto">
    <img width="300" src="https://raw.githubusercontent.com/redis/redis-vl-python/main/docs/_static/Redis_Logo_Red_RGB.svg" style="max-width: 100%" alt="Redis">
    <h1>Vector Library </h1>
</div>

<div align="center" style="margin-top: 20px;">
    <span style="display: block; margin-bottom: 10px;">The AI-native Redis Java client</span>
    <br />

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
![Language](https://img.shields.io/badge/language-Java-orange)
![Java Version](https://img.shields.io/badge/Java-17%2B-blue)
[![Maven Central](https://img.shields.io/maven-central/v/com.redis/redisvl)](https://central.sonatype.com/artifact/com.redis/redisvl)
[![Snapshots](https://img.shields.io/nexus/s/https/s01.oss.sonatype.org/com.redis/redisvl.svg)](https://s01.oss.sonatype.org/content/repositories/snapshots/com/redis/redisvl/)
![GitHub last commit](https://img.shields.io/github/last-commit/redis/redis-vl-java)
[![GitHub stars](https://img.shields.io/github/stars/redis/redis-vl-java)](https://github.com/redis/redis-vl-java/stargazers)

</div>

<div align="center">
<div display="inline-block">
    <a href="https://github.com/redis/redis-vl-java"><b>Home</b></a>&nbsp;&nbsp;&nbsp;
    <a href="https://redis.github.io/redis-vl-java/redisvl/current/"><b>Documentation</b></a>&nbsp;&nbsp;&nbsp;
    <a href="https://github.com/redis-developer/redis-ai-resources"><b>Recipes</b></a>&nbsp;&nbsp;&nbsp;
  </div>
    <br />
</div>

# Introduction

Redis Vector Library (RedisVL) is the ultimate Java client designed for AI-native applications harnessing the power of [Redis](https://redis.io).

RedisVL is your go-to client for:

- Lightning-fast information retrieval & vector similarity search
- Real-time RAG pipelines
- LLM semantic caching
- Hybrid search combining vectors and metadata

# 💪 Getting Started

## Installation

Add RedisVL to your Java (17+) project using Maven or Gradle:

**Maven:**

```xml
<dependency>
    <groupId>com.redis</groupId>
    <artifactId>redisvl</artifactId>
    <version>0.12.2</version>
</dependency>
```

**Gradle:**

```gradle
implementation 'com.redis:redisvl:0.12.2'
```

## Setting up Redis

Choose from multiple Redis deployment options:

1. [Redis Cloud](https://redis.io/try-free): Managed cloud database (free tier available)
2. [Redis on Docker](https://hub.docker.com/_/redis): Docker image for development

    ```bash
    docker run -d --name redis -p 6379:6379 -p 8001:8001 redis:latest
    ```

3. [Redis Enterprise](https://redis.io/enterprise/): Commercial, self-hosted database
4. [Azure Managed Redis](https://azure.microsoft.com/en-us/products/managed-redis): Fully managed Redis Enterprise on Azure

> Enhance your experience and observability with the free [Redis Insight GUI](https://redis.io/insight/).

# Overview

## 🗃️ Redis Index Management

1. **Design a schema** for your use case that models your dataset with built-in Redis and indexable fields (*e.g. text, tags, numerics, geo, and vectors*). Load a schema from a YAML file:

    ```yaml
    index:
      name: user-idx
      prefix: user
      storage_type: json

    fields:
      - name: user
        type: tag
      - name: credit_score
        type: tag
      - name: embedding
        type: vector
        attrs:
          algorithm: flat
          dims: 4
          distance_metric: cosine
          datatype: float32
    ```

    ```java
    import com.redis.vl.schema.IndexSchema;

    IndexSchema schema = IndexSchema.fromYaml("schemas/schema.yaml");
    ```

    Or load directly from a Java Map:

    ```java
    Map<String, Object> schemaMap = Map.of(
        "index", Map.of(
            "name", "user-idx",
            "prefix", "user",
            "storage_type", "json"
        ),
        "fields", List.of(
            Map.of("name", "user", "type", "tag"),
            Map.of("name", "credit_score", "type", "tag"),
            Map.of(
                "name", "embedding",
                "type", "vector",
                "attrs", Map.of(
                    "algorithm", "flat",
                    "datatype", "float32",
                    "dims", 4,
                    "distance_metric", "cosine"
                )
            )
        )
    );

    IndexSchema schema = IndexSchema.fromMap(schemaMap);
    ```

2. **Create a SearchIndex** class with an input schema to perform admin and search operations on your index in Redis:

    ```java
    import redis.clients.jedis.UnifiedJedis;
    import com.redis.vl.index.SearchIndex;

    // Define the index
    UnifiedJedis jedis = new UnifiedJedis("redis://localhost:6379");
    SearchIndex index = new SearchIndex(schema, jedis);

    // Create the index in Redis
    index.create(true); // overwrite if exists
    ```

3. **Load and fetch** data to/from your Redis instance:

    ```java
    Map<String, Object> data = Map.of(
        "user", "john",
        "credit_score", "high",
        "embedding", new float[]{0.23f, 0.49f, -0.18f, 0.95f}
    );

    // load list of maps, specify the "id" field
    index.load(List.of(data), "user");

    // fetch by "id"
    Map<String, Object> john = index.fetch("john");
    ```

## 🔍 Retrieval

Define queries and perform advanced searches over your indices, including the combination of vectors, metadata filters, and more.

- **VectorQuery** - Flexible vector queries with customizable filters enabling semantic search:

    ```java
    import com.redis.vl.query.VectorQuery;

    VectorQuery query = VectorQuery.builder()
        .vector(new float[]{0.16f, -0.34f, 0.98f, 0.23f})
        .field("embedding")
        .numResults(3)
        .build();

    // run the vector search query against the embedding field
    List<Map<String, Object>> results = index.query(query);
    ```

    Incorporate complex metadata filters on your queries:

    ```java
    import com.redis.vl.query.filter.FilterQuery;

    // define a tag match filter
    FilterQuery tagFilter = FilterQuery.tag("user", "john");

    // update query definition
    VectorQuery filteredQuery = query.toBuilder()
        .withPreFilter(tagFilter.build())
        .build();

    // execute query
    List<Map<String, Object>> results = index.query(filteredQuery);
    ```

- **HybridQuery** - Native hybrid search combining text and vector similarity using Redis 8.4+ `FT.HYBRID` command with built-in score fusion:

    ```java
    import com.redis.vl.query.HybridQuery;
    import com.redis.vl.query.Filter;

    // Native hybrid search with LINEAR combination (Redis 8.4+)
    HybridQuery hybridQuery = HybridQuery.builder()
        .text("machine learning algorithms")
        .textFieldName("description")
        .vector(queryVector)
        .vectorFieldName("embedding")
        .filterExpression(Filter.tag("category", "AI"))
        .combinationMethod(HybridQuery.CombinationMethod.LINEAR)
        .linearAlpha(0.3f)  // 30% text, 70% vector
        .numResults(10)
        .build();

    List<Map<String, Object>> results = index.query(hybridQuery);
    // Automatically falls back to AggregateHybridQuery on older Redis versions
    ```

- **AggregateHybridQuery** - Backward-compatible hybrid search using `FT.AGGREGATE` for Redis versions before 8.4:

    ```java
    import com.redis.vl.query.AggregateHybridQuery;

    AggregateHybridQuery aggQuery = AggregateHybridQuery.builder()
        .text("machine learning algorithms")
        .textFieldName("description")
        .vector(queryVector)
        .vectorFieldName("embedding")
        .alpha(0.7f)  // 70% vector, 30% text
        .numResults(10)
        .build();

    List<Map<String, Object>> results = index.query(aggQuery);
    ```

- **VectorRangeQuery** - Vector search within a defined range paired with customizable filters
- **FilterQuery** - Standard search using filters and the full-text search
- **CountQuery** - Count the number of indexed records given attributes

> Read more about building [advanced Redis queries](https://redis.github.io/redis-vl-java/redisvl/current/hybrid-queries.html).

## 🔧 Utilities

### Vectorizers

Integrate with popular embedding providers to greatly simplify the process of vectorizing unstructured data for your index and queries:

- **LangChain4J** - Integration with LangChain4J embedding models
  - OpenAI
  - Cohere
  - HuggingFace
  - Ollama
  - Vertex AI
  - Azure OpenAI
  - Mistral AI
  - Voyage AI

- **ONNX Models** - Local embedding models via ONNX Runtime
  - Sentence Transformers (all-MiniLM-L6-v2, etc.)
  - Custom ONNX models

- **DJL (Deep Java Library)** - Face recognition and embeddings

```java
import com.redis.vl.utils.vectorize.LangChain4JVectorizer;
import dev.langchain4j.model.cohere.CoherEmbeddingModel;

// Using LangChain4J with Cohere
CoherEmbeddingModel cohereModel = CoherEmbeddingModel.withApiKey(apiKey);
LangChain4JVectorizer vectorizer = new LangChain4JVectorizer(cohereModel);

float[] embedding = vectorizer.embed("What is the capital city of France?");

List<float[]> embeddings = vectorizer.embedBatch(
    List.of(
        "my document chunk content",
        "my other document chunk content"
    )
);
```

```java
import com.redis.vl.utils.vectorize.SentenceTransformersVectorizer;

// Using local ONNX model
SentenceTransformersVectorizer vectorizer = new SentenceTransformersVectorizer("sentence-transformers/all-mpnet-base-v2");

float[] embedding = vectorizer.embed("What is the capital city of France?");
```

> Learn more about using [vectorizers](https://redis.github.io/redis-vl-java/redisvl/current/vectorizers.html) in your embedding workflows.

### Rerankers

Integrate with popular reranking providers to improve the relevancy of the initial search results from Redis:

- **Cohere Reranker** - Using Cohere's reranking API
- **HuggingFace Cross-Encoder** - Local reranking with cross-encoder models

```java
import com.redis.vl.utils.rerank.CohereReranker;

CohereReranker reranker = new CohereReranker(apiKey);

List<Map<String, Object>> reranked = reranker.rerank(
    "What is the capital of France?",
    results,
    topK
);
```

## 💫 Extensions

RedisVL supports **Extensions** that implement interfaces exposing best practices and design patterns for working with LLM memory and agents.

### LLM Semantic Caching

Increase application throughput and reduce the cost of using LLM models in production by leveraging previously generated knowledge with the `SemanticCache`.

```java
import com.redis.vl.extensions.cache.llm.SemanticCache;
import com.redis.vl.extensions.cache.llm.CacheConfiguration;

// init cache with TTL and semantic distance threshold
CacheConfiguration config = CacheConfiguration.builder()
    .name("llmcache")
    .ttl(360L)
    .distanceThreshold(0.1)
    .build();

SemanticCache cache = new SemanticCache(config, jedis);

// store user queries and LLM responses in the semantic cache
cache.store(
    "What is the capital city of France?",
    "Paris"
);

// quickly check the cache with a slightly different prompt (before invoking an LLM)
CacheResult result = cache.check("What is France's capital city?");
if (result.isHit()) {
    System.out.println(result.getResponse());
    // Output: Paris
}
```

> Learn more about [semantic caching](https://redis.github.io/redis-vl-java/redisvl/current/llmcache.html) for LLMs.

### LLM Semantic Routing

Build fast decision models that run directly in Redis and route user queries to the nearest "route" or "topic".

```java
import com.redis.vl.extensions.router.SemanticRouter;
import com.redis.vl.extensions.router.Route;
import com.redis.vl.utils.vectorize.SentenceTransformersVectorizer

SentenceTransformersVectorizer vectorizer = new SentenceTransformersVectorizer("sentence-transformers/all-mpnet-base-v2")

List<Route> routes = List.of(
    Route.builder()
        .name("greeting")
        .references(List.of("hello", "hi"))
        .metadata(Map.of("type", "greeting"))
        .distanceThreshold(0.3)
        .build(),
    Route.builder()
        .name("farewell")
        .references(List.of("bye", "goodbye"))
        .metadata(Map.of("type", "farewell"))
        .distanceThreshold(0.3)
        .build()
);

// build semantic router from routes
SemanticRouter router = SemanticRouter.builder()
    .name("topic-router")
    .vectorizer(vectorizer)
    .routes(routes)
    .jedis(jedis)
    .build();

RouteMatch match = router.route("Hi, good morning");
System.out.println(match.getName()); // Output: greeting
System.out.println(match.getDistance()); // Output: 0.273891836405
```

> Learn more about [semantic routing](https://redis.github.io/redis-vl-java/redisvl/current/semantic-router.html).

## 🧪 Experimental: VCR Test System

RedisVL includes an experimental VCR (Video Cassette Recorder) test system for recording and replaying LLM/embedding API calls. This enables:

- **Deterministic tests** - Replay recorded responses for consistent results
- **Cost reduction** - Avoid repeated API calls during test runs
- **Speed improvement** - Local Redis playback is faster than API calls
- **Offline testing** - Run tests without network access or API keys

### Quick Start with JUnit 5

The simplest way to use VCR is with the declarative annotations:

```java
import com.redis.vl.test.vcr.VCRMode;
import com.redis.vl.test.vcr.VCRModel;
import com.redis.vl.test.vcr.VCRTest;

@VCRTest(mode = VCRMode.PLAYBACK_OR_RECORD)
class MyLLMTest {

    // Models are automatically wrapped by VCR
    @VCRModel(modelName = "text-embedding-3-small")
    private EmbeddingModel embeddingModel = createEmbeddingModel();

    @VCRModel
    private ChatLanguageModel chatModel = createChatModel();

    @Test
    void testEmbedding() {
        // First run: Records API response to Redis
        // Subsequent runs: Replays from Redis cassette
        Response<Embedding> response = embeddingModel.embed("What is Redis?");
        assertNotNull(response.content());
    }

    @Test
    void testChat() {
        String response = chatModel.generate("Explain Redis in one sentence.");
        assertNotNull(response);
    }
}
```

### VCR Modes

| Mode | Description | API Key Required |
|------|-------------|------------------|
| `PLAYBACK` | Only use recorded cassettes. Fails if missing. | No |
| `PLAYBACK_OR_RECORD` | Use cassette if available, record if not. | Only for first run |
| `RECORD` | Always call real API and record response. | Yes |
| `OFF` | Bypass VCR, always call real API. | Yes |

### Environment Variable Override

Override the VCR mode at runtime without changing code:

```bash
# Record new cassettes
VCR_MODE=RECORD OPENAI_API_KEY=your-key ./gradlew test

# Playback only (CI/CD, no API key needed)
VCR_MODE=PLAYBACK ./gradlew test
```

### LangChain4J Integration

```java
import com.redis.vl.test.vcr.VCREmbeddingModel;
import com.redis.vl.test.vcr.VCRChatModel;
import com.redis.vl.test.vcr.VCRMode;

// Wrap any LangChain4J EmbeddingModel
VCREmbeddingModel vcrEmbedding = new VCREmbeddingModel(openAiEmbeddingModel);
vcrEmbedding.setMode(VCRMode.PLAYBACK_OR_RECORD);
Response<Embedding> response = vcrEmbedding.embed("What is Redis?");

// Wrap any LangChain4J ChatLanguageModel
VCRChatModel vcrChat = new VCRChatModel(openAiChatModel);
vcrChat.setMode(VCRMode.PLAYBACK_OR_RECORD);
String response = vcrChat.generate("What is Redis?");
```

### Spring AI Integration

```java
import com.redis.vl.test.vcr.VCRSpringAIEmbeddingModel;
import com.redis.vl.test.vcr.VCRSpringAIChatModel;
import com.redis.vl.test.vcr.VCRMode;

// Wrap any Spring AI EmbeddingModel
VCRSpringAIEmbeddingModel vcrEmbedding = new VCRSpringAIEmbeddingModel(openAiEmbeddingModel);
vcrEmbedding.setMode(VCRMode.PLAYBACK_OR_RECORD);
EmbeddingResponse response = vcrEmbedding.embedForResponse(List.of("What is Redis?"));

// Wrap any Spring AI ChatModel
VCRSpringAIChatModel vcrChat = new VCRSpringAIChatModel(openAiChatModel);
vcrChat.setMode(VCRMode.PLAYBACK_OR_RECORD);
String response = vcrChat.call("What is Redis?");
```

### How It Works

1. **Container Management**: VCR starts a Redis container with persistence
2. **Model Wrapping**: `@VCRModel` fields are wrapped with VCR proxies
3. **Cassette Storage**: Responses stored as Redis JSON documents
4. **Persistence**: Data saved to `src/test/resources/vcr-data/` via Redis AOF/RDB
5. **Playback**: Subsequent runs load cassettes from persistent storage

### Demo Projects

Complete working examples are available:

- **[LangChain4J VCR Demo](demos/langchain4j-vcr/)** - LangChain4J embedding and chat models
- **[Spring AI VCR Demo](demos/spring-ai-vcr/)** - Spring AI embedding and chat models

Run the demos without an API key (uses pre-recorded cassettes):

```bash
./gradlew :demos:langchain4j-vcr:test
./gradlew :demos:spring-ai-vcr:test
```

> Learn more about [VCR testing](https://redis.github.io/redis-vl-java/redisvl/current/vcr-testing.html).

## 🚀 Why RedisVL?

In the age of GenAI, **vector databases** and **LLMs** are transforming information retrieval systems. With emerging and popular frameworks like [LangChain4J](https://github.com/langchain4j/langchain4j) and [Spring AI](https://spring.io/projects/spring-ai), innovation is rapid. Yet, many organizations face the challenge of delivering AI solutions **quickly** and at **scale**.

Enter [Redis](https://redis.io) – a cornerstone of the NoSQL world, renowned for its versatile [data structures](https://redis.io/docs/data-types/) and [processing engines](https://redis.io/docs/interact/). Redis excels in real-time workloads like caching, session management, and search. It's also a powerhouse as a vector database for RAG, an LLM cache, and a chat session memory store for conversational AI.

The Redis Vector Library bridges the gap between the AI-native developer ecosystem and Redis's robust capabilities. With a lightweight, elegant, and intuitive interface, RedisVL makes it easy to leverage Redis's power. Built on the [Jedis](https://github.com/redis/jedis) client, RedisVL transforms Redis's features into a grammar perfectly aligned with the needs of today's AI/ML Engineers and Data Scientists.

RedisVL is more than a low-level client wrapper — it's an SDK plus a set of patterns, examples, and docs for AI-native Redis usage. At a high level it helps Java teams:

- Define Redis search/index schemas in YAML or Java
- Create and manage Redis-backed search indices
- Store documents, metadata, and embeddings in Redis
- Run vector, text, filter, and hybrid queries
- Integrate Redis into LangChain4J-based RAG systems
- Add semantic cache, message history, reranking, and routing patterns

## 🧱 Core Use Cases

RedisVL is positioned around these primary use cases:

- Vector similarity search
- Hybrid text + vector search
- RAG retrieval backends
- LLM semantic caching
- Embedding generation workflows
- Reranking search results
- Semantic routing
- LLM chat/message history persistence

In practice, adoption tends to fall into three buckets, which maps to how you should pick APIs:

| You are… | Use… |
|----------|------|
| Building a Java vector-search layer directly on Redis | The basic `schema` / `index` / `query` APIs |
| Building a RAG assistant or retrieval chain with LangChain4J | The `com.redis.vl.langchain4j` adapters |
| Looking for reusable AI infrastructure patterns | The `extensions` (cache, router, message history) |

The main entry point is **`SearchIndex`**, which holds the schema and Redis connection behavior and exposes index lifecycle and query operations. Treat the demos and notebooks as reference implementations, not production architecture templates.

## 🗂️ Repository Structure

This is a multi-module Gradle (Kotlin DSL) project:

| Module | Description |
|--------|-------------|
| **`core/`** | The main product code, published as the `com.redis:redisvl` Maven artifact. This is the customer-facing deliverable. |
| **`docs/`** | A dedicated [Antora](https://antora.org/) documentation site (not just a README supplement), including generated Javadocs. Treated as a maintained, first-class artifact and part of the delivery pipeline. |
| **`demos/`** | Sample applications: `rag-multimodal` (JavaFX multimodal RAG demo), `langchain4j-vcr`, and `spring-ai-vcr` (record/replay testing demos). Important for adoption and validation, but not shipped artifacts. |
| **`notebooks/`** | Jupyter notebooks for interactive walkthroughs — enablement and onboarding material for developers evaluating the library. |

Key package areas inside `core` (`com.redis.vl.*`):

- `index` — index creation, loading, querying, and fetch operations
- `schema` — schema and field definitions
- `query` — vector, text, count, aggregation, and hybrid query objects
- `storage` — Redis Hash and Redis JSON storage behavior
- `langchain4j` — LangChain4J adapters for embedding store, retriever, chat memory, and document store
- `extensions.cache` — semantic cache and embeddings cache
- `extensions.router` — semantic router
- `extensions.messagehistory` — message history and semantic message history
- `utils.vectorize` — vectorizer integrations for LangChain4J and local ONNX models
- `utils.rerank` — reranking utilities
- `test.vcr` — VCR-style record/replay support for LLM-related tests

## ⚙️ Dependency & Runtime Model

- Java **source/target compatibility is 17**; the Gradle toolchain builds with Java **21**.
- **Jedis** is the underlying — and public — Redis client dependency. RedisVL layers higher-level abstractions on top of it.
- **LangChain4J** integrations are mostly optional, compile-time integrations (`compileOnly`) — include only the providers you need.
- **ONNX Runtime** is bundled for local embedding and reranking support.
- **Spring AI** support is present mainly for VCR testing compatibility.

This means you can use RedisVL in a basic Redis/vector-search mode without adopting every AI integration, while still being able to opt into richer AI-framework integrations when needed.

## 📚 Examples and Notebooks

Check out the [notebooks](notebooks/) directory for interactive Jupyter notebook examples:

- [Getting Started](notebooks/01_getting_started.ipynb) - Introduction to RedisVL basics
- [Hybrid Queries](notebooks/02_hybrid_queries.ipynb) - Combining vector and metadata search
- [Advanced Queries](notebooks/11_advanced_queries.ipynb) - TextQuery, HybridQuery, and MultiVectorQuery
- [LLM Cache](notebooks/03_llmcache.ipynb) - Semantic caching for LLMs
- [Hash vs JSON Storage](notebooks/05_hash_vs_json.ipynb) - Storage type comparison
- [Vectorizers](notebooks/06_vectorizers.ipynb) - Working with embedding models
- [Rerankers](notebooks/07_rerankers.ipynb) - Improving search relevance

### Running the Notebooks

The notebooks use Java kernel support via JJava. To run them:

1. **Start the notebook environment:**

   ```bash
   cd notebooks
   docker compose up -d
   ```

2. **Access Jupyter Lab:**
   Navigate to [http://localhost:8888/](http://localhost:8888/)

3. **Stop when done:**

   ```bash
   docker compose down
   ```

> See the [notebooks README](notebooks/README.md) for more details.

## 😁 Helpful Links

For additional help, check out the following resources:

- [Getting Started Guide](https://redis.github.io/redis-vl-java/redisvl/current/getting-started.html)
- [API Reference (Javadoc)](https://redis.github.io/redis-vl-java/redisvl/current/_attachments/javadoc/aggregate/index.html)
- [Example Notebooks](notebooks/)
- [Redis AI Recipes](https://github.com/redis-developer/redis-ai-resources)
- [Official Redis Vector API Docs](https://redis.io/docs/interact/search-and-query/advanced-concepts/vectors/)

## 🫱🏼‍🫲🏽 Contributing

Please help us by contributing PRs, opening GitHub issues for bugs or new feature ideas, improving documentation, or increasing test coverage. [Read more about how to contribute!](CONTRIBUTING.md)

## Requirements

- Java 17+
- Redis 8.0+ (includes built-in search and vector capabilities)
- For native `FT.HYBRID` support: Redis 8.4+

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🚧 Maintenance

This project is supported by [Redis, Inc](https://redis.io) on a good faith effort basis. To report bugs, request features, or receive assistance, please [file an issue](https://github.com/redis/redis-vl-java/issues).
