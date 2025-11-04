package com.redis.vl.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.UnifiedJedis;

/**
 * LangChain4J ChatMemoryStore implementation using Redis as the backend.
 *
 * <p>This store enables persistent conversation history storage in Redis, allowing chat sessions to
 * be resumed across application restarts and supporting multi-user scenarios.
 *
 * <p>Messages are stored as JSON in Redis Lists, with each session having its own list key. This
 * provides efficient append and retrieval operations.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Create chat memory store
 * UnifiedJedis jedis = new JedisPooled("localhost", 6379);
 * ChatMemoryStore memoryStore = new RedisVLChatMemoryStore(jedis);
 *
 * // Use with LangChain4J chat memory
 * ChatMemory chatMemory = MessageWindowChatMemory.builder()
 *     .id("user-123")
 *     .maxMessages(10)
 *     .chatMemoryStore(memoryStore)
 *     .build();
 *
 * // Or with more control
 * ChatMemoryStore store = RedisVLChatMemoryStore.builder()
 *     .jedis(jedis)
 *     .keyPrefix("chat:history:")
 *     .ttlSeconds(3600)
 *     .build();
 * }</pre>
 */
public class RedisVLChatMemoryStore implements ChatMemoryStore {

  private final UnifiedJedis jedis;
  private final String keyPrefix;
  private final Integer ttlSeconds;
  private final ObjectMapper objectMapper;

  /**
   * Creates a new RedisVLChatMemoryStore with default prefix.
   *
   * @param jedis The Redis client
   */
  public RedisVLChatMemoryStore(UnifiedJedis jedis) {
    this(jedis, "langchain4j:chat:memory:", null);
  }

  /**
   * Creates a new RedisVLChatMemoryStore with custom prefix.
   *
   * @param jedis The Redis client
   * @param keyPrefix The key prefix for Redis keys
   */
  public RedisVLChatMemoryStore(UnifiedJedis jedis, String keyPrefix) {
    this(jedis, keyPrefix, null);
  }

  /**
   * Creates a new RedisVLChatMemoryStore with custom prefix and TTL.
   *
   * @param jedis The Redis client
   * @param keyPrefix The key prefix for Redis keys
   * @param ttlSeconds Optional TTL for conversation history (null for no expiration)
   */
  public RedisVLChatMemoryStore(UnifiedJedis jedis, String keyPrefix, Integer ttlSeconds) {
    this.jedis = jedis;
    this.keyPrefix = keyPrefix != null ? keyPrefix : "langchain4j:chat:memory:";
    this.ttlSeconds = ttlSeconds;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public List<ChatMessage> getMessages(Object memoryId) {
    if (memoryId == null) {
      throw new IllegalArgumentException("Memory ID cannot be null");
    }

    String key = makeKey(memoryId.toString());
    List<String> jsonMessages = jedis.lrange(key, 0, -1);

    List<ChatMessage> messages = new ArrayList<>();
    for (String json : jsonMessages) {
      try {
        ChatMessage message = ChatMessageDeserializer.messageFromJson(json);
        messages.add(message);
      } catch (Exception e) {
        System.err.println("Warning: Failed to deserialize chat message: " + e.getMessage());
        // Continue with other messages
      }
    }

    return messages;
  }

  @Override
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    if (memoryId == null) {
      throw new IllegalArgumentException("Memory ID cannot be null");
    }
    if (messages == null) {
      messages = new ArrayList<>();
    }

    String key = makeKey(memoryId.toString());

    // Delete existing messages
    jedis.del(key);

    // Store new messages
    if (!messages.isEmpty()) {
      String[] jsonMessages =
          messages.stream().map(ChatMessageSerializer::messageToJson).toArray(String[]::new);

      jedis.rpush(key, jsonMessages);

      // Set TTL if configured
      if (ttlSeconds != null && ttlSeconds > 0) {
        jedis.expire(key, ttlSeconds);
      }
    }
  }

  @Override
  public void deleteMessages(Object memoryId) {
    if (memoryId == null) {
      throw new IllegalArgumentException("Memory ID cannot be null");
    }

    String key = makeKey(memoryId.toString());
    jedis.del(key);
  }

  /**
   * Creates the Redis key for a memory ID.
   *
   * @param memoryId The memory ID
   * @return The full Redis key
   */
  private String makeKey(String memoryId) {
    return keyPrefix + memoryId;
  }

  /**
   * Gets the underlying Jedis client.
   *
   * @return The Redis client
   */
  public UnifiedJedis getJedis() {
    return jedis;
  }

  /**
   * Gets the key prefix.
   *
   * @return The key prefix
   */
  public String getKeyPrefix() {
    return keyPrefix;
  }

  /**
   * Creates a builder for RedisVLChatMemoryStore.
   *
   * @return A new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for RedisVLChatMemoryStore. */
  public static class Builder {
    private UnifiedJedis jedis;
    private String keyPrefix = "langchain4j:chat:memory:";
    private Integer ttlSeconds;

    /**
     * Sets the Redis client.
     *
     * @param jedis The Jedis client
     * @return This builder
     */
    public Builder jedis(UnifiedJedis jedis) {
      this.jedis = jedis;
      return this;
    }

    /**
     * Sets the key prefix.
     *
     * @param keyPrefix The key prefix
     * @return This builder
     */
    public Builder keyPrefix(String keyPrefix) {
      this.keyPrefix = keyPrefix;
      return this;
    }

    /**
     * Sets the TTL for conversation history.
     *
     * @param ttlSeconds TTL in seconds (null for no expiration)
     * @return This builder
     */
    public Builder ttlSeconds(Integer ttlSeconds) {
      this.ttlSeconds = ttlSeconds;
      return this;
    }

    /**
     * Builds the chat memory store.
     *
     * @return A new RedisVLChatMemoryStore
     */
    public RedisVLChatMemoryStore build() {
      if (jedis == null) {
        throw new IllegalArgumentException("Jedis client is required");
      }
      return new RedisVLChatMemoryStore(jedis, keyPrefix, ttlSeconds);
    }
  }
}
