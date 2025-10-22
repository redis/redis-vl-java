package com.redis.vl.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for parsing flexible sort specifications into normalized format.
 *
 * <p>Python port: Corresponds to SortSpec type alias and _parse_sort_spec() static method in Python
 * redisvl (PR #393)
 *
 * <p>Python SortSpec accepts:
 *
 * <ul>
 *   <li>str: field name (defaults to ASC)
 *   <li>Tuple[str, str]: (field_name, direction)
 *   <li>List[Union[str, Tuple[str, str]]]: multiple fields with optional directions
 * </ul>
 *
 * <p>Java SortSpec provides overloaded methods:
 *
 * <ul>
 *   <li>{@code parseSortSpec(String field)} - single field, ASC
 *   <li>{@code parseSortSpec(String field, String direction)} - single field with direction
 *   <li>{@code parseSortSpec(SortField field)} - single SortField
 *   <li>{@code parseSortSpec(List<SortField> fields)} - multiple fields
 * </ul>
 *
 * <p>Note: Redis Search only supports single-field sorting. When multiple fields are provided, only
 * the first field is used and a warning is logged.
 */
public final class SortSpec {

  private static final Logger logger = LoggerFactory.getLogger(SortSpec.class);

  // Private constructor - utility class
  private SortSpec() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Parse a single field name (defaults to ascending order).
   *
   * <p>Python equivalent: sort_by="price"
   *
   * @param field Field name to sort by
   * @return List containing single SortField with ascending=true
   * @throws IllegalArgumentException if field is null or empty
   */
  public static List<SortField> parseSortSpec(String field) {
    validateFieldName(field);
    return Collections.singletonList(SortField.asc(field.trim()));
  }

  /**
   * Parse a single field name with direction.
   *
   * <p>Python equivalent: sort_by=("price", "DESC")
   *
   * @param field Field name to sort by
   * @param direction Sort direction ("ASC" or "DESC", case-insensitive)
   * @return List containing single SortField
   * @throws IllegalArgumentException if field is null/empty or direction is invalid
   */
  public static List<SortField> parseSortSpec(String field, String direction) {
    validateFieldName(field);
    validateDirection(direction);

    String normalizedDirection = direction.trim().toUpperCase();
    boolean ascending = "ASC".equals(normalizedDirection);

    return Collections.singletonList(new SortField(field.trim(), ascending));
  }

  /**
   * Parse a single SortField.
   *
   * @param field SortField to wrap in list
   * @return List containing the single SortField
   * @throws IllegalArgumentException if field is null
   */
  public static List<SortField> parseSortSpec(SortField field) {
    if (field == null) {
      throw new IllegalArgumentException("SortField cannot be null");
    }
    return Collections.singletonList(field);
  }

  /**
   * Parse a list of SortFields (supports multiple fields).
   *
   * <p>Python equivalent: sort_by=[("price", "DESC"), ("rating", "ASC"), "stock"]
   *
   * <p>Note: Redis Search only supports single-field sorting. When multiple fields are provided,
   * only the first field is used and a warning is logged.
   *
   * @param fields List of SortFields
   * @return List of SortFields (may be empty, uses only first field for Redis)
   */
  public static List<SortField> parseSortSpec(List<SortField> fields) {
    if (fields == null || fields.isEmpty()) {
      return Collections.emptyList();
    }

    // Make defensive copy
    List<SortField> result = new ArrayList<>(fields);

    // Log warning if multiple fields specified (Redis limitation)
    if (result.size() > 1) {
      logger.warn(
          "Multiple sort fields specified ({}), but Redis Search only supports single-field"
              + " sorting. Using first field: '{}'",
          result.size(),
          result.get(0).getFieldName());
    }

    return result;
  }

  /**
   * Validate field name is not null or empty.
   *
   * @param field Field name to validate
   * @throws IllegalArgumentException if field is null or empty/blank
   */
  private static void validateFieldName(String field) {
    if (field == null || field.trim().isEmpty()) {
      throw new IllegalArgumentException("Field name cannot be null or empty");
    }
  }

  /**
   * Validate sort direction is "ASC" or "DESC" (case-insensitive).
   *
   * <p>Python raises: ValueError("Sort direction must be 'ASC' or 'DESC'")
   *
   * @param direction Direction string to validate
   * @throws IllegalArgumentException if direction is invalid
   */
  private static void validateDirection(String direction) {
    if (direction == null || direction.trim().isEmpty()) {
      throw new IllegalArgumentException("Sort direction cannot be null or empty");
    }

    String normalized = direction.trim().toUpperCase();
    if (!normalized.equals("ASC") && !normalized.equals("DESC")) {
      throw new IllegalArgumentException(
          String.format("Sort direction must be 'ASC' or 'DESC', got: '%s'", direction));
    }
  }
}
