package com.redis.vl.extensions.messagehistory;

import static com.redis.vl.extensions.Constants.*;

import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.VectorField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

/**
 * Schema for semantic message history index with vector field for semantic search.
 *
 * <p>Matches the Python SemanticMessageHistorySchema from redisvl.extensions.message_history.schema
 */
public class SemanticMessageHistorySchema {

  /**
   * Creates an IndexSchema for semantic message history with standard fields plus vector field.
   *
   * @param name The name of the index
   * @param prefix The key prefix for stored messages
   * @param vectorizerDims The dimension of the vector embeddings
   * @param dtype The data type for vectors (e.g., "float32")
   * @return IndexSchema configured for semantic message history
   */
  public static IndexSchema fromParams(
      String name, String prefix, int vectorizerDims, String dtype) {
    // Map Python dtype strings to Java VectorField.VectorDataType
    VectorField.VectorDataType dataType = mapDataType(dtype);

    return IndexSchema.builder()
        .name(name)
        .prefix(prefix)
        .storageType(IndexSchema.StorageType.HASH)
        // Standard message fields (same as MessageHistorySchema)
        .addTagField(ROLE_FIELD_NAME, tagField -> {})
        .addTextField(CONTENT_FIELD_NAME, textField -> {})
        .addTagField(TOOL_FIELD_NAME, tagField -> {})
        .addNumericField(TIMESTAMP_FIELD_NAME, numericField -> {})
        .addTagField(SESSION_FIELD_NAME, tagField -> {})
        .addTextField(METADATA_FIELD_NAME, textField -> {})
        // Vector field for semantic search (Python: flat algorithm, cosine distance)
        .addVectorField(
            MESSAGE_VECTOR_FIELD_NAME,
            vectorizerDims,
            vectorField ->
                vectorField
                    .algorithm(VectorAlgorithm.FLAT)
                    .distanceMetric(VectorField.DistanceMetric.COSINE)
                    .dataType(dataType))
        .build();
  }

  /**
   * Map Python-style dtype strings to Java VectorDataType enum.
   *
   * @param dtype The dtype string (e.g., "float32", "float16", "FLOAT32")
   * @return The corresponding VectorDataType
   */
  private static VectorField.VectorDataType mapDataType(String dtype) {
    if (dtype == null) {
      return VectorField.VectorDataType.FLOAT32;
    }

    return switch (dtype.toLowerCase()) {
      case "float16" -> VectorField.VectorDataType.FLOAT16;
      case "float64" -> VectorField.VectorDataType.FLOAT64;
      case "bfloat16" -> VectorField.VectorDataType.BFLOAT16;
      default -> VectorField.VectorDataType.FLOAT32;
    };
  }

  private SemanticMessageHistorySchema() {
    // Prevent instantiation
  }
}
