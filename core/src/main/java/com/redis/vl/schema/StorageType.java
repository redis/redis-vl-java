package com.redis.vl.schema;

/** Storage type for documents in Redis */
public enum StorageType {
  HASH("hash"),
  JSON("json");

  private final String value;

  StorageType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return value;
  }

  public static StorageType fromValue(String value) {
    for (StorageType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown storage type: " + value);
  }
}
