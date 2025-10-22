package com.redis.vl.query;

import lombok.Value;

/**
 * Represents a sort field with its direction.
 *
 * <p>Python port: Corresponds to tuple (field_name, ascending) in _parse_sort_spec
 */
@Value
public class SortField {
  /** Field name to sort by */
  String fieldName;

  /** Whether to sort ascending (true) or descending (false) */
  boolean ascending;

  /**
   * Create a SortField with ascending order.
   *
   * @param fieldName Field name
   * @return SortField with ascending=true
   */
  public static SortField asc(String fieldName) {
    return new SortField(fieldName, true);
  }

  /**
   * Create a SortField with descending order.
   *
   * @param fieldName Field name
   * @return SortField with ascending=false
   */
  public static SortField desc(String fieldName) {
    return new SortField(fieldName, false);
  }

  /**
   * Get the direction as a string.
   *
   * @return "ASC" or "DESC"
   */
  public String getDirection() {
    return ascending ? "ASC" : "DESC";
  }
}
