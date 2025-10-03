package com.redis.vl.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import redis.clients.jedis.search.schemafields.SchemaField;

/**
 * TextField represents a full-text searchable field in Redis. Supports features like stemming,
 * phonetic matching, and custom weights.
 */
@Getter
public class TextField extends BaseField {

  /** Weight for scoring in full-text search (default: 1.0) */
  @JsonProperty("weight")
  private final double weight;

  /** Whether to disable stemming for this field */
  @JsonProperty("noStem")
  private final boolean noStem;

  /** Phonetic matcher for this field (e.g., "dm:en" for Double Metaphone English) */
  @JsonProperty("phonetic")
  private final String phonetic;

  /**
   * Create a TextField with just a name
   *
   * @param name Field name
   */
  public TextField(String name) {
    this(name, null, true, false, 1.0, false, null);
  }

  /** Create a TextField with all properties */
  private TextField(
      String name,
      String alias,
      Boolean indexed,
      Boolean sortable,
      Double weight,
      Boolean noStem,
      String phonetic) {
    super(name, alias, indexed != null ? indexed : true, sortable != null ? sortable : false);
    this.weight = weight != null ? weight : 1.0;
    this.noStem = noStem != null ? noStem : false;
    this.phonetic = phonetic;
  }

  /**
   * Create a TextField with fluent API
   *
   * @param name Field name
   * @return TextField builder
   */
  public static TextFieldBuilder of(String name) {
    return new TextFieldBuilder(name);
  }

  /**
   * Create a TextField builder (Lombok-style)
   *
   * @return TextField builder
   */
  public static TextFieldBuilder builder() {
    return new TextFieldBuilder(null);
  }

  @Override
  public FieldType getFieldType() {
    return FieldType.TEXT;
  }

  @Override
  public SchemaField toJedisSchemaField() {
    redis.clients.jedis.search.schemafields.TextField jedisField =
        new redis.clients.jedis.search.schemafields.TextField(name);

    if (alias != null) {
      jedisField.as(alias);
    }

    if (sortable) {
      jedisField.sortable();
    }

    if (noStem) {
      jedisField.noStem();
    }

    if (weight != 1.0) {
      jedisField.weight(weight);
    }

    if (phonetic != null) {
      jedisField.phonetic(phonetic);
    }

    if (!indexed) {
      jedisField.noIndex();
    }

    return jedisField;
  }

  /** Fluent builder for TextField */
  public static class TextFieldBuilder {
    private String name;
    private String alias;
    private Boolean indexed;
    private Boolean sortable;
    private Double weight;
    private Boolean noStem;
    private String phonetic;

    private TextFieldBuilder(String name) {
      this.name = name;
    }

    /**
     * Set the field name
     *
     * @param name Field name
     * @return This builder
     */
    public TextFieldBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Set the field alias
     *
     * @param alias Field alias
     * @return This builder
     */
    public TextFieldBuilder alias(String alias) {
      this.alias = alias;
      return this;
    }

    /**
     * Set the field alias (alternative method)
     *
     * @param alias Field alias
     * @return This builder
     */
    public TextFieldBuilder withAlias(String alias) {
      this.alias = alias;
      return this;
    }

    /**
     * Set whether the field is indexed
     *
     * @param indexed True if indexed
     * @return This builder
     */
    public TextFieldBuilder indexed(boolean indexed) {
      this.indexed = indexed;
      return this;
    }

    /**
     * Set whether the field is sortable
     *
     * @param sortable True if sortable
     * @return This builder
     */
    public TextFieldBuilder sortable(boolean sortable) {
      this.sortable = sortable;
      return this;
    }

    /**
     * Set the field as sortable
     *
     * @return This builder
     */
    public TextFieldBuilder sortable() {
      this.sortable = true;
      return this;
    }

    /**
     * Set the weight for scoring in full-text search
     *
     * @param weight Weight value
     * @return This builder
     */
    public TextFieldBuilder weight(double weight) {
      this.weight = weight;
      return this;
    }

    /**
     * Set the weight for scoring in full-text search (alternative method)
     *
     * @param weight Weight value
     * @return This builder
     */
    public TextFieldBuilder withWeight(double weight) {
      this.weight = weight;
      return this;
    }

    /**
     * Set whether to disable stemming
     *
     * @param noStem True to disable stemming
     * @return This builder
     */
    public TextFieldBuilder noStem(boolean noStem) {
      this.noStem = noStem;
      return this;
    }

    /**
     * Disable stemming for this field
     *
     * @return This builder
     */
    public TextFieldBuilder noStem() {
      this.noStem = true;
      return this;
    }

    /**
     * Set the phonetic matcher
     *
     * @param phonetic Phonetic matcher (e.g., "dm:en" for Double Metaphone English)
     * @return This builder
     */
    public TextFieldBuilder phonetic(String phonetic) {
      this.phonetic = phonetic;
      return this;
    }

    /**
     * Set the phonetic matcher (alternative method)
     *
     * @param phonetic Phonetic matcher (e.g., "dm:en" for Double Metaphone English)
     * @return This builder
     */
    public TextFieldBuilder withPhonetic(String phonetic) {
      this.phonetic = phonetic;
      return this;
    }

    /**
     * Build the TextField
     *
     * @return TextField instance
     */
    public TextField build() {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Field name cannot be null or empty");
      }
      return new TextField(name, alias, indexed, sortable, weight, noStem, phonetic);
    }
  }
}
