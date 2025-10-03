package com.redis.vl.schema;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Enumeration of field types supported by RedisVL. Maps to Redis RediSearch field types. */
@Getter
@RequiredArgsConstructor
public enum FieldType {

  /** Text field for full-text search */
  TEXT("text"),

  /** Tag field for exact matching */
  TAG("tag"),

  /** Numeric field for range queries */
  NUMERIC("numeric"),

  /** Geo field for geographic queries */
  GEO("geo"),

  /** Vector field for similarity search */
  VECTOR("vector");

  /** The Redis field type name */
  private final String redisType;

  /**
   * Get the FieldType from its Redis type name.
   *
   * @param redisType The Redis type name (e.g., "text", "tag", "numeric")
   * @return The corresponding FieldType
   * @throws IllegalArgumentException if the Redis type is unknown
   */
  public static FieldType fromRedisType(String redisType) {
    for (FieldType type : values()) {
      if (type.redisType.equalsIgnoreCase(redisType)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown Redis field type: " + redisType);
  }
}
