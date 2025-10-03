package com.redis.vl.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import redis.clients.jedis.search.schemafields.SchemaField;

/**
 * TagField represents an exact-match field in Redis. Used for categories, statuses, or other fields
 * that require exact matching rather than full-text search.
 */
@Getter
public class TagField extends BaseField {

  /** Separator for multi-value tags (default: ",") */
  @JsonProperty("separator")
  private final String separator;

  /** Whether tag matching is case-sensitive */
  @JsonProperty("caseSensitive")
  private final boolean caseSensitive;

  /**
   * Create a TagField with just a name
   *
   * @param name Field name
   */
  public TagField(String name) {
    this(name, null, true, false, ",", false);
  }

  /** Create a TagField with all properties */
  private TagField(
      String name,
      String alias,
      Boolean indexed,
      Boolean sortable,
      String separator,
      Boolean caseSensitive) {
    super(name, alias, indexed != null ? indexed : true, sortable != null ? sortable : false);
    // Handle empty separator by using default
    this.separator = (separator == null || separator.isEmpty()) ? "," : separator;
    this.caseSensitive = caseSensitive != null ? caseSensitive : false;
  }

  /**
   * Create a TagField with fluent API
   *
   * @param name Field name
   * @return TagField builder
   */
  public static TagFieldBuilder of(String name) {
    return new TagFieldBuilder(name);
  }

  /**
   * Create a TagField builder (Lombok-style)
   *
   * @return TagField builder
   */
  public static TagFieldBuilder builder() {
    return new TagFieldBuilder(null);
  }

  @Override
  public FieldType getFieldType() {
    return FieldType.TAG;
  }

  @Override
  public SchemaField toJedisSchemaField() {
    redis.clients.jedis.search.schemafields.TagField jedisField =
        new redis.clients.jedis.search.schemafields.TagField(name);

    if (alias != null) {
      jedisField.as(alias);
    }

    if (sortable) {
      jedisField.sortable();
    }

    if (!separator.equals(",")) {
      jedisField.separator(separator.charAt(0));
    }

    if (caseSensitive) {
      jedisField.caseSensitive();
    }

    if (!indexed) {
      jedisField.noIndex();
    }

    return jedisField;
  }

  /** Fluent builder for TagField */
  public static class TagFieldBuilder {
    private String name;
    private String alias;
    private Boolean indexed;
    private Boolean sortable;
    private String separator;
    private Boolean caseSensitive;

    private TagFieldBuilder(String name) {
      this.name = name;
    }

    /**
     * Set the field name
     *
     * @param name Field name
     * @return This builder
     */
    public TagFieldBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Set the field alias
     *
     * @param alias Field alias
     * @return This builder
     */
    public TagFieldBuilder alias(String alias) {
      this.alias = alias;
      return this;
    }

    /**
     * Set the field alias (alternative method)
     *
     * @param alias Field alias
     * @return This builder
     */
    public TagFieldBuilder withAlias(String alias) {
      this.alias = alias;
      return this;
    }

    /**
     * Set whether the field is indexed
     *
     * @param indexed True if indexed
     * @return This builder
     */
    public TagFieldBuilder indexed(boolean indexed) {
      this.indexed = indexed;
      return this;
    }

    /**
     * Set whether the field is sortable
     *
     * @param sortable True if sortable
     * @return This builder
     */
    public TagFieldBuilder sortable(boolean sortable) {
      this.sortable = sortable;
      return this;
    }

    /**
     * Set the field as sortable
     *
     * @return This builder
     */
    public TagFieldBuilder sortable() {
      this.sortable = true;
      return this;
    }

    /**
     * Set the separator for multi-value tags
     *
     * @param separator Separator character (default: ",")
     * @return This builder
     */
    public TagFieldBuilder separator(String separator) {
      this.separator = separator;
      return this;
    }

    /**
     * Set the separator for multi-value tags (alternative method)
     *
     * @param separator Separator character (default: ",")
     * @return This builder
     */
    public TagFieldBuilder withSeparator(String separator) {
      this.separator = separator;
      return this;
    }

    /**
     * Set whether tag matching is case-sensitive
     *
     * @param caseSensitive True for case-sensitive matching
     * @return This builder
     */
    public TagFieldBuilder caseSensitive(boolean caseSensitive) {
      this.caseSensitive = caseSensitive;
      return this;
    }

    /**
     * Set tag matching as case-sensitive
     *
     * @return This builder
     */
    public TagFieldBuilder caseSensitive() {
      this.caseSensitive = true;
      return this;
    }

    /**
     * Build the TagField
     *
     * @return TagField instance
     */
    public TagField build() {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Field name cannot be null or empty");
      }
      return new TagField(name, alias, indexed, sortable, separator, caseSensitive);
    }
  }
}
