package com.redis.vl.extensions;

/** Constants used within the extension classes. */
public final class ExtensionConstants {

  // BaseMessageHistory
  /** Field name for message entry ID. */
  public static final String ID_FIELD_NAME = "entry_id";

  /** Field name for message role (e.g., user, assistant, system). */
  public static final String ROLE_FIELD_NAME = "role";

  /** Field name for message content. */
  public static final String CONTENT_FIELD_NAME = "content";

  /** Field name for tool call ID. */
  public static final String TOOL_FIELD_NAME = "tool_call_id";

  /** Field name for message timestamp. */
  public static final String TIMESTAMP_FIELD_NAME = "timestamp";

  /** Field name for session tag. */
  public static final String SESSION_FIELD_NAME = "session_tag";

  // SemanticMessageHistory
  /** Field name for message vector field in semantic message history. */
  public static final String MESSAGE_VECTOR_FIELD_NAME = "vector_field";

  // SemanticCache
  /** Field name for Redis key in semantic cache. */
  public static final String REDIS_KEY_FIELD_NAME = "key";

  /** Field name for cache entry ID. */
  public static final String ENTRY_ID_FIELD_NAME = "entry_id";

  /** Field name for prompt text in cache. */
  public static final String PROMPT_FIELD_NAME = "prompt";

  /** Field name for response text in cache. */
  public static final String RESPONSE_FIELD_NAME = "response";

  /** Field name for prompt vector in cache. */
  public static final String CACHE_VECTOR_FIELD_NAME = "prompt_vector";

  /** Field name for cache insertion timestamp. */
  public static final String INSERTED_AT_FIELD_NAME = "inserted_at";

  /** Field name for cache update timestamp. */
  public static final String UPDATED_AT_FIELD_NAME = "updated_at";

  /** Field name for metadata (used in both MessageHistory and SemanticCache). */
  public static final String METADATA_FIELD_NAME = "metadata";

  // EmbeddingsCache
  /** Field name for text in embeddings cache. */
  public static final String TEXT_FIELD_NAME = "text";

  /** Field name for model name in embeddings cache. */
  public static final String MODEL_NAME_FIELD_NAME = "model_name";

  /** Field name for embedding vector. */
  public static final String EMBEDDING_FIELD_NAME = "embedding";

  /** Field name for embedding dimensions. */
  public static final String DIMENSIONS_FIELD_NAME = "dimensions";

  // SemanticRouter
  /** Field name for route vector in semantic router. */
  public static final String ROUTE_VECTOR_FIELD_NAME = "vector";

  private ExtensionConstants() {
    // Prevent instantiation
  }
}
