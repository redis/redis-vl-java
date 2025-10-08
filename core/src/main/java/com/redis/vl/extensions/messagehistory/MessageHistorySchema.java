package com.redis.vl.extensions.messagehistory;

import static com.redis.vl.extensions.Constants.*;

import com.redis.vl.schema.IndexSchema;

/**
 * Schema for message history index.
 *
 * <p>Matches the Python MessageHistorySchema from redisvl.extensions.message_history.schema
 */
public class MessageHistorySchema {

  /**
   * Creates an IndexSchema for message history with the standard fields.
   *
   * @param name The name of the index
   * @param prefix The key prefix for stored messages
   * @return IndexSchema configured for message history
   */
  public static IndexSchema fromParams(String name, String prefix) {
    return IndexSchema.builder()
        .name(name)
        .prefix(prefix)
        .storageType(IndexSchema.StorageType.HASH)
        .addTagField(ROLE_FIELD_NAME, tagField -> {})
        .addTextField(CONTENT_FIELD_NAME, textField -> {})
        .addTagField(TOOL_FIELD_NAME, tagField -> {})
        .addNumericField(TIMESTAMP_FIELD_NAME, numericField -> {})
        .addTagField(SESSION_FIELD_NAME, tagField -> {})
        .build();
  }

  private MessageHistorySchema() {
    // Prevent instantiation
  }
}
