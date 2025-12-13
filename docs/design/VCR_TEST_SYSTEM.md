# VCR Test System Design for RedisVL Java

## Overview

This document outlines a design for implementing a VCR (Video Cassette Recorder) test system for JUnit 5 (and potentially TestNG) that enables recording and replaying LLM API calls and embedding computations during test execution.

The design is inspired by the Python implementation in `maestro-langgraph` and adapted for Java idioms, leveraging RedisVL's existing `EmbeddingsCache` infrastructure.

## Goals

1. **Zero API Costs in CI** - Tests run without LLM API calls after initial recording
2. **Deterministic Tests** - Same responses every run for reproducibility
3. **Fast Execution** - No network latency (cached responses)
4. **Transparent Integration** - Minimal test code changes via annotations
5. **Redis-Native Storage** - Leverage Redis AOF/RDB for persistent cassettes
6. **Framework Agnostic Core** - Support JUnit 5 primarily, TestNG optionally

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Test Execution                           │
├─────────────────────────────────────────────────────────────────┤
│  @VCRTest(mode = PLAYBACK)                                      │
│  class MyIntegrationTest {                                      │
│      @Test void testRAGQuery() { ... }                          │
│  }                                                              │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              VCRExtension (JUnit 5 Extension)                   │
│  • BeforeAllCallback: Start Redis, load cassettes               │
│  • BeforeEachCallback: Set test context, reset counters         │
│  • AfterEachCallback: Register test result, persist cassettes   │
│  • AfterAllCallback: BGSAVE, stop Redis                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                   VCR Interceptor Layer                         │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │ LLMInterceptor  │  │EmbeddingIntercep│  │ RedisVCRStore   │  │
│  │ (ByteBuddy/     │  │tor (ByteBuddy)  │  │ (Redis + RDB)   │  │
│  │  MockServer)    │  │                 │  │                 │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │
│           │                    │                    │           │
│           ▼                    ▼                    ▼           │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    VCRCassette                              ││
│  │  • generateKey(testId, callIndex)                           ││
│  │  • store(key, response)                                     ││
│  │  • retrieve(key) → Optional<Response>                       ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Redis Storage Layer                          │
├─────────────────────────────────────────────────────────────────┤
│  Keys:                                                          │
│    vcr:cassette:{testClass}:{testMethod}:{callIndex}            │
│    vcr:registry:{testClass}:{testMethod}                        │
│    vcr:embedding:{testClass}:{testMethod}:{model}:{callIndex}   │
│                                                                 │
│  Persistence:                                                   │
│    tests/vcr-data/dump.rdb      (RDB snapshot)                  │
│    tests/vcr-data/appendonly/   (AOF segments)                  │
└─────────────────────────────────────────────────────────────────┘
```

## Component Design

### 1. VCR Annotations

```java
package com.redis.vl.test.vcr;

/**
 * Class-level annotation to enable VCR for all tests in the class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(VCRExtension.class)
public @interface VCRTest {
    VCRMode mode() default VCRMode.PLAYBACK;
    String dataDir() default "src/test/resources/vcr-data";
}

/**
 * Method-level annotation to override class-level VCR mode.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VCRMode {
    VCRMode value();
}

/**
 * Skip VCR for specific test.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VCRDisabled {
}

/**
 * VCR operating modes.
 */
public enum VCRMode {
    /** Use cached only, fail if missing */
    PLAYBACK,

    /** Always call API, overwrite cache */
    RECORD,

    /** Only record tests not in registry */
    RECORD_NEW,

    /** Re-record only failed tests */
    RECORD_FAILED,

    /** Use cache if exists, else record */
    PLAYBACK_OR_RECORD,

    /** Disable VCR entirely */
    OFF
}
```

### 2. JUnit 5 Extension

```java
package com.redis.vl.test.vcr;

import org.junit.jupiter.api.extension.*;

public class VCRExtension implements
        BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        AfterEachCallback,
        TestWatcher {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(VCRExtension.class);

    private VCRContext context;

    @Override
    public void beforeAll(ExtensionContext ctx) throws Exception {
        // 1. Get VCR configuration from @VCRTest annotation
        VCRTest config = ctx.getRequiredTestClass()
            .getAnnotation(VCRTest.class);

        // 2. Start Redis container with persistence volume
        context = new VCRContext(config);
        context.startRedis();

        // 3. Load existing cassettes from RDB/AOF
        context.loadCassettes();

        // 4. Install interceptors
        context.installInterceptors();

        // 5. Store in extension context
        ctx.getStore(NAMESPACE).put("vcr-context", context);
    }

    @Override
    public void beforeEach(ExtensionContext ctx) throws Exception {
        // 1. Get test identifier
        String testId = getTestId(ctx);

        // 2. Reset call counters
        context.resetCallCounters();

        // 3. Set current test context (for key generation)
        context.setCurrentTest(testId);

        // 4. Check for method-level mode override
        VCRMode methodMode = ctx.getRequiredTestMethod()
            .getAnnotation(VCRMode.class);
        if (methodMode != null) {
            context.setEffectiveMode(methodMode.value());
        }
    }

    @Override
    public void afterEach(ExtensionContext ctx) throws Exception {
        // Handled by TestWatcher methods
    }

    @Override
    public void testSuccessful(ExtensionContext ctx) {
        String testId = getTestId(ctx);
        context.getRegistry().registerSuccess(testId,
            context.getCurrentCassetteKeys());
    }

    @Override
    public void testFailed(ExtensionContext ctx, Throwable cause) {
        String testId = getTestId(ctx);
        context.getRegistry().registerFailure(testId, cause.getMessage());

        // Optionally delete cassettes for failed tests in RECORD mode
        if (context.getEffectiveMode() == VCRMode.RECORD) {
            context.deleteCassettes(context.getCurrentCassetteKeys());
        }
    }

    @Override
    public void afterAll(ExtensionContext ctx) throws Exception {
        // 1. Trigger Redis BGSAVE
        if (context.isRecordMode()) {
            context.persistCassettes();
        }

        // 2. Print statistics
        context.printStatistics();

        // 3. Restore original methods
        context.uninstallInterceptors();

        // 4. Stop Redis
        context.stopRedis();
    }

    private String getTestId(ExtensionContext ctx) {
        return ctx.getRequiredTestClass().getName() + ":" +
               ctx.getRequiredTestMethod().getName();
    }
}
```

### 3. VCR Context (State Management)

```java
package com.redis.vl.test.vcr;

public class VCRContext {
    private final VCRTest config;
    private final Path dataDir;
    private RedisContainer redisContainer;
    private JedisPooled jedis;
    private VCRRegistry registry;
    private LLMInterceptor llmInterceptor;
    private EmbeddingInterceptor embeddingInterceptor;

    private String currentTestId;
    private VCRMode effectiveMode;
    private List<String> currentCassetteKeys = new ArrayList<>();
    private Map<String, AtomicInteger> callCounters = new ConcurrentHashMap<>();

    // Statistics
    private AtomicLong cacheHits = new AtomicLong();
    private AtomicLong cacheMisses = new AtomicLong();
    private AtomicLong apiCalls = new AtomicLong();

    public VCRContext(VCRTest config) {
        this.config = config;
        this.dataDir = Path.of(config.dataDir());
        this.effectiveMode = config.mode();
    }

    public void startRedis() {
        // Ensure data directory exists
        Files.createDirectories(dataDir);

        // Start Redis with volume mount
        redisContainer = new RedisContainer(DockerImageName.parse("redis/redis-stack:latest"))
            .withFileSystemBind(dataDir.toAbsolutePath().toString(), "/data",
                BindMode.READ_WRITE)
            .withCommand(buildRedisCommand());

        redisContainer.start();

        jedis = new JedisPooled(redisContainer.getHost(),
            redisContainer.getFirstMappedPort());
    }

    private String buildRedisCommand() {
        StringBuilder cmd = new StringBuilder("redis-stack-server");
        cmd.append(" --appendonly yes");
        cmd.append(" --appendfsync everysec");
        cmd.append(" --dir /data");
        cmd.append(" --dbfilename dump.rdb");

        if (isRecordMode()) {
            cmd.append(" --save '60 1' --save '300 10'");
        } else {
            cmd.append(" --save ''"); // Disable saves in playback
        }

        return cmd.toString();
    }

    public void installInterceptors() {
        llmInterceptor = new LLMInterceptor(this);
        llmInterceptor.install();

        embeddingInterceptor = new EmbeddingInterceptor(this);
        embeddingInterceptor.install();
    }

    public String generateCassetteKey(String type) {
        int callIndex = callCounters
            .computeIfAbsent(currentTestId + ":" + type, k -> new AtomicInteger())
            .incrementAndGet();

        String key = String.format("vcr:%s:%s:%04d", type, currentTestId, callIndex);
        currentCassetteKeys.add(key);
        return key;
    }

    public void persistCassettes() {
        // Trigger BGSAVE and wait for completion
        jedis.bgsave();

        long lastSave = jedis.lastsave();
        while (jedis.lastsave() == lastSave) {
            Thread.sleep(100);
        }
    }

    public void printStatistics() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;

        System.out.println("=== VCR Statistics ===");
        System.out.printf("Cache Hits: %d%n", hits);
        System.out.printf("Cache Misses: %d%n", misses);
        System.out.printf("API Calls: %d%n", apiCalls.get());
        System.out.printf("Hit Rate: %.1f%%%n", hitRate);
    }

    // Getters, setters...
}
```

### 4. LLM Interceptor (ByteBuddy)

```java
package com.redis.vl.test.vcr;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;

/**
 * Intercepts LLM API calls using ByteBuddy instrumentation.
 * Supports LangChain4J ChatLanguageModel and similar interfaces.
 */
public class LLMInterceptor {
    private final VCRContext context;
    private static VCRContext staticContext; // For Advice access

    public LLMInterceptor(VCRContext context) {
        this.context = context;
    }

    public void install() {
        staticContext = context;
        ByteBuddyAgent.install();

        // Intercept LangChain4J ChatLanguageModel.generate()
        new ByteBuddy()
            .redefine(OpenAiChatModel.class)
            .visit(Advice.to(ChatModelAdvice.class)
                .on(named("generate")))
            .make()
            .load(OpenAiChatModel.class.getClassLoader(),
                ClassReloadingStrategy.fromInstalledAgent());

        // Similarly for other LLM providers...
    }

    public void uninstall() {
        // Restore original classes
        staticContext = null;
    }

    public static class ChatModelAdvice {

        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static Object onEnter(
                @Advice.AllArguments Object[] args,
                @Advice.Origin String method) {

            if (staticContext == null) return null;

            String key = staticContext.generateCassetteKey("llm");

            // Try to get from cache
            Optional<String> cached = staticContext.getCassette(key);
            if (cached.isPresent()) {
                staticContext.recordCacheHit();
                // Return cached response (skip original method)
                return deserializeResponse(cached.get());
            }

            if (staticContext.getEffectiveMode() == VCRMode.PLAYBACK) {
                throw new VCRCassetteMissingException(
                    "No cassette found for key: " + key);
            }

            return null; // Proceed with original method
        }

        @Advice.OnMethodExit
        public static void onExit(
                @Advice.Enter Object cachedResult,
                @Advice.Return(readOnly = false) Object result) {

            if (staticContext == null) return;

            if (cachedResult != null) {
                // Use cached result
                result = cachedResult;
            } else {
                // Store new result
                String key = staticContext.getLastGeneratedKey();
                staticContext.storeCassette(key, serializeResponse(result));
                staticContext.recordApiCall();
            }
        }
    }
}
```

### 5. Embedding Interceptor

```java
package com.redis.vl.test.vcr;

/**
 * Intercepts embedding generation calls.
 * Integrates with RedisVL's EmbeddingsCache for storage.
 */
public class EmbeddingInterceptor {
    private final VCRContext context;
    private final EmbeddingsCache embeddingsCache;

    public EmbeddingInterceptor(VCRContext context) {
        this.context = context;
        this.embeddingsCache = new EmbeddingsCache(
            "vcr-embeddings",
            context.getJedis(),
            TimeUnit.DAYS.toSeconds(7)  // 7-day TTL
        );
    }

    public void install() {
        // Intercept SentenceTransformers, LangChain4J EmbeddingModel, etc.
        // Similar ByteBuddy pattern as LLMInterceptor
    }

    /**
     * Called by advice when embedding is requested.
     */
    public float[] intercept(String text, String modelName) {
        String key = context.generateCassetteKey("embedding");

        // Check cache first
        Optional<float[]> cached = embeddingsCache.getEmbedding(text, modelName);
        if (cached.isPresent()) {
            context.recordCacheHit();
            return cached.get();
        }

        if (context.getEffectiveMode() == VCRMode.PLAYBACK) {
            throw new VCRCassetteMissingException(
                "No embedding cassette for: " + text.substring(0, 50) + "...");
        }

        // Will be called after actual embedding is computed
        return null;
    }

    public void storeEmbedding(String text, String modelName, float[] embedding) {
        embeddingsCache.set(text, modelName, embedding);
        context.recordApiCall();
    }
}
```

### 6. VCR Registry

```java
package com.redis.vl.test.vcr;

/**
 * Tracks which tests have been recorded and their status.
 */
public class VCRRegistry {
    private final JedisPooled jedis;
    private static final String REGISTRY_KEY = "vcr:registry";
    private static final String TESTS_KEY = "vcr:registry:tests";

    public enum RecordingStatus {
        RECORDED, FAILED, MISSING, OUTDATED
    }

    public void registerSuccess(String testId, List<String> cassetteKeys) {
        String testKey = "vcr:test:" + testId;

        Map<String, String> data = Map.of(
            "status", RecordingStatus.RECORDED.name(),
            "recorded_at", Instant.now().toString(),
            "cassette_count", String.valueOf(cassetteKeys.size())
        );

        jedis.hset(testKey, data);
        jedis.sadd(TESTS_KEY, testId);

        // Store cassette keys
        if (!cassetteKeys.isEmpty()) {
            jedis.sadd(testKey + ":cassettes", cassetteKeys.toArray(new String[0]));
        }
    }

    public void registerFailure(String testId, String error) {
        String testKey = "vcr:test:" + testId;

        Map<String, String> data = Map.of(
            "status", RecordingStatus.FAILED.name(),
            "recorded_at", Instant.now().toString(),
            "error", error != null ? error : "Unknown error"
        );

        jedis.hset(testKey, data);
        jedis.sadd(TESTS_KEY, testId);
    }

    public RecordingStatus getTestStatus(String testId) {
        String testKey = "vcr:test:" + testId;
        String status = jedis.hget(testKey, "status");

        if (status == null) {
            return RecordingStatus.MISSING;
        }
        return RecordingStatus.valueOf(status);
    }

    public VCRMode determineEffectiveMode(String testId, VCRMode globalMode) {
        RecordingStatus status = getTestStatus(testId);

        return switch (globalMode) {
            case RECORD_NEW -> status == RecordingStatus.MISSING
                ? VCRMode.RECORD : VCRMode.PLAYBACK;

            case RECORD_FAILED -> status == RecordingStatus.FAILED ||
                                  status == RecordingStatus.MISSING
                ? VCRMode.RECORD : VCRMode.PLAYBACK;

            case PLAYBACK_OR_RECORD -> status == RecordingStatus.RECORDED
                ? VCRMode.PLAYBACK : VCRMode.RECORD;

            default -> globalMode;
        };
    }

    public Set<String> getAllRecordedTests() {
        return jedis.smembers(TESTS_KEY);
    }
}
```

### 7. Cassette Storage

```java
package com.redis.vl.test.vcr;

/**
 * Stores and retrieves cassette data from Redis.
 */
public class VCRCassetteStore {
    private final JedisPooled jedis;
    private final ObjectMapper objectMapper;

    public void store(String key, Object response) {
        // Serialize response to JSON
        Map<String, Object> cassette = new HashMap<>();
        cassette.put("type", response.getClass().getName());
        cassette.put("timestamp", Instant.now().toString());
        cassette.put("data", serializeResponse(response));

        jedis.jsonSet(key, Path2.ROOT_PATH, cassette);
    }

    public <T> Optional<T> retrieve(String key, Class<T> responseType) {
        Object data = jedis.jsonGet(key);
        if (data == null) {
            return Optional.empty();
        }

        Map<String, Object> cassette = (Map<String, Object>) data;
        return Optional.of(deserializeResponse(
            (Map<String, Object>) cassette.get("data"),
            responseType
        ));
    }

    private Map<String, Object> serializeResponse(Object response) {
        // Handle different response types:
        // - LangChain4J AiMessage
        // - OpenAI ChatCompletionResponse
        // - Anthropic Message
        // etc.

        if (response instanceof AiMessage aiMsg) {
            return Map.of(
                "content", aiMsg.text(),
                "toolExecutionRequests", serializeToolRequests(aiMsg)
            );
        }

        // Generic fallback
        return objectMapper.convertValue(response, Map.class);
    }
}
```

## Usage Examples

### Basic Usage

```java
@VCRTest(mode = VCRMode.PLAYBACK)
class RAGServiceIntegrationTest {

    @Test
    void testQueryWithContext() {
        // LLM calls are automatically intercepted
        RAGService rag = new RAGService(embedder, chatModel, index);

        String response = rag.query("What is Redis?");

        assertThat(response).contains("in-memory");
        // This test uses cached LLM responses - no API calls!
    }
}
```

### Recording New Tests

```java
@VCRTest(mode = VCRMode.RECORD_NEW)
class NewFeatureTest {

    @Test
    void testNewFeature() {
        // First run: makes real API calls and records them
        // Subsequent runs: uses cached responses
        ChatLanguageModel llm = createChatModel();
        String response = llm.generate("Hello, world!");

        assertThat(response).isNotEmpty();
    }
}
```

### Method-Level Override

```java
@VCRTest(mode = VCRMode.PLAYBACK)
class MixedModeTest {

    @Test
    void usesPlayback() {
        // Uses cached responses
    }

    @Test
    @VCRMode(VCRMode.RECORD)
    void forcesRecording() {
        // Always makes real API calls and updates cache
    }

    @Test
    @VCRDisabled
    void noVCR() {
        // VCR completely disabled - real API calls, no caching
    }
}
```

### Programmatic Control

```java
@VCRTest
class AdvancedTest {

    @RegisterExtension
    static VCRExtension vcr = VCRExtension.builder()
        .mode(VCRMode.PLAYBACK_OR_RECORD)
        .dataDir("src/test/resources/custom-vcr-data")
        .redisImage("redis/redis-stack:7.4")
        .enableInteractionLogging(true)
        .build();

    @Test
    void testWithCustomConfig() {
        // Test code...
    }
}
```

## Redis Data Structure

### Key Patterns

```
vcr:llm:{testClass}:{testMethod}:{callIndex}     → JSON (cassette)
vcr:embedding:{testClass}:{testMethod}:{model}:{callIndex} → HASH (embedding)
vcr:test:{testId}                                 → HASH (test metadata)
vcr:test:{testId}:cassettes                       → SET (cassette keys)
vcr:registry:tests                                → SET (all test IDs)
```

### Cassette JSON Format

```json
{
  "type": "dev.langchain4j.data.message.AiMessage",
  "timestamp": "2025-12-13T10:30:00Z",
  "data": {
    "content": "Redis is an in-memory data structure store...",
    "toolExecutionRequests": []
  }
}
```

### Persistence Files

```
src/test/resources/vcr-data/
├── dump.rdb              # RDB snapshot
└── appendonlydir/
    ├── appendonly.aof.manifest
    ├── appendonly.aof.1.base.rdb
    └── appendonly.aof.1.incr.aof
```

## Build Configuration

### Gradle Dependencies

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("com.redis.vl:redisvl-vcr:0.12.0")

    // Required for ByteBuddy instrumentation
    testImplementation("net.bytebuddy:byte-buddy:1.14.+")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.14.+")

    // Testcontainers for Redis
    testImplementation("org.testcontainers:testcontainers:1.19.+")
}
```

### JVM Arguments (for ByteBuddy agent)

```kotlin
tasks.test {
    jvmArgs("-XX:+AllowRedefinitionToAddDeleteMethods")
}
```

## CLI / Management Tools

```bash
# View VCR statistics
./gradlew vcrStats

# List unrecorded tests
./gradlew vcrListUnrecorded

# Clean failed test cassettes
./gradlew vcrCleanFailed

# Export cassettes to JSON (for backup/migration)
./gradlew vcrExport --output=vcr-backup.json

# Import cassettes from JSON
./gradlew vcrImport --input=vcr-backup.json
```

## Alternative: MockServer Approach

Instead of ByteBuddy instrumentation, use WireMock/MockServer for HTTP interception:

```java
@VCRTest(strategy = InterceptionStrategy.MOCK_SERVER)
class HttpBasedVCRTest {
    // Intercepts at HTTP level
    // Simpler but only works for HTTP-based LLM APIs
}
```

**Pros:**
- No bytecode manipulation
- Works with any HTTP client
- Easier debugging

**Cons:**
- Doesn't work with local models (ONNX, etc.)
- More complex URL matching

## TestNG Support

```java
package com.redis.vl.test.vcr.testng;

import org.testng.annotations.*;

@Listeners(VCRTestNGListener.class)
@VCRTest(mode = VCRMode.PLAYBACK)
public class TestNGExample {

    @Test
    public void testWithVCR() {
        // Same functionality as JUnit 5
    }
}
```

## Implementation Phases

| Phase | Scope | Effort |
|-------|-------|--------|
| **1** | Core VCRContext + Redis persistence | 2 days |
| **2** | JUnit 5 Extension + annotations | 2 days |
| **3** | LLM Interceptor (LangChain4J) | 3 days |
| **4** | Embedding Interceptor (integration with EmbeddingsCache) | 1 day |
| **5** | Registry + smart modes | 2 days |
| **6** | CLI tools + statistics | 1 day |
| **7** | TestNG support (optional) | 1 day |
| **8** | Documentation + examples | 1 day |

## Success Criteria

- [ ] Tests run in CI without API keys
- [ ] 95%+ cache hit rate after initial recording
- [ ] Test execution <100ms per cached LLM call
- [ ] Cassettes persist across CI runs (via committed vcr-data/)
- [ ] Seamless integration with existing RedisVL tests
- [ ] Clear error messages when cassettes are missing

## References

- [maestro-langgraph VCR Implementation](../../../maestro-langgraph/tests/utils/)
- [VCR.py (Python HTTP recording)](https://vcrpy.readthedocs.io/)
- [WireMock](https://wiremock.org/)
- [ByteBuddy](https://bytebuddy.net/)
- [JUnit 5 Extension Model](https://junit.org/junit5/docs/current/user-guide/#extensions)
