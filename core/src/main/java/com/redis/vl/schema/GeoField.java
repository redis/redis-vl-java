package com.redis.vl.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import redis.clients.jedis.search.schemafields.SchemaField;

/**
 * GeoField represents a geographic field in Redis. Used for fields that contain longitude,latitude
 * coordinates and support geographic queries.
 */
@Getter
public class GeoField extends BaseField {

  /**
   * Index missing values - allow indexing and searching for missing values (documents without this
   * field)
   */
  @JsonProperty("index_missing")
  private final boolean indexMissing;

  /**
   * Create a GeoField with just a name.
   *
   * @param name The field name
   */
  public GeoField(String name) {
    this(name, null, true, false, false);
  }

  /**
   * Create a GeoField with all properties.
   *
   * @param name The field name
   * @param alias The field alias
   * @param indexed Whether the field is indexed
   * @param sortable Whether the field is sortable
   * @param indexMissing Whether to index missing values
   */
  private GeoField(
      String name, String alias, Boolean indexed, Boolean sortable, Boolean indexMissing) {
    super(name, alias, indexed != null ? indexed : true, sortable != null && sortable);
    this.indexMissing = indexMissing != null ? indexMissing : false;
  }

  /**
   * Create a GeoField with fluent API.
   *
   * @param name The field name
   * @return A GeoFieldBuilder for fluent configuration
   */
  public static GeoFieldBuilder of(String name) {
    return new GeoFieldBuilder(name);
  }

  /**
   * Create a GeoField builder (Lombok-style).
   *
   * @return A GeoFieldBuilder for fluent configuration
   */
  public static GeoFieldBuilder builder() {
    return new GeoFieldBuilder(null);
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

    // Apply modifiers in canonical order required by RediSearch parser:
    // [INDEXMISSING] [SORTABLE] [NOINDEX]
    // Note: GeoField does not support INDEXEMPTY or UNF

    if (indexMissing) {
      jedisField.indexMissing();
    }

    if (sortable) {
      jedisField.sortable();
    }

    if (!indexed) {
      jedisField.noIndex();
    }

    return jedisField;
  }

  /** Fluent builder for GeoField */
  public static class GeoFieldBuilder {
    private String name;
    private String alias;
    private Boolean indexed;
    private Boolean sortable;
    private Boolean indexMissing;

    private GeoFieldBuilder(String name) {
      this.name = name;
    }

    /**
     * Set the field name.
     *
     * @param name The name for this field
     * @return This builder for chaining
     */
    public GeoFieldBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Set the field alias.
     *
     * @param alias The alias for this field
     * @return This builder for chaining
     */
    public GeoFieldBuilder alias(String alias) {
      this.alias = alias;
      return this;
    }

    /**
     * Set the field alias (alternative method name).
     *
     * @param alias The alias for this field
     * @return This builder for chaining
     */
    public GeoFieldBuilder withAlias(String alias) {
      this.alias = alias;
      return this;
    }

    /**
     * Set whether the field is indexed.
     *
     * @param indexed True if the field should be indexed
     * @return This builder for chaining
     */
    public GeoFieldBuilder indexed(boolean indexed) {
      this.indexed = indexed;
      return this;
    }

    /**
     * Set whether the field is sortable.
     *
     * @param sortable True if the field should be sortable
     * @return This builder for chaining
     */
    public GeoFieldBuilder sortable(boolean sortable) {
      this.sortable = sortable;
      return this;
    }

    /**
     * Make the field sortable.
     *
     * @return This builder for chaining
     */
    public GeoFieldBuilder sortable() {
      this.sortable = true;
      return this;
    }

    /**
     * Set whether to index missing values (documents without this field).
     *
     * <p>When enabled, allows searching for documents where this field is missing using the
     * ismissing() filter.
     *
     * @param indexMissing True to index missing values
     * @return This builder for chaining
     */
    public GeoFieldBuilder indexMissing(boolean indexMissing) {
      this.indexMissing = indexMissing;
      return this;
    }

    /**
     * Index missing values (equivalent to indexMissing(true)).
     *
     * <p>When enabled, allows searching for documents where this field is missing using the
     * ismissing() filter.
     *
     * @return This builder for chaining
     */
    public GeoFieldBuilder indexMissing() {
      this.indexMissing = true;
      return this;
    }

    /**
     * Build the GeoField.
     *
     * @return A new GeoField instance
     * @throws IllegalArgumentException if the field name is null or empty
     */
    public GeoField build() {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Field name cannot be null or empty");
      }
      return new GeoField(name, alias, indexed, sortable, indexMissing);
    }
  }
}
