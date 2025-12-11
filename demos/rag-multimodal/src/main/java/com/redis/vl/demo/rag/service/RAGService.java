package com.redis.vl.demo.rag.service;

import com.redis.vl.demo.rag.model.ChatMessage;
import com.redis.vl.demo.rag.model.LLMConfig;
import com.redis.vl.extensions.cache.LangCacheSemanticCache;
import com.redis.vl.langchain4j.RedisVLContentRetriever;
import com.redis.vl.langchain4j.RedisVLDocumentStore;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) service using LangChain4J.
 *
 * <p>Orchestrates the RAG workflow:
 *
 * <ol>
 *   <li>Retrieve relevant context from Redis vector store
 *   <li>Build prompt with context
 *   <li>Generate response with LLM
 *   <li>Track costs and tokens
 * </ol>
 */
public class RAGService {

  private final RedisVLContentRetriever contentRetriever;
  private final RedisVLDocumentStore documentStore;
  private final ChatLanguageModel chatModel;
  private final CostTracker costTracker;
  private final LLMConfig config;
  private final LangCacheSemanticCache langCache;

  private static final String SYSTEM_PROMPT =
      """
      You are a helpful assistant that answers questions based on the provided context, including both text and images.
      When images are provided, carefully analyze their visual content.
      Describe diagrams, charts, and visual elements you see.
      If the context doesn't contain relevant information, politely say so.
      Always cite the context when possible.
      """;

  /**
   * Creates a new RAGService.
   *
   * @param contentRetriever Content retriever for finding relevant documents
   * @param documentStore Document store for retrieving raw image data
   * @param chatModel Chat language model for generation
   * @param costTracker Cost tracker for calculating costs
   * @param config LLM configuration
   * @param langCache LangCache wrapper for semantic caching (can be null)
   */
  public RAGService(
      RedisVLContentRetriever contentRetriever,
      RedisVLDocumentStore documentStore,
      ChatLanguageModel chatModel,
      CostTracker costTracker,
      LLMConfig config,
      LangCacheSemanticCache langCache) {
    this.contentRetriever = contentRetriever;
    this.documentStore = documentStore;
    this.chatModel = chatModel;
    this.costTracker = costTracker;
    this.config = config;
    this.langCache = langCache;
  }

  /**
   * Queries the RAG system.
   *
   * @param userQuery User's question
   * @param useCache Whether to use LangCache for semantic caching
   * @return Assistant response with cost tracking
   * @throws IllegalArgumentException if userQuery is null or empty
   */
  public ChatMessage query(String userQuery, boolean useCache) {
    if (userQuery == null || userQuery.trim().isEmpty()) {
      throw new IllegalArgumentException("User query cannot be null or empty");
    }

    // Check cache first if enabled
    if (useCache && langCache != null) {
      try {
        System.out.println("→ Checking LangCache for: " + userQuery);
        List<Map<String, Object>> cacheHits = langCache.check(userQuery, null, 1, null, null, 0.9f);
        System.out.println("← LangCache returned " + cacheHits.size() + " results");

        if (!cacheHits.isEmpty()) {
          Map<String, Object> hit = cacheHits.get(0);
          String cachedResponse = (String) hit.get("response");
          int tokenCount = costTracker.countTokens(cachedResponse);

          System.out.println("✓ Cache HIT for query: " + userQuery);
          return ChatMessage.assistant(cachedResponse, tokenCount, 0.0, config.model(), true);
        } else {
          System.out.println("✗ Cache MISS for query: " + userQuery);
        }
      } catch (IOException e) {
        System.err.println("LangCache check failed: " + e.getMessage());
        e.printStackTrace();
        // Fall through to normal generation
      }
    }

    // 1. Retrieve relevant context
    Query query = Query.from(userQuery);
    List<Content> retrievedContent = contentRetriever.retrieve(query);
    System.out.println("→ Retrieved " + retrievedContent.size() + " content items for query: " + userQuery);
    for (int i = 0; i < Math.min(3, retrievedContent.size()); i++) {
      String preview = retrievedContent.get(i).textSegment().text();
      if (preview.length() > 100) preview = preview.substring(0, 100) + "...";
      System.out.println("  [" + i + "] " + preview);
    }

    // 2. Separate text and image content
    List<Content> textContent = new ArrayList<>();
    List<Content> imageContent = new ArrayList<>();

    for (Content content : retrievedContent) {
      if (content.textSegment() != null && content.textSegment().metadata() != null) {
        String type = content.textSegment().metadata().getString("type");
        if ("IMAGE".equals(type)) {
          imageContent.add(content);
        } else {
          textContent.add(content);
        }
      } else {
        textContent.add(content);
      }
    }

    // 3. Build multimodal prompt with context and images
    String contextText = buildContext(textContent);
    List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
    messages.add(SystemMessage.from(SYSTEM_PROMPT));

    // Build user message with text and images
    if (!imageContent.isEmpty()) {
      // Multimodal message: text + images
      List<dev.langchain4j.data.message.Content> messageContents = new ArrayList<>();

      // Add text content
      StringBuilder promptText = new StringBuilder();
      if (!contextText.isEmpty()) {
        promptText.append("Text Context:\n").append(contextText).append("\n\n");
      }
      promptText.append("Question: ").append(userQuery);
      messageContents.add(TextContent.from(promptText.toString()));

      // Add image content
      for (Content imgContent : imageContent) {
        try {
          String chunkId = imgContent.textSegment().metadata().getString("chunk_id");
          if (chunkId != null) {
            Optional<RedisVLDocumentStore.Document> doc = documentStore.retrieve(chunkId);
            if (doc.isPresent()) {
              byte[] imageBytes = doc.get().content();
              String base64Image = Base64.getEncoder().encodeToString(imageBytes);
              messageContents.add(ImageContent.from(base64Image, "image/png"));
              System.out.println("✓ Added image to multimodal prompt: " + imgContent.textSegment().text());
            }
          }
        } catch (Exception e) {
          System.err.println("Failed to retrieve image: " + e.getMessage());
        }
      }

      messages.add(UserMessage.from(messageContents));
    } else {
      // Text-only message
      if (!contextText.isEmpty()) {
        messages.add(
            UserMessage.from(
                String.format(
                    """
                    Context:
                    %s

                    Question: %s
                    """,
                    contextText, userQuery)));
      } else {
        messages.add(UserMessage.from(userQuery));
      }
    }

    // 4. Generate response
    Response<AiMessage> response = chatModel.generate(messages);
    String responseText = response.content().text();

    // 4. Store in cache if enabled
    if (useCache && langCache != null) {
      try {
        System.out.println("→ Storing to LangCache: " + userQuery);
        String entryId = langCache.store(userQuery, responseText, null);
        System.out.println("✓ Cached response for query: " + userQuery + " (entry_id: " + entryId + ")");
      } catch (IOException e) {
        System.err.println("LangCache store failed: " + e.getMessage());
        e.printStackTrace();
      }
    }

    // 5. Calculate costs
    int tokenCount = costTracker.countTokens(responseText);
    double cost = costTracker.calculateCost(config.provider(), config.model(), tokenCount);

    return ChatMessage.assistant(responseText, tokenCount, cost, config.model(), false);
  }

  /**
   * Builds context string from retrieved content.
   *
   * @param content Retrieved content
   * @return Formatted context string
   */
  private String buildContext(List<Content> content) {
    if (content == null || content.isEmpty()) {
      return "";
    }

    return content.stream()
        .map(c -> c.textSegment().text())
        .collect(Collectors.joining("\n\n"));
  }
}
