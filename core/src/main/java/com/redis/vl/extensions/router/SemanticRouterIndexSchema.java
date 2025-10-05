package com.redis.vl.extensions.router;

import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.TagField;
import com.redis.vl.schema.TextField;
import com.redis.vl.schema.VectorField;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Customized index schema for SemanticRouter. Ported from Python:
 * redisvl/extensions/router/schema.py:86
 */
public class SemanticRouterIndexSchema {

  /** Constant for route vector field name */
  public static final String ROUTE_VECTOR_FIELD_NAME = "vector";

  /**
   * Private constructor to prevent instantiation of utility class.
   *
   * @throws UnsupportedOperationException always thrown to prevent instantiation
   */
  @SuppressFBWarnings(
      value = "CT_CONSTRUCTOR_THROW",
      justification = "Utility class should never be instantiated")
  private SemanticRouterIndexSchema() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Create an index schema based on router name and vector dimensions. Ported from Python:
   * from_params() (line 90)
   *
   * @param name The name of the index
   * @param vectorDims The dimensions of the vectors
   * @param dtype The data type for vectors
   * @return The constructed index schema
   */
  public static IndexSchema fromParams(String name, int vectorDims, String dtype) {
    // Convert dtype string to VectorDataType enum
    VectorField.VectorDataType dataType =
        dtype != null && dtype.equalsIgnoreCase("float64")
            ? VectorField.VectorDataType.FLOAT64
            : VectorField.VectorDataType.FLOAT32;

    return IndexSchema.builder()
        .name(name)
        .prefix(name)
        .storageType(IndexSchema.StorageType.HASH)
        .field(TagField.builder().name("reference_id").build())
        .field(TagField.builder().name("route_name").build())
        .field(TextField.builder().name("reference").build())
        .field(
            VectorField.builder()
                .name(ROUTE_VECTOR_FIELD_NAME)
                .algorithm(redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.FLAT)
                .dimensions(vectorDims)
                .distanceMetric(VectorField.DistanceMetric.COSINE)
                .dataType(dataType)
                .build())
        .build();
  }
}
