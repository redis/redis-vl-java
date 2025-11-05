package com.redis.vl.demo.rag.service;

import com.redis.vl.demo.rag.config.AppConfig;
import com.redis.vl.demo.rag.model.LLMConfig;
import com.redis.vl.extensions.cache.LangCacheSemanticCache;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.langchain4j.RedisVLContentRetriever;
import com.redis.vl.langchain4j.RedisVLDocumentStore;
import com.redis.vl.langchain4j.RedisVLEmbeddingStore;
import com.redis.vl.schema.IndexSchema;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.UnifiedJedis;

/**
 * Factory for creating and wiring up application services.
 *
 * <p>Handles initialization of Redis connections, embedding models, and all service dependencies.
 */
public class ServiceFactory {

  private static final String DEFAULT_INDEX_NAME = "rag_multimodal_docs";
  private static final int VECTOR_DIM = 384; // All-MiniLM-L6-v2 dimensions

  private UnifiedJedis jedis;
  private SearchIndex searchIndex;
  private RedisVLEmbeddingStore embeddingStore;
  private RedisVLDocumentStore documentStore;
  private EmbeddingModel embeddingModel;
  private CostTracker costTracker;
  private LangCacheSemanticCache langCache;

  /**
   * Initializes all services with default configuration.
   *
   * @param redisHost Redis host
   * @param redisPort Redis port
   * @throws Exception if initialization fails
   */
  public void initialize(String redisHost, int redisPort) throws Exception {
    // Initialize Redis connection
    jedis = new JedisPooled(redisHost, redisPort);

    // Initialize embedding model (local, no API key needed)
    embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    // Initialize cost tracker
    costTracker = new JTokKitCostTracker();

    // Create search index for embeddings
    searchIndex = createSearchIndex();

    // Initialize stores
    embeddingStore = new RedisVLEmbeddingStore(searchIndex);
    documentStore = new RedisVLDocumentStore(jedis, "rag:docs:");

    // Initialize LangCache if enabled
    AppConfig config = AppConfig.getInstance();
    if (config.isLangCacheEnabled()) {
      langCache = new LangCacheSemanticCache.Builder()
          .name("rag_demo_cache")
          .serverUrl(config.getLangCacheUrl())
          .cacheId(config.getLangCacheCacheId())
          .apiKey(config.getLangCacheApiKey())
          .useSemanticSearch(true)
          .useExactSearch(true)
          .build();
      System.out.println("LangCache initialized: " + config.getLangCacheUrl());
    }
  }

  /**
   * Creates a search index for storing document embeddings.
   *
   * @return SearchIndex instance
   */
  private SearchIndex createSearchIndex() {
    Map<String, Object> schema =
        Map.of(
            "index",
            Map.of(
                "name", DEFAULT_INDEX_NAME,
                "prefix", "rag:doc:",
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

    SearchIndex index = new SearchIndex(IndexSchema.fromDict(schema), jedis);

    // Create index if it doesn't exist
    try {
      index.create(false); // Don't overwrite existing
    } catch (Exception e) {
      // Index might already exist, that's fine
      System.out.println("Index already exists or creation failed: " + e.getMessage());
    }

    return index;
  }

  /**
   * Creates a RAG service with the specified LLM configuration.
   *
   * @param config LLM configuration
   * @return RAGService instance
   * @throws IllegalStateException if services not initialized
   */
  public RAGService createRAGService(LLMConfig config) {
    if (embeddingStore == null || embeddingModel == null || costTracker == null) {
      throw new IllegalStateException("Services not initialized. Call initialize() first.");
    }

    // Create content retriever
    RedisVLContentRetriever retriever =
        RedisVLContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(5)
            .minScore(0.7)
            .build();

    // Create chat model based on provider
    ChatLanguageModel chatModel = createChatModel(config);

    return new RAGService(retriever, documentStore, chatModel, costTracker, config, langCache);
  }

  /**
   * Creates a PDF ingestion service.
   *
   * @return PDFIngestionService instance
   * @throws IllegalStateException if services not initialized
   */
  public PDFIngestionService createPDFIngestionService() {
    if (embeddingStore == null || documentStore == null || embeddingModel == null) {
      throw new IllegalStateException("Services not initialized. Call initialize() first.");
    }

    return new PDFIngestionService(embeddingStore, documentStore, embeddingModel);
  }

  /**
   * Creates a chat language model based on configuration.
   *
   * @param config LLM configuration
   * @return ChatLanguageModel instance
   */
  private ChatLanguageModel createChatModel(LLMConfig config) {
    return switch (config.provider()) {
      case OPENAI ->
          OpenAiChatModel.builder()
              .apiKey(config.apiKey())
              .modelName(config.model())
              .temperature(config.temperature())
              .maxTokens(config.maxTokens())
              .build();

      case ANTHROPIC ->
          dev.langchain4j.model.anthropic.AnthropicChatModel.builder()
              .apiKey(config.apiKey())
              .modelName(config.model())
              .temperature(config.temperature())
              .maxTokens(config.maxTokens())
              .build();

      case AZURE ->
          dev.langchain4j.model.azure.AzureOpenAiChatModel.builder()
              .apiKey(config.apiKey())
              .deploymentName(config.model())
              .temperature(config.temperature())
              .maxTokens(config.maxTokens())
              .build();

      case OLLAMA ->
          dev.langchain4j.model.ollama.OllamaChatModel.builder()
              .baseUrl(config.baseUrl())
              .modelName(config.model())
              .temperature(config.temperature())
              .build();
    };
  }

  /**
   * Gets the Redis connection.
   *
   * @return UnifiedJedis instance
   */
  public UnifiedJedis getJedis() {
    return jedis;
  }

  /**
   * Gets the cost tracker.
   *
   * @return CostTracker instance
   */
  public CostTracker getCostTracker() {
    return costTracker;
  }

  /**
   * Closes all resources.
   */
  public void close() {
    if (jedis != null) {
      jedis.close();
    }
  }
}
