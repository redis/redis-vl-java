package com.redis.vl.query;

import com.redis.vl.utils.TokenEscaper;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Full-text search query with support for field weights.
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

  private TextQuery(Builder builder) {
    this.text = builder.text;
    this.scorer = builder.scorer;
    this.filterExpression = builder.filterExpression;
    this.numResults = builder.numResults;
    this.fieldWeights = new HashMap<>(builder.fieldWeights);
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

  /** Builder for TextQuery with support for field weights. */
  public static class Builder {
    private String text;
    private String scorer = "BM25STD";
    private Filter filterExpression;
    private Integer numResults = 10;
    private Map<String, Double> fieldWeights = new HashMap<>();

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
