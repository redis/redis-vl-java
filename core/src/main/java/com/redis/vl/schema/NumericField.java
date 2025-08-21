package com.redis.vl.schema;

import lombok.Getter;
import redis.clients.jedis.search.schemafields.SchemaField;

/**
 * NumericField represents a numeric field in Redis. Used for fields that contain numbers and
 * support range queries.
 */
@Getter
public class NumericField extends BaseField {

  /** Create a NumericField with just a name */
  public NumericField(String name) {
    super(name);
  }

  /** Create a NumericField with all properties */
  private NumericField(String name, String alias, Boolean indexed, Boolean sortable) {
    super(name, alias, indexed != null ? indexed : true, sortable != null ? sortable : false);
  }

  @Override
  public FieldType getFieldType() {
    return FieldType.NUMERIC;
  }

  @Override
  public SchemaField toJedisSchemaField() {
    redis.clients.jedis.search.schemafields.NumericField jedisField =
        new redis.clients.jedis.search.schemafields.NumericField(name);

    if (alias != null) {
      jedisField.as(alias);
    }

    if (sortable) {
      jedisField.sortable();
    }

    if (!indexed) {
      jedisField.noIndex();
    }

    return jedisField;
  }

  /** Create a NumericField with fluent API */
  public static NumericFieldBuilder of(String name) {
    return new NumericFieldBuilder(name);
  }

  /** Create a NumericField builder (Lombok-style) */
  public static NumericFieldBuilder builder() {
    return new NumericFieldBuilder(null);
  }

  /** Fluent builder for NumericField */
  public static class NumericFieldBuilder {
    private String name;
    private String alias;
    private Boolean indexed;
    private Boolean sortable;

    private NumericFieldBuilder(String name) {
      this.name = name;
    }

    public NumericFieldBuilder name(String name) {
      this.name = name;
      return this;
    }

    public NumericFieldBuilder alias(String alias) {
      this.alias = alias;
      return this;
    }

    public NumericFieldBuilder withAlias(String alias) {
      this.alias = alias;
      return this;
    }

    public NumericFieldBuilder indexed(boolean indexed) {
      this.indexed = indexed;
      return this;
    }

    public NumericFieldBuilder sortable(boolean sortable) {
      this.sortable = sortable;
      return this;
    }

    public NumericFieldBuilder sortable() {
      this.sortable = true;
      return this;
    }

    public NumericField build() {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Field name cannot be null or empty");
      }
      return new NumericField(name, alias, indexed, sortable);
    }
  }
}
