package com.redis.vl.schema;

import lombok.Getter;
import redis.clients.jedis.search.schemafields.SchemaField;

/**
 * GeoField represents a geographic field in Redis. Used for fields that contain longitude,latitude
 * coordinates and support geographic queries.
 */
@Getter
public class GeoField extends BaseField {

  /** Create a GeoField with just a name */
  public GeoField(String name) {
    super(name);
  }

  /** Create a GeoField with all properties */
  private GeoField(String name, String alias, Boolean indexed, Boolean sortable) {
    super(name, alias, indexed != null ? indexed : true, sortable != null && sortable);
  }

  @Override
  public FieldType getFieldType() {
    return FieldType.GEO;
  }

  @Override
  public SchemaField toJedisSchemaField() {
    redis.clients.jedis.search.schemafields.GeoField jedisField =
        new redis.clients.jedis.search.schemafields.GeoField(name);

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

  /** Create a GeoField with fluent API */
  public static GeoFieldBuilder of(String name) {
    return new GeoFieldBuilder(name);
  }

  /** Create a GeoField builder (Lombok-style) */
  public static GeoFieldBuilder builder() {
    return new GeoFieldBuilder(null);
  }

  /** Fluent builder for GeoField */
  public static class GeoFieldBuilder {
    private String name;
    private String alias;
    private Boolean indexed;
    private Boolean sortable;

    private GeoFieldBuilder(String name) {
      this.name = name;
    }

    public GeoFieldBuilder name(String name) {
      this.name = name;
      return this;
    }

    public GeoFieldBuilder alias(String alias) {
      this.alias = alias;
      return this;
    }

    public GeoFieldBuilder withAlias(String alias) {
      this.alias = alias;
      return this;
    }

    public GeoFieldBuilder indexed(boolean indexed) {
      this.indexed = indexed;
      return this;
    }

    public GeoFieldBuilder sortable(boolean sortable) {
      this.sortable = sortable;
      return this;
    }

    public GeoFieldBuilder sortable() {
      this.sortable = true;
      return this;
    }

    public GeoField build() {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Field name cannot be null or empty");
      }
      return new GeoField(name, alias, indexed, sortable);
    }
  }
}
