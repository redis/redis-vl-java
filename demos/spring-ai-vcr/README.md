# Spring AI VCR Demo

This demo shows how to use the VCR (Video Cassette Recorder) test system with Spring AI models. VCR records LLM/embedding API responses to Redis and replays them in subsequent test runs, enabling fast, deterministic, and cost-effective testing.

## Features

- Record and replay Spring AI `EmbeddingModel` responses
- Record and replay Spring AI `ChatModel` responses
- Declarative `@VCRTest` and `@VCRModel` annotations
- Automatic model wrapping via JUnit 5 extension
- Redis-backed persistence with automatic test isolation

## Quick Start

### 1. Annotate Your Test Class

```java
import com.redis.vl.test.vcr.VCRMode;
import com.redis.vl.test.vcr.VCRModel;
import com.redis.vl.test.vcr.VCRTest;

@VCRTest(mode = VCRMode.PLAYBACK_OR_RECORD)
class MySpringAITest {

    @VCRModel(modelName = "text-embedding-3-small")
    private EmbeddingModel embeddingModel = createEmbeddingModel();

    @VCRModel
    private ChatModel chatModel = createChatModel();

    // Models must be initialized at field declaration time,
    // not in @BeforeEach (VCR wrapping happens before @BeforeEach)
}
```

### 2. Use Models Normally

```java
@Test
void shouldEmbedText() {
    // First run: calls real API and records response
    // Subsequent runs: replays from Redis cassette
    EmbeddingResponse response = embeddingModel.embedForResponse(
        List.of("What is Redis?")
    );

    assertNotNull(response.getResults().get(0));
}

@Test
void shouldGenerateResponse() {
    String response = chatModel.call("Explain Redis in one sentence.");

    assertNotNull(response);
}
```

## VCR Modes

| Mode | Description | API Key Required |
|------|-------------|------------------|
| `PLAYBACK` | Only use recorded cassettes. Fails if cassette missing. | No |
| `PLAYBACK_OR_RECORD` | Use cassette if available, record if not. | Only for first run |
| `RECORD` | Always call real API and record response. | Yes |
| `OFF` | Bypass VCR, always call real API. | Yes |

### Setting Mode via Environment Variable

Override the annotation mode at runtime without changing code:

```bash
# Record new cassettes
VCR_MODE=RECORD ./gradlew :demos:spring-ai-vcr:test

# Playback only (CI/CD, no API key needed)
VCR_MODE=PLAYBACK ./gradlew :demos:spring-ai-vcr:test

# Default behavior from annotation
./gradlew :demos:spring-ai-vcr:test
```

## Running the Demo

### With Pre-recorded Cassettes (No API Key)

The demo includes pre-recorded cassettes in `src/test/resources/vcr-data/`. Run tests without an API key:

```bash
./gradlew :demos:spring-ai-vcr:test
```

### Recording New Cassettes

To record fresh cassettes, set your OpenAI API key:

```bash
OPENAI_API_KEY=your-key VCR_MODE=RECORD ./gradlew :demos:spring-ai-vcr:test
```

## How It Works

1. **Test Setup**: `@VCRTest` annotation triggers the VCR JUnit 5 extension
2. **Container Start**: A Redis Stack container is started with persistence enabled
3. **Model Wrapping**: Fields annotated with `@VCRModel` are wrapped with VCR proxies
4. **Recording**: When a model is called, VCR checks for existing cassette:
   - **Cache hit**: Returns recorded response
   - **Cache miss**: Calls real API, stores response as cassette
5. **Persistence**: Cassettes are saved to `vcr-data/` directory via Redis persistence
6. **Cleanup**: Container stops, data persists for next run

## Cassette Storage

Cassettes are stored in Redis JSON format with keys like:

```
vcr:embedding:MyTest.testMethod:0001
vcr:chat:MyTest.testMethod:0001
```

Data persists to `src/test/resources/vcr-data/` via Redis AOF/RDB.

## Test Structure

```
demos/spring-ai-vcr/
├── src/test/java/
│   └── com/redis/vl/demo/vcr/
│       └── SpringAIVCRDemoTest.java
└── src/test/resources/
    └── vcr-data/           # Persisted cassettes
        ├── appendonly.aof
        └── dump.rdb
```

## Configuration Options

### @VCRTest Annotation

| Parameter | Default | Description |
|-----------|---------|-------------|
| `mode` | `PLAYBACK_OR_RECORD` | VCR operating mode |
| `dataDir` | `src/test/resources/vcr-data` | Cassette storage directory |
| `redisImage` | `redis/redis-stack:latest` | Redis Docker image |

### @VCRModel Annotation

| Parameter | Default | Description |
|-----------|---------|-------------|
| `modelName` | `""` | Optional model identifier for logging |

## Spring AI Specifics

### Supported Model Types

- `org.springframework.ai.embedding.EmbeddingModel`
- `org.springframework.ai.chat.model.ChatModel`

### Creating Models for VCR

```java
private static String getApiKey() {
    String key = System.getenv("OPENAI_API_KEY");
    // In PLAYBACK mode, use a dummy key (VCR will use cached responses)
    return (key == null || key.isEmpty()) ? "vcr-playback-mode" : key;
}

private static EmbeddingModel createEmbeddingModel() {
    OpenAiApi api = OpenAiApi.builder().apiKey(getApiKey()).build();
    OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
        .model("text-embedding-3-small")
        .build();
    return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options,
        RetryUtils.DEFAULT_RETRY_TEMPLATE);
}

private static ChatModel createChatModel() {
    OpenAiApi api = OpenAiApi.builder().apiKey(getApiKey()).build();
    OpenAiChatOptions options = OpenAiChatOptions.builder()
        .model("gpt-4o-mini")
        .temperature(0.0)
        .build();
    return OpenAiChatModel.builder()
        .openAiApi(api)
        .defaultOptions(options)
        .build();
}
```

## Best Practices

1. **Initialize models at field declaration** - Not in `@BeforeEach`
2. **Use dummy API key in PLAYBACK mode** - VCR will use cached responses
3. **Commit cassettes to version control** - Enables reproducible tests
4. **Use specific test names** - Cassette keys include test class and method names
5. **Re-record periodically** - API responses may change over time
6. **Set temperature to 0** - For deterministic LLM responses during recording

## Troubleshooting

### Tests fail with "Cassette missing"

- Ensure cassettes exist in `src/test/resources/vcr-data/`
- Run once with `VCR_MODE=RECORD` and API key to generate cassettes

### API key required error

- In `PLAYBACK` mode, use a dummy key: `"vcr-playback-mode"`
- VCR won't call the real API when cassettes exist

### Tests pass but call real API

- Verify models are initialized at field declaration, not `@BeforeEach`
- Check that `@VCRModel` annotation is present on model fields

### Spring AI version compatibility

- VCR wrappers implement Spring AI interfaces
- Test with your specific Spring AI version for compatibility

## See Also

- [LangChain4J VCR Demo](../langchain4j-vcr/README.md)
- [VCR Test System Documentation](../../README.md#-experimental-vcr-test-system)
