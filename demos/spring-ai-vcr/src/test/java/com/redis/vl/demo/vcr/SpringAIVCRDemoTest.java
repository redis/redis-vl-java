package com.redis.vl.demo.vcr;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.test.vcr.VCRMode;
import com.redis.vl.test.vcr.VCRModel;
import com.redis.vl.test.vcr.VCRTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;

/**
 * VCR Demo Tests for Spring AI.
 *
 * <p>This demo shows how to use VCR (Video Cassette Recorder) functionality to record and replay
 * LLM API calls with Spring AI.
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
@DisplayName("Spring AI VCR Demo")
class SpringAIVCRDemoTest {

  // Annotate model fields with @VCRModel - VCR wraps them automatically!
  // NOTE: Models must be initialized at field declaration time or in @BeforeAll,
  // not in @BeforeEach, because VCR wrapping happens before @BeforeEach runs.
  @VCRModel(modelName = "text-embedding-3-small")
  private EmbeddingModel embeddingModel = createEmbeddingModel();

  @VCRModel private ChatModel chatModel = createChatModel();

  private static String getApiKey() {
    String key = System.getenv("OPENAI_API_KEY");
    // In PLAYBACK mode, use a dummy key if none provided (VCR will use cached responses)
    return (key == null || key.isEmpty()) ? "vcr-playback-mode" : key;
  }

  private static OpenAiApi createOpenAiApi() {
    return OpenAiApi.builder().apiKey(getApiKey()).build();
  }

  private static EmbeddingModel createEmbeddingModel() {
    OpenAiEmbeddingOptions embeddingOptions =
        OpenAiEmbeddingOptions.builder().model("text-embedding-3-small").build();
    return new OpenAiEmbeddingModel(
        createOpenAiApi(), MetadataMode.EMBED, embeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
  }

  private static ChatModel createChatModel() {
    OpenAiChatOptions chatOptions =
        OpenAiChatOptions.builder().model("gpt-4o-mini").temperature(0.0).build();
    return OpenAiChatModel.builder()
        .openAiApi(createOpenAiApi())
        .defaultOptions(chatOptions)
        .build();
  }

  @Nested
  @DisplayName("Embedding Model VCR Tests")
  class EmbeddingModelTests {

    @Test
    @DisplayName("should embed a single text about Redis")
    void shouldEmbedSingleText() {
      // Use the model - calls are recorded/replayed transparently
      EmbeddingResponse response =
          embeddingModel.embedForResponse(List.of("Redis is an in-memory data store"));

      assertNotNull(response);
      assertNotNull(response.getResults());
      assertFalse(response.getResults().isEmpty());
      float[] vector = response.getResults().get(0).getOutput();
      assertNotNull(vector);
      assertTrue(vector.length > 0, "Embedding should have dimensions");
    }

    @Test
    @DisplayName("should embed text about vector search")
    void shouldEmbedVectorSearchText() {
      EmbeddingResponse response =
          embeddingModel.embedForResponse(
              List.of("Vector similarity search enables semantic retrieval"));

      assertNotNull(response);
      float[] vector = response.getResults().get(0).getOutput();
      assertTrue(vector.length > 0);
    }

    @Test
    @DisplayName("should embed multiple related texts")
    void shouldEmbedMultipleTexts() {
      // Multiple calls - each gets its own cassette key
      EmbeddingResponse response1 = embeddingModel.embedForResponse(List.of("What is Redis?"));
      EmbeddingResponse response2 = embeddingModel.embedForResponse(List.of("Redis is a database"));
      EmbeddingResponse response3 =
          embeddingModel.embedForResponse(List.of("How does caching work?"));

      assertNotNull(response1.getResults().get(0));
      assertNotNull(response2.getResults().get(0));
      assertNotNull(response3.getResults().get(0));

      // All embeddings should have the same dimensions
      assertEquals(
          response1.getResults().get(0).getOutput().length,
          response2.getResults().get(0).getOutput().length);
      assertEquals(
          response2.getResults().get(0).getOutput().length,
          response3.getResults().get(0).getOutput().length);
    }
  }

  @Nested
  @DisplayName("Chat Model VCR Tests")
  class ChatModelTests {

    @Test
    @DisplayName("should answer a question about Redis")
    void shouldAnswerRedisQuestion() {
      // Use the chat model - calls are recorded/replayed transparently
      String response = chatModel.call("What is Redis in one sentence?");

      assertNotNull(response);
      assertFalse(response.isEmpty());
    }

    @Test
    @DisplayName("should explain vector databases")
    void shouldExplainVectorDatabases() {
      String response =
          chatModel.call("Explain vector databases in two sentences for a developer.");

      assertNotNull(response);
      assertTrue(response.length() > 20, "Response should be substantive");
    }

    @Test
    @DisplayName("should provide code example")
    void shouldProvideCodeExample() {
      String response = chatModel.call("Show a one-line Redis SET command example in Python.");

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
      EmbeddingResponse queryEmbedding =
          embeddingModel.embedForResponse(List.of("How do I use Redis for caching?"));

      assertNotNull(queryEmbedding.getResults().get(0));

      // Step 2: Generate an answer (simulating after retrieval)
      String answer =
          chatModel.call("Based on Redis documentation, explain caching in one sentence.");

      assertNotNull(answer);
      assertFalse(answer.isEmpty());
    }
  }
}
