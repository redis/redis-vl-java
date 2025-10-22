package com.redis.vl.query;

import com.redis.vl.utils.TokenEscaper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * Full-text search query with support for field weights and sorting.
 *
 * <p>Python port: Implements text_field_name with Union[str, Dict[str, float]] for weighted text
 * search across multiple fields.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Single field (backward compatible)
 * TextQuery query = TextQuery.builder()
 *     .text("search terms")
 *     .textField("description")
 *     .build();
 *
 * // Multiple fields with weights
 * TextQuery query = TextQuery.builder()
 *     .text("search terms")
 *     .textFieldWeights(Map.of("title", 5.0, "content", 2.0, "tags", 1.0))
 *     .build();
 *
 * // With sorting
 * TextQuery query = TextQuery.builder()
 *     .text("search terms")
 *     .textField("description")
 *     .sortBy("price", "DESC")
 *     .build();
 * }</pre>
 */
@Getter
public class TextQuery {

  private final String text;
  private final String scorer;
  private final Filter filterExpression;
  private final Integer numResults;

  /** Field names mapped to their search weights */
  private Map<String, Double> fieldWeights;

  /** Field to sort results by */
  private final String sortBy;

  /** Whether to sort in descending order */
  private final boolean sortDescending;

  /** Fields that should not be decoded from binary format */
  private final List<String> skipDecodeFields;

  private TextQuery(Builder builder) {
    this.text = builder.text;
    this.scorer = builder.scorer;
    this.filterExpression = builder.filterExpression;
    this.numResults = builder.numResults;
    this.fieldWeights = new HashMap<>(builder.fieldWeights);
    this.sortBy = builder.sortBy;
    this.sortDescending = builder.sortDescending;
    this.skipDecodeFields =
        builder.skipDecodeFields != null ? List.copyOf(builder.skipDecodeFields) : List.of();
  }

  /**
   * Update the field weights dynamically.
   *
   * @param fieldWeights Map of field names to weights
   */
  public void setFieldWeights(Map<String, Double> fieldWeights) {
    validateFieldWeights(fieldWeights);
    this.fieldWeights = new HashMap<>(fieldWeights);
  }

  /**
   * Get a copy of the field weights.
   *
   * @return Map of field names to weights
   */
  public Map<String, Double> getFieldWeights() {
    return new HashMap<>(fieldWeights);
  }

  /**
   * Get the list of fields that should not be decoded from binary format.
   *
   * @return List of field names to skip decoding
   */
  public List<String> getSkipDecodeFields() {
    return skipDecodeFields;
  }

  /**
   * Build the Redis query string for text search with weighted fields.
   *
   * <p>Format:
   *
   * <ul>
   *   <li>Single field default weight: {@code @field:(term1 | term2)}
   *   <li>Single field with weight: {@code @field:(term1 | term2) => { $weight: 5.0 }}
   *   <li>Multiple fields: {@code (@field1:(terms) => { $weight: 3.0 } | @field2:(terms) => {
   *       $weight: 2.0 })}
   * </ul>
   *
   * @return Redis query string
   */
  public String toQueryString() {
    TokenEscaper escaper = new TokenEscaper();

    // Tokenize and escape the query text
    String[] tokens = text.split("\\s+");
    StringBuilder escapedQuery = new StringBuilder();

    for (int i = 0; i < tokens.length; i++) {
      if (i > 0) {
        escapedQuery.append(" | ");
      }
      String cleanToken =
          tokens[i].strip().stripLeading().stripTrailing().replace(",", "").toLowerCase();
      escapedQuery.append(escaper.escape(cleanToken));
    }

    String escapedText = escapedQuery.toString();

    // Build query parts for each field with its weight
    StringBuilder queryBuilder = new StringBuilder();
    int fieldCount = 0;

    for (Map.Entry<String, Double> entry : fieldWeights.entrySet()) {
      String field = entry.getKey();
      Double weight = entry.getValue();

      if (fieldCount > 0) {
        queryBuilder.append(" | ");
      }

      queryBuilder.append("@").append(field).append(":(").append(escapedText).append(")");

      // Add weight modifier if not default
      if (weight != 1.0) {
        queryBuilder.append(" => { $weight: ").append(weight).append(" }");
      }

      fieldCount++;
    }

    // Wrap multiple fields in parentheses
    String textQuery;
    if (fieldWeights.size() > 1) {
      textQuery = "(" + queryBuilder.toString() + ")";
    } else {
      textQuery = queryBuilder.toString();
    }

    // Add filter expression if present
    if (filterExpression != null) {
      return textQuery + " AND " + filterExpression.build();
    }

    return textQuery;
  }

  @Override
  public String toString() {
    return toQueryString();
  }

  /**
   * Create a new Builder for TextQuery.
   *
   * @return Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for TextQuery with support for field weights and sorting. */
  public static class Builder {
    private String text;
    private String scorer = "BM25STD";
    private Filter filterExpression;
    private Integer numResults = 10;
    private Map<String, Double> fieldWeights = new HashMap<>();
    private String sortBy;
    private boolean sortDescending = false;
    private List<String> skipDecodeFields = List.of();

    /**
     * Set the text to search for.
     *
     * @param text Search text
     * @return Builder
     */
    public Builder text(String text) {
      this.text = text;
      return this;
    }

    /**
     * Set a single text field to search (backward compatible).
     *
     * @param fieldName Field name
     * @return Builder
     */
    public Builder textField(String fieldName) {
      this.fieldWeights = Map.of(fieldName, 1.0);
      return this;
    }

    /**
     * Set multiple text fields with weights.
     *
     * @param fieldWeights Map of field names to weights
     * @return Builder
     */
    public Builder textFieldWeights(Map<String, Double> fieldWeights) {
      validateFieldWeights(fieldWeights);
      this.fieldWeights = new HashMap<>(fieldWeights);
      return this;
    }

    /**
     * Set the scoring algorithm.
     *
     * @param scorer Scorer name (e.g., BM25STD, TFIDF)
     * @return Builder
     */
    public Builder scorer(String scorer) {
      this.scorer = scorer;
      return this;
    }

    /**
     * Set the filter expression.
     *
     * @param filterExpression Filter to apply
     * @return Builder
     */
    public Builder filterExpression(Filter filterExpression) {
      this.filterExpression = filterExpression;
      return this;
    }

    /**
     * Set the number of results to return.
     *
     * @param numResults Number of results
     * @return Builder
     */
    public Builder numResults(int numResults) {
      this.numResults = numResults;
      return this;
    }

    /**
     * Set the sort field (defaults to ascending).
     *
     * <p>Python equivalent: sort_by="price"
     *
     * @param sortBy Field name to sort by
     * @return This builder
     */
    public Builder sortBy(String sortBy) {
      this.sortBy = sortBy;
      this.sortDescending = false; // Default to ascending
      return this;
    }

    /**
     * Set the sort field with explicit direction.
     *
     * <p>Python equivalent: sort_by=("price", "DESC")
     *
     * @param field Field name to sort by
     * @param direction Sort direction ("ASC" or "DESC", case-insensitive)
     * @return This builder
     * @throws IllegalArgumentException if direction is invalid
     */
    public Builder sortBy(String field, String direction) {
      List<SortField> parsed = SortSpec.parseSortSpec(field, direction);
      SortField sortField = parsed.get(0);
      this.sortBy = sortField.getFieldName();
      this.sortDescending = !sortField.isAscending();
      return this;
    }

    /**
     * Set the sort field using SortField.
     *
     * <p>Python equivalent: sort_by=("rating", "DESC") or using SortField.desc("rating")
     *
     * @param sortField SortField specifying field and direction
     * @return This builder
     * @throws IllegalArgumentException if sortField is null
     */
    public Builder sortBy(SortField sortField) {
      if (sortField == null) {
        throw new IllegalArgumentException("SortField cannot be null");
      }
      this.sortBy = sortField.getFieldName();
      this.sortDescending = !sortField.isAscending();
      return this;
    }

    /**
     * Set the sort fields (supports multiple fields, but only first is used).
     *
     * <p>Python equivalent: sort_by=[("price", "ASC"), ("rating", "DESC")]
     *
     * <p>Note: Redis Search only supports single-field sorting. When multiple fields are provided,
     * only the first field is used and a warning is logged.
     *
     * @param sortFields List of SortFields
     * @return This builder
     */
    public Builder sortBy(List<SortField> sortFields) {
      List<SortField> parsed = SortSpec.parseSortSpec(sortFields);
      if (!parsed.isEmpty()) {
        SortField firstField = parsed.get(0);
        this.sortBy = firstField.getFieldName();
        this.sortDescending = !firstField.isAscending();
      } else {
        // Empty list - clear sorting
        this.sortBy = null;
      }
      return this;
    }

    /**
     * Set whether to sort in descending order.
     *
     * @param descending True for descending sort
     * @return This builder
     */
    public Builder sortDescending(boolean descending) {
      this.sortDescending = descending;
      return this;
    }

    /**
     * Set fields that should not be decoded from binary format.
     *
     * @param skipDecodeFields List of field names
     * @return This builder
     * @throws IllegalArgumentException if list contains null values
     */
    public Builder skipDecodeFields(List<String> skipDecodeFields) {
      if (skipDecodeFields == null) {
        this.skipDecodeFields = List.of();
        return this;
      }
      // Validate no null values
      for (String field : skipDecodeFields) {
        if (field == null) {
          throw new IllegalArgumentException("skipDecodeFields cannot contain null values");
        }
      }
      this.skipDecodeFields = List.copyOf(skipDecodeFields);
      return this;
    }

    /**
     * Set fields that should not be decoded from binary format (varargs).
     *
     * @param fields Field names
     * @return This builder
     * @throws IllegalArgumentException if any field is null
     */
    public Builder skipDecodeFields(String... fields) {
      if (fields == null || fields.length == 0) {
        this.skipDecodeFields = List.of();
        return this;
      }
      for (String field : fields) {
        if (field == null) {
          throw new IllegalArgumentException("skipDecodeFields cannot contain null values");
        }
      }
      this.skipDecodeFields = List.of(fields);
      return this;
    }

    /**
     * Build the TextQuery instance.
     *
     * @return TextQuery
     * @throws IllegalArgumentException if text is null or field weights are empty
     */
    public TextQuery build() {
      if (text == null || text.trim().isEmpty()) {
        throw new IllegalArgumentException("Text cannot be null or empty");
      }
      if (fieldWeights.isEmpty()) {
        throw new IllegalArgumentException("At least one text field must be specified");
      }
      return new TextQuery(this);
    }
  }

  /**
   * Validate field weights.
   *
   * @param fieldWeights Map to validate
   * @throws IllegalArgumentException if weights are invalid
   */
  private static void validateFieldWeights(Map<String, Double> fieldWeights) {
    for (Map.Entry<String, Double> entry : fieldWeights.entrySet()) {
      String field = entry.getKey();
      Double weight = entry.getValue();

      if (weight == null) {
        throw new IllegalArgumentException("Weight for field '" + field + "' cannot be null");
      }
      if (weight <= 0) {
        throw new IllegalArgumentException(
            "Weight for field '" + field + "' must be positive, got " + weight);
      }
    }
  }
}
