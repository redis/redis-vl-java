package com.redis.vl.demo.rag.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.redis.vl.demo.rag.model.CacheType;
import com.redis.vl.demo.rag.model.ChatMessage;
import com.redis.vl.demo.rag.model.LLMConfig;
import com.redis.vl.langchain4j.RedisVLContentRetriever;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Test for RAGService - LangChain4J based RAG orchestration. */
class RAGServiceTest {

  @Mock private RedisVLContentRetriever contentRetriever;

  @Mock private ChatLanguageModel chatModel;

  @Mock private CostTracker costTracker;

  private RAGService ragService;
  private LLMConfig config;

  @Mock private com.redis.vl.langchain4j.RedisVLDocumentStore documentStore;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    config = LLMConfig.defaultConfig(LLMConfig.Provider.OPENAI, "test-key");
    ragService = new RAGService(contentRetriever, documentStore, chatModel, costTracker, config, null, null, null);
  }

  @Test
  void testQueryWithContext() {
    // Given
    String userQuery = "What is Redis?";
    List<Content> retrievedContent =
        List.of(
            Content.from(TextSegment.from("Redis is an in-memory database")),
            Content.from(TextSegment.from("Redis supports various data structures")));

    when(contentRetriever.retrieve(any(Query.class))).thenReturn(retrievedContent);

    dev.langchain4j.data.message.AiMessage aiMessage =
        dev.langchain4j.data.message.AiMessage.from("Redis is a fast in-memory database.");
    Response<dev.langchain4j.data.message.AiMessage> response =
        Response.from(aiMessage, null, null);
    when(chatModel.generate(anyList())).thenReturn(response);

    when(costTracker.countTokens(anyString())).thenReturn(10);
    when(costTracker.calculateCost(eq(config.provider()), eq(config.model()), anyInt()))
        .thenReturn(0.0001);

    // When
    ChatMessage result = ragService.query(userQuery, CacheType.NONE);

    // Then
    assertNotNull(result);
    assertEquals(ChatMessage.Role.ASSISTANT, result.role());
    assertEquals("Redis is a fast in-memory database.", result.content());
    assertFalse(result.fromCache());
    assertTrue(result.costUsd() > 0);
    verify(contentRetriever).retrieve(any(Query.class));
    verify(chatModel).generate(anyList());
  }

  @Test
  void testQueryWithCache() {
    // Given
    String userQuery = "What is Redis?";
    when(contentRetriever.retrieve(any(Query.class))).thenReturn(List.of());

    dev.langchain4j.data.message.AiMessage aiMessage =
        dev.langchain4j.data.message.AiMessage.from("Cached response");
    Response<dev.langchain4j.data.message.AiMessage> response =
        Response.from(aiMessage, null, null);
    when(chatModel.generate(anyList())).thenReturn(response);

    when(costTracker.countTokens(anyString())).thenReturn(5);
    when(costTracker.calculateCost(any(), any(), anyInt())).thenReturn(0.0);

    // When
    ChatMessage result = ragService.query(userQuery, CacheType.LANGCACHE);

    // Then
    assertNotNull(result);
    // Note: Actual cache hit would be tested with real LangCache integration
    verify(contentRetriever).retrieve(any(Query.class));
  }

  @Test
  void testQueryWithEmptyContext() {
    // Given
    String userQuery = "Tell me about X";
    when(contentRetriever.retrieve(any(Query.class))).thenReturn(List.of());

    dev.langchain4j.data.message.AiMessage aiMessage =
        dev.langchain4j.data.message.AiMessage.from("I don't have information about X");
    Response<dev.langchain4j.data.message.AiMessage> response =
        Response.from(aiMessage, null, null);
    when(chatModel.generate(anyList())).thenReturn(response);

    when(costTracker.countTokens(anyString())).thenReturn(10);
    when(costTracker.calculateCost(any(), any(), anyInt())).thenReturn(0.00005);

    // When
    ChatMessage result = ragService.query(userQuery, CacheType.NONE);

    // Then
    assertNotNull(result);
    assertTrue(result.content().contains("don't have information"));
  }

  @Test
  void testQueryWithNullInput() {
    // When/Then
    assertThrows(IllegalArgumentException.class, () -> ragService.query(null, CacheType.NONE));
  }

  @Test
  void testQueryWithEmptyInput() {
    // When/Then
    assertThrows(IllegalArgumentException.class, () -> ragService.query("", CacheType.NONE));
  }
}
