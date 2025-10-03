package com.redis.vl.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.search.FieldName;

/**
 * Base class for all field types in RedisVL. Provides common functionality for all field
 * implementations.
 */
@Getter
@Slf4j
public abstract class BaseField {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /** The name of the field in Redis */
  @JsonProperty("name")
  protected final String name;

  /** Optional alias for the field */
  @JsonProperty("alias")
  protected final String alias;

  /** Whether this field should be indexed */
  @JsonProperty("indexed")
  protected final boolean indexed;

  /** Whether this field is sortable */
  @JsonProperty("sortable")
  protected final boolean sortable;

  /**
   * Create a field with just a name (defaults: indexed=true, sortable=false).
   *
   * @param name The name of the field in Redis
   */
  protected BaseField(String name) {
    // Validate name before calling other constructor
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Field name cannot be null or empty");
    }
    this.name = name.trim();
    this.alias = null;
    this.indexed = true;
    this.sortable = false;
  }

  /**
   * Create a field with all properties.
   *
   * @param name The name of the field in Redis
   * @param alias Optional alias for the field
   * @param indexed Whether this field should be indexed
   * @param sortable Whether this field is sortable
   */
  protected BaseField(String name, String alias, boolean indexed, boolean sortable) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Field name cannot be null or empty");
    }
    this.name = name.trim();
    this.alias = alias;
    this.indexed = indexed;
    this.sortable = sortable;
  }

  /**
   * Get the field type.
   *
   * @return The field type enumeration
   */
  @JsonProperty("type")
  public abstract FieldType getFieldType();

  /**
   * Convert to Jedis SchemaField for index creation.
   *
   * @return The Jedis schema field representation
   */
  @JsonIgnore
  public abstract redis.clients.jedis.search.schemafields.SchemaField toJedisSchemaField();

  /**
   * Convert to Jedis FieldName for query building.
   *
   * @return The Jedis field name representation
   */
  @JsonIgnore
  public FieldName toJedisFieldName() {
    return alias != null ? new FieldName(name, alias) : new FieldName(name);
  }

  /**
   * Serialize this field to JSON.
   *
   * @return JSON string representation of this field
   */
  public String toJson() {
    try {
      return objectMapper.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize field to JSON: {}", name, e);
      throw new RuntimeException("Failed to serialize field to JSON", e);
    }
  }

  @Override
  public String toString() {
    return String.format(
        "%s[name=%s, type=%s, indexed=%s, sortable=%s]",
        getClass().getSimpleName(), name, getFieldType(), indexed, sortable);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BaseField baseField = (BaseField) o;
    return indexed == baseField.indexed
        && sortable == baseField.sortable
        && Objects.equals(name, baseField.name)
        && Objects.equals(alias, baseField.alias);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, alias, indexed, sortable);
  }
}
