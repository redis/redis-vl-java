package com.redis.vl.extensions.messagehistory;

import static com.redis.vl.extensions.Constants.*;

import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.Filter;
import com.redis.vl.query.FilterQuery;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.UnifiedJedis;

/**
 * Semantic Message History for storing and retrieving LLM conversation history with vector
 * embeddings for semantic search.
 *
 * <p>Stores user prompts and LLM responses with vector embeddings to allow for enriching future
 * prompts with semantically relevant session context. Messages are tagged by session to support
 * multiple concurrent conversations.
 *
 * <p>Matches the Python SemanticMessageHistory from
 * redisvl.extensions.message_history.semantic_history
 *
 * <p>This class is final to prevent finalizer attacks (SEI CERT OBJ11-J).
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "CT_CONSTRUCTOR_THROW"},
    justification =
        "SearchIndex, UnifiedJedis and BaseVectorizer are intentionally shared; "
            + "CT_CONSTRUCTOR_THROW suppressed as class is final preventing finalizer attacks")
public final class SemanticMessageHistory extends BaseMessageHistory {

  /** Default distance threshold for semantic search. */
  public static final double DEFAULT_DISTANCE_THRESHOLD = 0.3;

  private final SearchIndex index;
  private final UnifiedJedis redisClient;
  private final BaseVectorizer vectorizer;
  private final Filter defaultSessionFilter;
  private double distanceThreshold;

  /**
   * Initialize semantic message history with required parameters.
   *
   * @param name The name of the message history index
   * @param vectorizer The vectorizer used to create embeddings
   * @param redisClient A Jedis client instance
   */
  public SemanticMessageHistory(String name, BaseVectorizer vectorizer, UnifiedJedis redisClient) {
    this(name, null, null, vectorizer, DEFAULT_DISTANCE_THRESHOLD, redisClient, false);
  }

  /**
   * Initialize semantic message history with session tag and prefix.
   *
   * @param name The name of the message history index
   * @param sessionTag Tag to be added to entries to link to a specific conversation session.
   *     Defaults to instance ULID.
   * @param prefix Prefix for the keys for this conversation data. Defaults to the index name.
   * @param vectorizer The vectorizer used to create embeddings
   * @param redisClient A Jedis client instance
   */
  public SemanticMessageHistory(
      String name,
      String sessionTag,
      String prefix,
      BaseVectorizer vectorizer,
      UnifiedJedis redisClient) {
    this(name, sessionTag, prefix, vectorizer, DEFAULT_DISTANCE_THRESHOLD, redisClient, false);
  }

  /**
   * Initialize semantic message history with all parameters.
   *
   * @param name The name of the message history index
   * @param sessionTag Tag to be added to entries to link to a specific conversation session.
   *     Defaults to instance ULID.
   * @param prefix Prefix for the keys for this conversation data. Defaults to the index name.
   * @param vectorizer The vectorizer used to create embeddings
   * @param distanceThreshold The maximum semantic distance for results. Defaults to 0.3.
   * @param redisClient A Jedis client instance
   * @param overwrite Whether to overwrite existing index schema. Defaults to false.
   */
  public SemanticMessageHistory(
      String name,
      String sessionTag,
      String prefix,
      BaseVectorizer vectorizer,
      double distanceThreshold,
      UnifiedJedis redisClient,
      boolean overwrite) {
    super(name, sessionTag);

    if (vectorizer == null) {
      throw new IllegalArgumentException("Vectorizer cannot be null");
    }

    String keyPrefix = (prefix != null) ? prefix : name;
    this.vectorizer = vectorizer;
    this.distanceThreshold = distanceThreshold;
    this.redisClient = redisClient;

    // Create schema with vector field
    IndexSchema schema =
        SemanticMessageHistorySchema.fromParams(
            name, keyPrefix, vectorizer.getDimensions(), vectorizer.getDataType());

    this.index = new SearchIndex(schema, redisClient);
    this.index.create(overwrite);

    this.defaultSessionFilter = Filter.tag(SESSION_FIELD_NAME, this.sessionTag);
  }

  /**
   * Get the distance threshold for semantic search.
   *
   * @return The current distance threshold
   */
  public double getDistanceThreshold() {
    return distanceThreshold;
  }

  /**
   * Set the distance threshold for semantic search.
   *
   * @param distanceThreshold The new distance threshold
   */
  public void setDistanceThreshold(double distanceThreshold) {
    this.distanceThreshold = distanceThreshold;
  }

  /**
   * Get the vectorizer used for embeddings.
   *
   * @return The vectorizer
   */
  public BaseVectorizer getVectorizer() {
    return vectorizer;
  }

  /**
   * Get the underlying search index.
   *
   * @return The search index
   */
  public SearchIndex getIndex() {
    return index;
  }

  @Override
  public void clear() {
    index.clear();
  }

  @Override
  public void delete() {
    index.delete(true);
  }

  @Override
  public void drop(String id) {
    if (id == null) {
      // Get the most recent message
      List<Map<String, Object>> recent = getRecent(1, false, true, null, null);
      if (!recent.isEmpty()) {
        id = (String) recent.get(0).get(ID_FIELD_NAME);
      } else {
        return; // Nothing to drop
      }
    }

    redisClient.del(index.key(id));
  }

  @Override
  public List<Map<String, Object>> getMessages() {
    List<String> returnFields =
        List.of(
            ID_FIELD_NAME,
            SESSION_FIELD_NAME,
            ROLE_FIELD_NAME,
            CONTENT_FIELD_NAME,
            TOOL_FIELD_NAME,
            TIMESTAMP_FIELD_NAME,
            METADATA_FIELD_NAME);

    FilterQuery query =
        FilterQuery.builder()
            .filterExpression(defaultSessionFilter)
            .returnFields(returnFields)
            .sortBy(TIMESTAMP_FIELD_NAME) // ascending by default
            .build();

    List<Map<String, Object>> messages = index.query(query);

    return formatContext(messages, false);
  }

  /**
   * Retrieve the recent conversation history in sequential order.
   *
   * @param topK The number of previous messages to return
   * @param asText Whether to return as text strings or maps
   * @param raw Whether to return full Redis hash entries
   * @param sessionTag Session tag to filter by
   * @return List of messages
   */
  public <T> List<T> getRecent(int topK, boolean asText, boolean raw, String sessionTag) {
    return getRecent(topK, asText, raw, sessionTag, null);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> List<T> getRecent(
      int topK, boolean asText, boolean raw, String sessionTag, Object role) {
    // Validate topK
    if (topK < 0) {
      throw new IllegalArgumentException("topK must be an integer greater than or equal to 0");
    }

    if (topK == 0) {
      return new ArrayList<>();
    }

    // Validate and normalize role parameter
    List<String> rolesToFilter = validateRoles(role);

    List<String> returnFields =
        List.of(
            ID_FIELD_NAME,
            SESSION_FIELD_NAME,
            ROLE_FIELD_NAME,
            CONTENT_FIELD_NAME,
            TOOL_FIELD_NAME,
            TIMESTAMP_FIELD_NAME,
            METADATA_FIELD_NAME);

    Filter sessionFilter =
        (sessionTag != null) ? Filter.tag(SESSION_FIELD_NAME, sessionTag) : defaultSessionFilter;

    // Combine session filter with role filter if provided
    Filter filterExpression = sessionFilter;
    if (rolesToFilter != null) {
      filterExpression = combineWithRoleFilter(sessionFilter, rolesToFilter);
    }

    FilterQuery query =
        FilterQuery.builder()
            .filterExpression(filterExpression)
            .returnFields(returnFields)
            .numResults(topK)
            .sortBy(TIMESTAMP_FIELD_NAME)
            .sortAscending(false) // Python uses asc=False, then reverses
            .build();

    List<Map<String, Object>> messages = index.query(query);

    // Reverse to get chronological order
    List<Map<String, Object>> reversed = new ArrayList<>(messages);
    java.util.Collections.reverse(reversed);

    if (raw) {
      return (List<T>) reversed;
    }

    return formatContext(reversed, asText);
  }

  /**
   * Search the message history for information semantically related to the specified prompt.
   *
   * <p>This method uses vector similarity search with a text prompt as input. It checks for
   * semantically similar prompts and responses and gets the top k most relevant previous prompts or
   * responses to include as context to the next LLM call.
   *
   * @param prompt The message text to search for in message history
   * @param asText Whether to return as text strings or maps
   * @param topK The number of previous messages to return (default is 5)
   * @param fallBack Whether to fall back to recent conversation history if no relevant context
   * @param sessionTag Tag of the entries linked to a specific conversation session
   * @param distanceThreshold The threshold for semantic vector distance (null uses instance
   *     threshold)
   * @param role Filter messages by role(s) - single string or List of strings
   * @return List of semantically relevant messages
   * @throws IllegalArgumentException if topK is negative or role contains invalid values
   */
  @SuppressWarnings("unchecked")
  public <T> List<T> getRelevant(
      String prompt,
      boolean asText,
      int topK,
      boolean fallBack,
      String sessionTag,
      Double distanceThreshold,
      Object role) {

    // Validate topK
    if (topK < 0) {
      throw new IllegalArgumentException("topK must be an integer greater than or equal to 0");
    }
    if (topK == 0) {
      return new ArrayList<>();
    }

    // Validate and normalize role parameter
    List<String> rolesToFilter = validateRoles(role);

    // Use instance threshold if not overridden
    double threshold = (distanceThreshold != null) ? distanceThreshold : this.distanceThreshold;

    List<String> returnFields =
        List.of(
            SESSION_FIELD_NAME,
            ROLE_FIELD_NAME,
            CONTENT_FIELD_NAME,
            TIMESTAMP_FIELD_NAME,
            TOOL_FIELD_NAME,
            METADATA_FIELD_NAME);

    // Build session filter
    Filter sessionFilter =
        (sessionTag != null) ? Filter.tag(SESSION_FIELD_NAME, sessionTag) : defaultSessionFilter;

    // Combine with role filter if provided
    Filter filterExpression = sessionFilter;
    if (rolesToFilter != null) {
      filterExpression = combineWithRoleFilter(sessionFilter, rolesToFilter);
    }

    // Generate embedding for the search prompt
    float[] promptVector = vectorizer.embed(prompt);

    // Build the vector query with preFilter for session/role filtering
    // Using VectorQuery instead of VectorRangeQuery because VectorQuery supports preFilter
    VectorQuery vectorQuery =
        VectorQuery.builder()
            .vector(promptVector)
            .field(MESSAGE_VECTOR_FIELD_NAME)
            .numResults(topK * 2) // Request more results to account for threshold filtering
            .returnScore(true)
            .returnDistance(true)
            .preFilter(filterExpression.build())
            .returnFields(returnFields)
            .build();

    // Execute the query and filter by distance threshold
    List<Map<String, Object>> allResults = index.query(vectorQuery);

    // Filter results by distance threshold (simulating VectorRangeQuery behavior)
    List<Map<String, Object>> messages = new ArrayList<>();
    for (Map<String, Object> doc : allResults) {
      // Check both "distance" and "vector_distance" for compatibility
      Object distanceObj = doc.getOrDefault("distance", doc.get("vector_distance"));
      if (distanceObj != null) {
        double distance = Double.parseDouble(distanceObj.toString());
        if (distance <= threshold && messages.size() < topK) {
          messages.add(doc);
        }
      } else {
        // If no distance, include the result (shouldn't happen but be safe)
        if (messages.size() < topK) {
          messages.add(doc);
        }
      }
    }

    // If no semantic matches and fallback is enabled, return recent messages
    if (messages.isEmpty() && fallBack) {
      return getRecent(topK, asText, false, sessionTag, role);
    }

    if (asText) {
      List<String> textResults = new ArrayList<>();
      for (Map<String, Object> msg : messages) {
        textResults.add((String) msg.get(CONTENT_FIELD_NAME));
      }
      return (List<T>) textResults;
    }

    return formatContext(messages, asText);
  }

  @Override
  public void store(String prompt, String response, String sessionTag) {
    List<Map<String, String>> messages = new ArrayList<>();

    Map<String, String> userMessage = new HashMap<>();
    userMessage.put(ROLE_FIELD_NAME, "user");
    userMessage.put(CONTENT_FIELD_NAME, prompt);
    messages.add(userMessage);

    Map<String, String> llmMessage = new HashMap<>();
    llmMessage.put(ROLE_FIELD_NAME, "llm");
    llmMessage.put(CONTENT_FIELD_NAME, response);
    messages.add(llmMessage);

    addMessages(messages, sessionTag);
  }

  /**
   * Insert a prompt:response pair into the message history using the default session tag.
   *
   * @param prompt The user prompt to the LLM
   * @param response The corresponding LLM response
   */
  public void store(String prompt, String response) {
    store(prompt, response, null);
  }

  @Override
  public void addMessages(List<Map<String, String>> messages, String sessionTag) {
    String effectiveSessionTag = (sessionTag != null) ? sessionTag : this.sessionTag;
    List<Map<String, Object>> chatMessages = new ArrayList<>();

    for (Map<String, String> message : messages) {
      String content = message.get(CONTENT_FIELD_NAME);

      // Generate embedding for the content
      float[] contentVector = vectorizer.embed(content);

      // Build chat message with vector
      SemanticChatMessage.SemanticChatMessageBuilder builder =
          SemanticChatMessage.builder()
              .role(message.get(ROLE_FIELD_NAME))
              .content(content)
              .sessionTag(effectiveSessionTag)
              .vectorField(contentVector);

      if (message.containsKey(TOOL_FIELD_NAME)) {
        builder.toolCallId(message.get(TOOL_FIELD_NAME));
      }

      if (message.containsKey(METADATA_FIELD_NAME)) {
        builder.metadata(message.get(METADATA_FIELD_NAME));
      }

      SemanticChatMessage chatMessage = builder.build();
      chatMessages.add(chatMessage.toDict(vectorizer.getDataType()));
    }

    index.load(chatMessages, ID_FIELD_NAME);
  }

  @Override
  public void addMessage(Map<String, String> message, String sessionTag) {
    addMessages(List.of(message), sessionTag);
  }

  /**
   * Insert a single prompt or response into the message history using the default session tag.
   *
   * @param message The user prompt or LLM response
   */
  public void addMessage(Map<String, String> message) {
    addMessage(message, null);
  }

  /**
   * Insert a list of prompts and responses into the message history using the default session tag.
   *
   * @param messages The list of user prompts and LLM responses
   */
  public void addMessages(List<Map<String, String>> messages) {
    addMessages(messages, null);
  }

  /**
   * Combine a session filter with role filters.
   *
   * @param sessionFilter The session filter
   * @param roles The list of roles to filter by
   * @return Combined filter expression
   */
  private Filter combineWithRoleFilter(Filter sessionFilter, List<String> roles) {
    if (roles.size() == 1) {
      Filter roleFilter = Filter.tag(ROLE_FIELD_NAME, roles.get(0));
      return Filter.and(sessionFilter, roleFilter);
    } else {
      // Multiple roles - use OR logic
      Filter roleFilter = Filter.tag(ROLE_FIELD_NAME, roles.get(0));
      for (int i = 1; i < roles.size(); i++) {
        roleFilter = Filter.or(roleFilter, Filter.tag(ROLE_FIELD_NAME, roles.get(i)));
      }
      return Filter.and(sessionFilter, roleFilter);
    }
  }

  /**
   * Format messages with metadata deserialization support.
   *
   * @param messages The raw messages from Redis
   * @param asText Whether to return as text
   * @return Formatted messages
   */
  @Override
  @SuppressWarnings("unchecked")
  protected <T> List<T> formatContext(List<Map<String, Object>> messages, boolean asText) {
    List<T> context = new ArrayList<>();

    for (Map<String, Object> message : messages) {
      if (asText) {
        context.add((T) message.get(CONTENT_FIELD_NAME));
      } else {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put(ROLE_FIELD_NAME, message.get(ROLE_FIELD_NAME));
        formatted.put(CONTENT_FIELD_NAME, message.get(CONTENT_FIELD_NAME));

        // Include tool_call_id if present
        if (message.get(TOOL_FIELD_NAME) != null) {
          formatted.put(TOOL_FIELD_NAME, message.get(TOOL_FIELD_NAME));
        }

        // Include metadata if present
        if (message.get(METADATA_FIELD_NAME) != null) {
          formatted.put(METADATA_FIELD_NAME, message.get(METADATA_FIELD_NAME));
        }

        context.add((T) formatted);
      }
    }

    return context;
  }
}
