package com.redis.vl.langchain4j;

import java.util.*;
import java.util.stream.Collectors;
import redis.clients.jedis.UnifiedJedis;

/**
 * RedisVL Document Store for storing raw binary content (images, PDFs, etc.) for multimodal RAG.
 *
 * <p>This store enables persistent storage of raw documents with metadata, separate from vector
 * embeddings. Useful for multimodal RAG where text summaries are embedded for search, but raw
 * images/content is needed for vision LLM generation.
 *
 * <p>Documents are stored as Redis Hashes with:
 *
 * <ul>
 *   <li>content: Base64-encoded binary content
 *   <li>metadata_*: Individual metadata fields
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Create document store
 * UnifiedJedis jedis = new JedisPooled("localhost", 6379);
 * RedisVLDocumentStore store = new RedisVLDocumentStore(jedis, "docs:");
 *
 * // Store a document
 * byte[] imageBytes = Files.readAllBytes(Path.of("image.jpg"));
 * Map<String, String> metadata = Map.of("type", "image", "source", "page1.pdf");
 * store.store("doc_id_1", imageBytes, metadata);
 *
 * // Retrieve document
 * Optional<Document> doc = store.retrieve("doc_id_1");
 * if (doc.isPresent()) {
 *   byte[] content = doc.get().content();
 *   Map<String, String> meta = doc.get().metadata();
 *   // Use with vision LLM
 * }
 *
 * // Builder pattern
 * RedisVLDocumentStore store = RedisVLDocumentStore.builder()
 *     .jedis(jedis)
 *     .keyPrefix("docs:")
 *     .build();
 * }</pre>
 */
public class RedisVLDocumentStore {

  private final UnifiedJedis jedis;
  private final String keyPrefix;

  private static final String CONTENT_FIELD = "content";
  private static final String METADATA_PREFIX = "metadata_";

  /**
   * Creates a new RedisVLDocumentStore with default prefix.
   *
   * @param jedis The Redis client
   */
  public RedisVLDocumentStore(UnifiedJedis jedis) {
    this(jedis, "redisvl:documents:");
  }

  /**
   * Creates a new RedisVLDocumentStore with custom prefix.
   *
   * @param jedis The Redis client
   * @param keyPrefix The key prefix for Redis keys
   */
  public RedisVLDocumentStore(UnifiedJedis jedis, String keyPrefix) {
    this.jedis = jedis;
    this.keyPrefix = keyPrefix != null ? keyPrefix : "redisvl:documents:";
  }

  /**
   * Stores a document with metadata.
   *
   * @param id Unique document identifier
   * @param content Binary content
   * @param metadata Optional metadata (can be null)
   * @throws IllegalArgumentException if id or content is null
   */
  public void store(String id, byte[] content, Map<String, String> metadata) {
    if (id == null) {
      throw new IllegalArgumentException("Document ID cannot be null");
    }
    if (content == null) {
      throw new IllegalArgumentException("Document content cannot be null");
    }

    String key = makeKey(id);

    // Store content as base64-encoded string
    String encodedContent = Base64.getEncoder().encodeToString(content);
    Map<String, String> fields = new HashMap<>();
    fields.put(CONTENT_FIELD, encodedContent);

    // Store metadata with prefix
    if (metadata != null) {
      metadata.forEach((k, v) -> fields.put(METADATA_PREFIX + k, v));
    }

    jedis.hset(key, fields);
  }

  /**
   * Retrieves a document by ID.
   *
   * @param id Document identifier
   * @return Optional containing the document if found
   */
  public Optional<Document> retrieve(String id) {
    if (id == null) {
      return Optional.empty();
    }

    String key = makeKey(id);
    Map<String, String> fields = jedis.hgetAll(key);

    if (fields.isEmpty()) {
      return Optional.empty();
    }

    // Extract content
    String encodedContent = fields.get(CONTENT_FIELD);
    if (encodedContent == null) {
      return Optional.empty();
    }
    byte[] content = Base64.getDecoder().decode(encodedContent);

    // Extract metadata
    Map<String, String> metadata = new HashMap<>();
    fields.forEach(
        (k, v) -> {
          if (k.startsWith(METADATA_PREFIX)) {
            String metaKey = k.substring(METADATA_PREFIX.length());
            metadata.put(metaKey, v);
          }
        });

    return Optional.of(new Document(content, metadata));
  }

  /**
   * Deletes a document by ID.
   *
   * @param id Document identifier
   * @return true if document was deleted, false if it didn't exist
   */
  public boolean delete(String id) {
    if (id == null) {
      return false;
    }

    String key = makeKey(id);
    Long deleted = jedis.del(key);
    return deleted != null && deleted > 0;
  }

  /**
   * Lists all document IDs in this store.
   *
   * @return List of document IDs
   */
  public List<String> listDocumentIds() {
    Set<String> keys = jedis.keys(keyPrefix + "*");
    return keys.stream().map(key -> key.substring(keyPrefix.length())).collect(Collectors.toList());
  }

  /**
   * Creates the Redis key for a document ID.
   *
   * @param id The document ID
   * @return The full Redis key
   */
  private String makeKey(String id) {
    return keyPrefix + id;
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
   * Creates a builder for RedisVLDocumentStore.
   *
   * @return A new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for RedisVLDocumentStore. */
  public static class Builder {
    private UnifiedJedis jedis;
    private String keyPrefix = "redisvl:documents:";

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
     * Builds the document store.
     *
     * @return A new RedisVLDocumentStore
     */
    public RedisVLDocumentStore build() {
      if (jedis == null) {
        throw new IllegalArgumentException("Jedis client is required");
      }
      return new RedisVLDocumentStore(jedis, keyPrefix);
    }
  }

  /**
   * Represents a document with content and metadata.
   *
   * @param content Binary content
   * @param metadata Document metadata
   */
  public record Document(byte[] content, Map<String, String> metadata) {
    public Document {
      // Defensive copy for metadata
      metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Document document)) return false;
      return Arrays.equals(content, document.content)
          && Objects.equals(metadata, document.metadata);
    }

    @Override
    public int hashCode() {
      return Objects.hash(Arrays.hashCode(content), metadata);
    }

    @Override
    public String toString() {
      return "Document{" + "content.length=" + content.length + ", metadata=" + metadata + '}';
    }
  }
}
