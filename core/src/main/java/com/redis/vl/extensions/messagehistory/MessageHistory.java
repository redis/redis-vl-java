package com.redis.vl.extensions.messagehistory;

import static com.redis.vl.extensions.Constants.*;

import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.Filter;
import com.redis.vl.query.FilterQuery;
import com.redis.vl.schema.IndexSchema;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.UnifiedJedis;

/**
 * Message History for storing and retrieving LLM conversation history.
 *
 * <p>Stores user prompts and LLM responses to allow for enriching future prompts with session
 * context. Messages are tagged by session to support multiple concurrent conversations.
 *
 * <p>Matches the Python MessageHistory from redisvl.extensions.message_history.message_history
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "SearchIndex and UnifiedJedis are intentionally shared mutable objects")
public class MessageHistory extends BaseMessageHistory {

  private final SearchIndex index;
  private final UnifiedJedis redisClient;
  private final Filter defaultSessionFilter;

  /**
   * Initialize message history.
   *
   * @param name The name of the message history index
   * @param redisClient A Jedis client instance
   */
  public MessageHistory(String name, UnifiedJedis redisClient) {
    this(name, null, null, redisClient);
  }

  /**
   * Initialize message history.
   *
   * @param name The name of the message history index
   * @param sessionTag Tag to be added to entries to link to a specific conversation session.
   *     Defaults to instance ULID.
   * @param prefix Prefix for the keys for this conversation data. Defaults to the index name.
   * @param redisClient A Jedis client instance
   */
  public MessageHistory(String name, String sessionTag, String prefix, UnifiedJedis redisClient) {
    super(name, sessionTag);

    String keyPrefix = (prefix != null) ? prefix : name;

    IndexSchema schema = MessageHistorySchema.fromParams(name, keyPrefix);

    this.redisClient = redisClient;
    this.index = new SearchIndex(schema, redisClient);
    this.index.create(false); // don't overwrite existing

    this.defaultSessionFilter = Filter.tag(SESSION_FIELD_NAME, this.sessionTag);
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
      List<Map<String, Object>> recent = getRecent(1, false, true, null);
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
            TIMESTAMP_FIELD_NAME);

    FilterQuery query =
        FilterQuery.builder()
            .filterExpression(defaultSessionFilter)
            .returnFields(returnFields)
            .sortBy(TIMESTAMP_FIELD_NAME) // ascending by default
            .build();

    List<Map<String, Object>> messages = index.query(query);

    return formatContext(messages, false);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> List<T> getRecent(int topK, boolean asText, boolean raw, String sessionTag) {
    // Validate topK
    if (topK < 0) {
      throw new IllegalArgumentException("topK must be an integer greater than or equal to 0");
    }

    List<String> returnFields =
        List.of(
            ID_FIELD_NAME,
            SESSION_FIELD_NAME,
            ROLE_FIELD_NAME,
            CONTENT_FIELD_NAME,
            TOOL_FIELD_NAME,
            TIMESTAMP_FIELD_NAME);

    Filter sessionFilter =
        (sessionTag != null) ? Filter.tag(SESSION_FIELD_NAME, sessionTag) : defaultSessionFilter;

    FilterQuery query =
        FilterQuery.builder()
            .filterExpression(sessionFilter)
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
      ChatMessage.ChatMessageBuilder builder =
          ChatMessage.builder()
              .role(message.get(ROLE_FIELD_NAME))
              .content(message.get(CONTENT_FIELD_NAME))
              .sessionTag(effectiveSessionTag);

      if (message.containsKey(TOOL_FIELD_NAME)) {
        builder.toolCallId(message.get(TOOL_FIELD_NAME));
      }

      ChatMessage chatMessage = builder.build();
      chatMessages.add(chatMessage.toDict());
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

  public SearchIndex getIndex() {
    return index;
  }
}
