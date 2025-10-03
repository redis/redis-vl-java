package com.redis.vl.query;

import java.util.ArrayList;
import java.util.List;

/** Full-text search query */
public class TextQuery {

  private final String text;
  private final String textField;
  private final String scorer;
  private final Filter filterExpression;
  private final List<String> returnFields;

  /**
   * Create a text query without a filter expression.
   *
   * @param text The text to search for
   * @param textField The field to search in
   * @param scorer The scoring algorithm (e.g., "BM25", "TF IDF")
   * @param returnFields List of fields to return in results
   */
  public TextQuery(String text, String textField, String scorer, List<String> returnFields) {
    this(text, textField, scorer, null, returnFields);
  }

  /**
   * Create a text query with all parameters.
   *
   * @param text The text to search for
   * @param textField The field to search in
   * @param scorer The scoring algorithm
   * @param filterExpression Optional filter to apply
   * @param returnFields List of fields to return in results
   */
  public TextQuery(
      String text,
      String textField,
      String scorer,
      Filter filterExpression,
      List<String> returnFields) {
    this.text = text;
    this.textField = textField;
    this.scorer = scorer;
    this.filterExpression = filterExpression;
    this.returnFields = returnFields != null ? new ArrayList<>(returnFields) : null;
  }

  /**
   * Get the search text
   *
   * @return Search text
   */
  public String getText() {
    return text;
  }

  /**
   * Get the text field to search in
   *
   * @return Text field name
   */
  public String getTextField() {
    return textField;
  }

  /**
   * Get the scoring algorithm
   *
   * @return Scorer name
   */
  public String getScorer() {
    return scorer;
  }

  /**
   * Get the filter expression
   *
   * @return Filter expression or null
   */
  public Filter getFilterExpression() {
    return filterExpression;
  }

  /**
   * Get the return fields
   *
   * @return List of fields to return or null
   */
  public List<String> getReturnFields() {
    return returnFields != null ? new ArrayList<>(returnFields) : null;
  }

  /**
   * Build the query string for Redis text search
   *
   * @return Query string
   */
  public String toQueryString() {
    StringBuilder query = new StringBuilder();

    // Add filter expression if present
    if (filterExpression != null) {
      query.append(filterExpression.build()).append(" ");
    }

    // Add text search
    if (textField != null && !textField.isEmpty()) {
      query.append("@").append(textField).append(":(").append(text).append(")");
    } else {
      // Search all text fields
      query.append(text);
    }

    return query.toString();
  }

  @Override
  public String toString() {
    return toQueryString();
  }
}
