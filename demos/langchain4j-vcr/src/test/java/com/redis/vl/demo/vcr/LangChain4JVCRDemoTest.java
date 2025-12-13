package com.redis.vl.demo.vcr;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.test.vcr.VCRMode;
import com.redis.vl.test.vcr.VCRModel;
import com.redis.vl.test.vcr.VCRTest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * VCR Demo Tests for LangChain4J.
 *
 * <p>This demo shows how to use VCR (Video Cassette Recorder) functionality to record and replay
 * LLM API calls with LangChain4J.
 *
 * <h3>How to Use VCR in Your Tests:</h3>
 *
 * <ol>
 *   <li>Annotate your test class with {@code @VCRTest}
 *   <li>Annotate your model fields with {@code @VCRModel}
 *   <li>Initialize your models in {@code @BeforeEach} - VCR wraps them automatically
 *   <li>Use the models normally - first run records, subsequent runs replay
 * </ol>
 *
 * <h3>Benefits:</h3>
 *
 * <ul>
 *   <li>Fast, deterministic tests that don't call real LLM APIs after recording
 *   <li>Cost savings by avoiding repeated API calls
 *   <li>Offline development and CI/CD without API keys
 *   <li>Recorded data persists across test runs
 * </ul>
 */
// VCR mode choices:
// - PLAYBACK: Uses pre-recorded cassettes only (requires recorded data, no API key needed)
// - PLAYBACK_OR_RECORD: Uses cassettes if available, records if not (needs API key for first run)
// - RECORD: Always records fresh data (always needs API key)
// This demo uses PLAYBACK since cassettes are pre-recorded and committed to the repo
@VCRTest(mode = VCRMode.PLAYBACK)
@DisplayName("LangChain4J VCR Demo")
class LangChain4JVCRDemoTest {

  // Annotate model fields with @VCRModel - VCR wraps them automatically!
  // NOTE: Models must be initialized at field declaration time or in @BeforeAll,
  // not in @BeforeEach, because VCR wrapping happens before @BeforeEach runs.
  @VCRModel(modelName = "text-embedding-3-small")
  private EmbeddingModel embeddingModel = createEmbeddingModel();

  @VCRModel private ChatLanguageModel chatModel = createChatModel();

  private static String getApiKey() {
    String key = System.getenv("OPENAI_API_KEY");
    // In PLAYBACK mode, use a dummy key if none provided (VCR will use cached responses)
    return (key == null || key.isEmpty()) ? "vcr-playback-mode" : key;
  }

  private static EmbeddingModel createEmbeddingModel() {
    return OpenAiEmbeddingModel.builder()
        .apiKey(getApiKey())
        .modelName("text-embedding-3-small")
        .build();
  }

  private static ChatLanguageModel createChatModel() {
    return OpenAiChatModel.builder()
        .apiKey(getApiKey())
        .modelName("gpt-4o-mini")
        .temperature(0.0)
        .build();
  }

  @Nested
  @DisplayName("Embedding Model VCR Tests")
  class EmbeddingModelTests {

    @Test
    @DisplayName("should embed a single text about Redis")
    void shouldEmbedSingleText() {
      // Use the model - calls are recorded/replayed transparently
      Response<Embedding> response = embeddingModel.embed("Redis is an in-memory data store");

      assertNotNull(response);
      assertNotNull(response.content());
      float[] vector = response.content().vector();
      assertNotNull(vector);
      assertTrue(vector.length > 0, "Embedding should have dimensions");
    }

    @Test
    @DisplayName("should embed text about vector search")
    void shouldEmbedVectorSearchText() {
      Response<Embedding> response =
          embeddingModel.embed("Vector similarity search enables semantic retrieval");

      assertNotNull(response);
      float[] vector = response.content().vector();
      assertTrue(vector.length > 0);
    }

    @Test
    @DisplayName("should embed multiple related texts")
    void shouldEmbedMultipleTexts() {
      // Multiple calls - each gets its own cassette key
      Response<Embedding> response1 = embeddingModel.embed("What is Redis?");
      Response<Embedding> response2 = embeddingModel.embed("Redis is a database");
      Response<Embedding> response3 = embeddingModel.embed("How does caching work?");

      assertNotNull(response1.content());
      assertNotNull(response2.content());
      assertNotNull(response3.content());

      // All embeddings should have the same dimensions
      assertEquals(response1.content().vector().length, response2.content().vector().length);
      assertEquals(response2.content().vector().length, response3.content().vector().length);
    }
  }

  @Nested
  @DisplayName("Chat Model VCR Tests")
  class ChatModelTests {

    @Test
    @DisplayName("should answer a question about Redis")
    void shouldAnswerRedisQuestion() {
      // Use the chat model - calls are recorded/replayed transparently
      String response = chatModel.generate("What is Redis in one sentence?");

      assertNotNull(response);
      assertFalse(response.isEmpty());
    }

    @Test
    @DisplayName("should explain vector databases")
    void shouldExplainVectorDatabases() {
      String response =
          chatModel.generate("Explain vector databases in two sentences for a developer.");

      assertNotNull(response);
      assertTrue(response.length() > 20, "Response should be substantive");
    }

    @Test
    @DisplayName("should provide code example")
    void shouldProvideCodeExample() {
      String response = chatModel.generate("Show a one-line Redis SET command example in Python.");

      assertNotNull(response);
    }
  }

  @Nested
  @DisplayName("Combined RAG-style VCR Tests")
  class CombinedTests {

    @Test
    @DisplayName("should simulate RAG: embed query then generate answer")
    void shouldSimulateRAG() {
      // Step 1: Embed the user query (as you would to find relevant documents)
      Response<Embedding> queryEmbedding = embeddingModel.embed("How do I use Redis for caching?");

      assertNotNull(queryEmbedding.content());

      // Step 2: Generate an answer (simulating after retrieval)
      String answer =
          chatModel.generate("Based on Redis documentation, explain caching in one sentence.");

      assertNotNull(answer);
      assertFalse(answer.isEmpty());
    }
  }
}
