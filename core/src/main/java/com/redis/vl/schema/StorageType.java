package com.redis.vl.schema;

/** Storage type for documents in Redis */
public enum StorageType {
  /** Hash storage type */
  HASH("hash"),
  /** JSON storage type */
  JSON("json");

  private final String value;

  /**
   * Creates a StorageType with the given value.
   *
   * @param value The string value of the storage type
   */
  StorageType(String value) {
    this.value = value;
  }

  /**
   * Get StorageType from string value.
   *
   * @param value The string value to convert
   * @return The corresponding StorageType
   * @throws IllegalArgumentException if the value is unknown
   */
  public static StorageType fromValue(String value) {
    for (StorageType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown storage type: " + value);
  }

  /**
   * Get the string value of this storage type.
   *
   * @return The string value
   */
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }
}
