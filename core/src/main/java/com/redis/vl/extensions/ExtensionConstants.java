package com.redis.vl.extensions;

/** Constants used within the extension classes. */
public final class ExtensionConstants {

  // BaseMessageHistory
  public static final String ID_FIELD_NAME = "entry_id";
  public static final String ROLE_FIELD_NAME = "role";
  public static final String CONTENT_FIELD_NAME = "content";
  public static final String TOOL_FIELD_NAME = "tool_call_id";
  public static final String TIMESTAMP_FIELD_NAME = "timestamp";
  public static final String SESSION_FIELD_NAME = "session_tag";

  // SemanticMessageHistory
  public static final String MESSAGE_VECTOR_FIELD_NAME = "vector_field";

  // SemanticCache
  public static final String REDIS_KEY_FIELD_NAME = "key";
  public static final String ENTRY_ID_FIELD_NAME = "entry_id";
  public static final String PROMPT_FIELD_NAME = "prompt";
  public static final String RESPONSE_FIELD_NAME = "response";
  public static final String CACHE_VECTOR_FIELD_NAME = "prompt_vector";
  public static final String INSERTED_AT_FIELD_NAME = "inserted_at";
  public static final String UPDATED_AT_FIELD_NAME = "updated_at";
  public static final String METADATA_FIELD_NAME = "metadata"; // also used in MessageHistory

  // EmbeddingsCache
  public static final String TEXT_FIELD_NAME = "text";
  public static final String MODEL_NAME_FIELD_NAME = "model_name";
  public static final String EMBEDDING_FIELD_NAME = "embedding";
  public static final String DIMENSIONS_FIELD_NAME = "dimensions";

  // SemanticRouter
  public static final String ROUTE_VECTOR_FIELD_NAME = "vector";

  private ExtensionConstants() {
    // Prevent instantiation
  }
}
