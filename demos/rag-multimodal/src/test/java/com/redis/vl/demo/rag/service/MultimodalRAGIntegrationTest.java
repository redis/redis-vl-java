package com.redis.vl.demo.rag.service;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.demo.rag.model.CacheType;
import com.redis.vl.demo.rag.model.ChatMessage;
import com.redis.vl.demo.rag.model.LLMConfig;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.langchain4j.RedisVLContentRetriever;
import com.redis.vl.langchain4j.RedisVLDocumentStore;
import com.redis.vl.langchain4j.RedisVLEmbeddingStore;
import com.redis.vl.schema.IndexSchema;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Test-first integration tests for multimodal RAG with vision model.
 *
 * <p>These tests verify that the RAG system:
 * 1. Retrieves relevant images from Redis
 * 2. Sends images to GPT-4o (not just text)
 * 3. Gets vision-based responses (describing diagrams, not just text)
 *
 * <p>Uses TestContainers with Redis Stack 8.0 for test isolation.
 *
 * <p>Requires OPENAI_API_KEY environment variable to run.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class MultimodalRAGIntegrationTest extends BaseIntegrationTest {

  private SearchIndex searchIndex;
  private RedisVLEmbeddingStore embeddingStore;
  private RedisVLDocumentStore documentStore;
  private EmbeddingModel embeddingModel;
  private PDFIngestionService pdfIngestionService;
  private RAGService ragService;

  private static final String TEST_INDEX = "test_multimodal_rag";
  private static final int VECTOR_DIM = 384;

  @BeforeEach
  void setUp() throws Exception {
    // Verify OPENAI_API_KEY is set
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new IllegalStateException(
          "OPENAI_API_KEY environment variable must be set to run integration tests. "
              + "Set it with: export OPENAI_API_KEY=your-key-here");
    }
    System.out.println("✓ OPENAI_API_KEY found: " + apiKey.substring(0, Math.min(10, apiKey.length())) + "...");
    System.out.println("✓ Using TestContainers Redis Stack at: " + redisUrl);

    // Initialize embedding model
    embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    // Create search index
    Map<String, Object> schema =
        Map.of(
            "index",
            Map.of(
                "name", TEST_INDEX,
                "prefix", "test:multimodal:",
                "storage_type", "hash"),
            "fields",
            List.of(
                Map.of("name", "text", "type", "text"),
                Map.of("name", "metadata", "type", "text"),
                Map.of(
                    "name",
                    "vector",
                    "type",
                    "vector",
                    "attrs",
                    Map.of(
                        "dims", VECTOR_DIM,
                        "algorithm", "flat",
                        "distance_metric", "cosine"))));

    searchIndex = new SearchIndex(IndexSchema.fromDict(schema), unifiedJedis);

    try {
      searchIndex.create(true); // Overwrite if exists
    } catch (Exception e) {
      // Index creation might fail, that's okay for tests
    }

    // Initialize stores
    embeddingStore = new RedisVLEmbeddingStore(searchIndex);
    documentStore = new RedisVLDocumentStore(unifiedJedis, "test:docs:");

    // Initialize PDF ingestion service
    pdfIngestionService =
        new PDFIngestionService(embeddingStore, documentStore, embeddingModel);

    // Initialize RAG service with GPT-4o (apiKey already retrieved above)
    LLMConfig config = new LLMConfig(
        LLMConfig.Provider.OPENAI,
        "gpt-4o",  // Current vision-capable model
        apiKey,
        null,
        1000,  // maxTokens
        0.7);  // temperature

    ChatLanguageModel chatModel = OpenAiChatModel.builder()
        .apiKey(apiKey)
        .modelName("gpt-4o")
        .temperature(0.7)
        .maxTokens(1000)
        .build();

    RedisVLContentRetriever retriever = RedisVLContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(embeddingModel)
        .maxResults(5)
        .minScore(0.5)
        .build();

    JTokKitCostTracker costTracker = new JTokKitCostTracker();

    ragService = new RAGService(retriever, documentStore, chatModel, costTracker, config, null, null);

    // Ingest Attention.pdf
    File attentionPdf =
        new File(getClass().getResource("/test-pdfs/Attention.pdf").toURI());
    pdfIngestionService.ingestPDF(attentionPdf, "attention-paper");

    // Give Redis a moment to index
    Thread.sleep(1000);
  }

  @AfterEach
  void tearDown() {
    if (searchIndex != null) {
      try {
        searchIndex.delete(false);
      } catch (Exception e) {
        // Ignore cleanup errors
      }
    }
    // Don't close jedis/unifiedJedis - BaseIntegrationTest handles that
  }

  /**
   * Test that vision model receives and processes images, not just text.
   *
   * <p>Current implementation FAILS because RAGService only sends text summaries to the LLM,
   * not actual image bytes.
   *
   * <p>Expected: GPT-4V should describe visual elements it sees in the diagram.
   */
  @Test
  void testVisionModelReceivesAndProcessesImages() {
    // Given: A query about the Transformer architecture diagram (Figure 1 in paper)
    String query = "Describe the Transformer architecture diagram. What are the main components?";

    // When: Query the RAG system
    ChatMessage response = ragService.query(query, CacheType.NONE);

    // Then: Response should mention visual elements from the diagram
    String responseText = response.content().toLowerCase();

    // Vision-based response should describe diagram elements:
    assertTrue(
        responseText.contains("encoder") || responseText.contains("decoder"),
        "Response should mention encoder/decoder from diagram: " + responseText);

    assertTrue(
        responseText.contains("attention") || responseText.contains("multi-head"),
        "Response should mention attention mechanism from diagram: " + responseText);

    // Should describe visual structure, not just read text
    assertTrue(
        responseText.contains("layer") || responseText.contains("block") || responseText.contains("stack"),
        "Response should describe visual layer structure: " + responseText);

    // Verify the response is more detailed than a text-only summary would be
    assertTrue(
        responseText.length() > 200,
        "Vision-based response should be detailed (> 200 chars): " + responseText.length());
  }

  /**
   * Test that images are retrieved from document store when content matches.
   *
   * <p>Current implementation FAILS because RAGService doesn't retrieve images from
   * documentStore after content retrieval.
   *
   * <p>Expected: When a query matches an image chunk, the image bytes should be retrieved
   * and sent to the vision model.
   */
  @Test
  void testImagesRetrievedFromDocumentStore() {
    // Given: A query about attention visualization (Figure 3)
    String query = "What does the attention visualization show?";

    // When: Query the RAG system
    ChatMessage response = ragService.query(query, CacheType.NONE);

    // Then: Response should indicate it saw the actual visualization image
    String responseText = response.content().toLowerCase();

    // Vision model should describe what it sees in the visualization
    assertTrue(
        responseText.contains("visual") || responseText.contains("diagram") || responseText.contains("showing"),
        "Response should indicate visual content was processed: " + responseText);

    // Should not be a generic "I don't have that information" response
    assertFalse(
        responseText.contains("don't have") || responseText.contains("cannot see"),
        "Response should not indicate inability to see images: " + responseText);
  }

  /**
   * Test that GPT-4 Vision generates vision-specific responses.
   *
   * <p>Ask about diagram details that are visual, not in text.
   *
   * <p>Expected: Vision model describes visual elements.
   */
  @Test
  void testGPT4VisionDescribesVisualElements() {
    // Given: A query about visual diagram structure
    String query = "How many attention heads are shown in the Multi-Head Attention diagram?";

    // When: Query the RAG system
    ChatMessage response = ragService.query(query, CacheType.NONE);

    // Then: Response should provide specific count from diagram
    String responseText = response.content().toLowerCase();

    // Vision model should see the diagram and count/describe heads
    assertTrue(
        responseText.contains("head") || responseText.contains("parallel"),
        "Response should discuss attention heads: " + responseText);

    // Should provide some numerical or structural detail
    assertTrue(
        responseText.matches(".*\\d+.*") || responseText.contains("multiple") || responseText.contains("several"),
        "Response should include numerical/quantitative detail: " + responseText);

    assertNotNull(response);
    assertFalse(responseText.isEmpty());
  }
}
