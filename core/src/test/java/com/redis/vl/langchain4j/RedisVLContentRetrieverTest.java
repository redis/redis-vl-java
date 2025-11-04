package com.redis.vl.langchain4j;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test for RedisVLContentRetriever - LangChain4J integration.
 *
 * <p>Tests the ContentRetriever implementation for RAG workflows.
 */
class RedisVLContentRetrieverTest {

  @Mock private RedisVLEmbeddingStore embeddingStore;

  private EmbeddingModel embeddingModel;
  private RedisVLContentRetriever retriever;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    retriever = new RedisVLContentRetriever(embeddingStore, embeddingModel);
  }

  @Test
  void testRetrieveBasic() {
    // This test requires an actual embeddingStore implementation
    // Here we just verify the component is created correctly
    assertNotNull(retriever);
  }

  @Test
  void testBuilderPattern() {
    // Test builder
    RedisVLContentRetriever built =
        RedisVLContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(5)
            .minScore(0.8)
            .build();

    assertNotNull(built);
  }

  @Test
  void testRetrieveWithNullQuery() {
    // When/Then
    assertThrows(IllegalArgumentException.class, () -> retriever.retrieve((Query) null));
  }
}
