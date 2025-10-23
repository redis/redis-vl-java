package com.redis.vl.schema;

import lombok.Getter;
import redis.clients.jedis.search.schemafields.SchemaField;

/**
 * NumericField represents a numeric field in Redis. Used for fields that contain numbers and
 * support range queries.
 */
@Getter
public class NumericField extends BaseField {

  /** Un-normalized form - disable normalization for sorting (only applies when sortable=true) */
  private final boolean unf;

  /**
   * Create a NumericField with just a name.
   *
   * @param name The field name
   */
  public NumericField(String name) {
    this(name, null, true, false, false);
  }

  /** Create a NumericField with all properties */
  private NumericField(String name, String alias, Boolean indexed, Boolean sortable, Boolean unf) {
    super(name, alias, indexed != null ? indexed : true, sortable != null ? sortable : false);
    this.unf = unf != null ? unf : false;
  }

  /**
   * Create a NumericField with fluent API.
   *
   * @param name The field name
   * @return A NumericFieldBuilder for fluent configuration
   */
  public static NumericFieldBuilder of(String name) {
    return new NumericFieldBuilder(name);
  }

  /**
   * Create a NumericField builder (Lombok-style).
   *
   * @return A NumericFieldBuilder for fluent configuration
   */
  public static NumericFieldBuilder builder() {
    return new NumericFieldBuilder(null);
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
      // NOTE: Jedis NumericField doesn't support sortableUNF() yet
      // The unf flag is stored in this wrapper but cannot be passed to Redis via Jedis
      // TODO: File issue with Jedis to add sortableUNF() support for NumericField
    }

    if (!indexed) {
      jedisField.noIndex();
    }

    return jedisField;
  }

  /** Fluent builder for NumericField */
  public static class NumericFieldBuilder {
    private String name;
    private String alias;
    private Boolean indexed;
    private Boolean sortable;
    private Boolean unf;

    private NumericFieldBuilder(String name) {
      this.name = name;
    }

    /**
     * Set the field name.
     *
     * @param name The name for this field
     * @return This builder for chaining
     */
    public NumericFieldBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Set the field alias.
     *
     * @param alias The alias for this field
     * @return This builder for chaining
     */
    public NumericFieldBuilder alias(String alias) {
      this.alias = alias;
      return this;
    }

    /**
     * Set the field alias (alias for alias() method).
     *
     * @param alias The alias for this field
     * @return This builder for chaining
     */
    public NumericFieldBuilder withAlias(String alias) {
      this.alias = alias;
      return this;
    }

    /**
     * Set whether this field should be indexed.
     *
     * @param indexed True to index this field, false otherwise
     * @return This builder for chaining
     */
    public NumericFieldBuilder indexed(boolean indexed) {
      this.indexed = indexed;
      return this;
    }

    /**
     * Set whether this field should be sortable.
     *
     * @param sortable True to make this field sortable, false otherwise
     * @return This builder for chaining
     */
    public NumericFieldBuilder sortable(boolean sortable) {
      this.sortable = sortable;
      return this;
    }

    /**
     * Make this field sortable (equivalent to sortable(true)).
     *
     * @return This builder for chaining
     */
    public NumericFieldBuilder sortable() {
      this.sortable = true;
      return this;
    }

    /**
     * Set whether to use un-normalized form for sorting
     *
     * <p>UNF disables normalization when sorting, preserving original values. Only applies when
     * sortable=true.
     *
     * <p>NOTE: Jedis doesn't support sortableUNF() for NumericField yet, so this flag is stored but
     * not passed to Redis.
     *
     * @param unf True to disable normalization for sorting
     * @return This builder for chaining
     */
    public NumericFieldBuilder unf(boolean unf) {
      this.unf = unf;
      return this;
    }

    /**
     * Use un-normalized form for sorting (equivalent to unf(true))
     *
     * <p>UNF disables normalization when sorting, preserving original values. Only applies when
     * sortable=true.
     *
     * <p>NOTE: Jedis doesn't support sortableUNF() for NumericField yet, so this flag is stored but
     * not passed to Redis.
     *
     * @return This builder for chaining
     */
    public NumericFieldBuilder unf() {
      this.unf = true;
      return this;
    }

    /**
     * Build the NumericField instance.
     *
     * @return The configured NumericField
     * @throws IllegalArgumentException if the field name is null or empty
     */
    public NumericField build() {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Field name cannot be null or empty");
      }
      return new NumericField(name, alias, indexed, sortable, unf);
    }
  }
}
