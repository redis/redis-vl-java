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

  /** Create a TextField with just a name */
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

  /** Create a TextField with fluent API */
  public static TextFieldBuilder of(String name) {
    return new TextFieldBuilder(name);
  }

  /** Create a TextField builder (Lombok-style) */
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

    public TextFieldBuilder name(String name) {
      this.name = name;
      return this;
    }

    public TextFieldBuilder alias(String alias) {
      this.alias = alias;
      return this;
    }

    public TextFieldBuilder withAlias(String alias) {
      this.alias = alias;
      return this;
    }

    public TextFieldBuilder indexed(boolean indexed) {
      this.indexed = indexed;
      return this;
    }

    public TextFieldBuilder sortable(boolean sortable) {
      this.sortable = sortable;
      return this;
    }

    public TextFieldBuilder sortable() {
      this.sortable = true;
      return this;
    }

    public TextFieldBuilder weight(double weight) {
      this.weight = weight;
      return this;
    }

    public TextFieldBuilder withWeight(double weight) {
      this.weight = weight;
      return this;
    }

    public TextFieldBuilder noStem(boolean noStem) {
      this.noStem = noStem;
      return this;
    }

    public TextFieldBuilder noStem() {
      this.noStem = true;
      return this;
    }

    public TextFieldBuilder phonetic(String phonetic) {
      this.phonetic = phonetic;
      return this;
    }

    public TextFieldBuilder withPhonetic(String phonetic) {
      this.phonetic = phonetic;
      return this;
    }

    public TextField build() {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Field name cannot be null or empty");
      }
      return new TextField(name, alias, indexed, sortable, weight, noStem, phonetic);
    }
  }
}
