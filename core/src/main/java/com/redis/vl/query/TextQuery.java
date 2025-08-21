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

  public TextQuery(String text, String textField, String scorer, List<String> returnFields) {
    this(text, textField, scorer, null, returnFields);
  }

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

  public String getText() {
    return text;
  }

  public String getTextField() {
    return textField;
  }

  public String getScorer() {
    return scorer;
  }

  public Filter getFilterExpression() {
    return filterExpression;
  }

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
