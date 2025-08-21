package com.redis.vl.query;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Represents a filter for Redis search */
public class Filter {

  /** Geographic units for radius queries */
  public enum GeoUnit {
    M("m"),
    KM("km"),
    MI("mi"),
    FT("ft");

    private final String value;

    GeoUnit(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  /** Filter type */
  private enum FilterType {
    TEXT,
    TAG,
    NUMERIC,
    GEO,
    AND,
    OR,
    NOT,
    CUSTOM
  }

  private final FilterType type;
  private final String field;
  private final String expression;
  private final List<Filter> subFilters;

  /** Private constructor */
  private Filter(FilterType type, String field, String expression, List<Filter> subFilters) {
    this.type = type;
    this.field = field;
    this.expression = expression;
    this.subFilters = subFilters;
  }

  /**
   * Create a text search filter
   *
   * @param field Field name
   * @param value Search value
   * @return FilterQuery
   */
  public static Filter text(String field, String value) {
    validateField(field);
    validateValue(value);

    // Check if value needs escaping or has spaces (don't treat escaped spaces as regular spaces)
    String escapedValue = escapeSpecialCharacters(value);
    boolean needsParens = value.contains(" ") && !value.equals(escapedValue);

    String expr =
        needsParens || value.contains(" ")
            ? String.format("@%s:(%s)", escapeFieldName(field), escapedValue)
            : String.format("@%s:%s", escapeFieldName(field), escapedValue);

    return new Filter(FilterType.TEXT, field, expr, null);
  }

  /**
   * Create a tag filter
   *
   * @param field Field name
   * @param values Tag values
   * @return FilterQuery
   */
  public static Filter tag(String field, String... values) {
    validateField(field);
    if (values == null || values.length == 0) {
      // Return wildcard filter for empty case (graceful fallback)
      return new Filter(FilterType.CUSTOM, field, "*", null);
    }

    String valueStr = values.length == 1 ? values[0] : String.join("|", values);

    String expr = String.format("@%s:{%s}", escapeFieldName(field), valueStr);
    return new Filter(FilterType.TAG, field, expr, null);
  }

  /**
   * Create a numeric filter builder
   *
   * @param field Field name
   * @return NumericFilterBuilder
   */
  public static NumericFilterBuilder numeric(String field) {
    validateField(field);
    return new NumericFilterBuilder(field);
  }

  /**
   * Create a geo filter builder
   *
   * @param field Field name
   * @return GeoFilterBuilder
   */
  public static GeoFilterBuilder geo(String field) {
    validateField(field);
    return new GeoFilterBuilder(field);
  }

  /**
   * Create a wildcard filter
   *
   * @param field Field name
   * @param pattern Wildcard pattern
   * @return FilterQuery
   */
  public static Filter wildcard(String field, String pattern) {
    validateField(field);
    validateValue(pattern);

    String expr = String.format("@%s:%s", escapeFieldName(field), pattern);
    return new Filter(FilterType.TEXT, field, expr, null);
  }

  /**
   * Create a prefix filter
   *
   * @param field Field name
   * @param prefix Prefix value
   * @return FilterQuery
   */
  public static Filter prefix(String field, String prefix) {
    validateField(field);
    validateValue(prefix);

    String expr = String.format("@%s:%s*", escapeFieldName(field), prefix);
    return new Filter(FilterType.TEXT, field, expr, null);
  }

  /**
   * Create a fuzzy match filter
   *
   * @param field Field name
   * @param value Fuzzy match value
   * @return FilterQuery
   */
  public static Filter fuzzy(String field, String value) {
    validateField(field);
    validateValue(value);

    String expr = String.format("@%s:%%%s%%", escapeFieldName(field), value);
    return new Filter(FilterType.TEXT, field, expr, null);
  }

  /**
   * Create an exact match filter
   *
   * @param field Field name
   * @param value Exact match value
   * @return FilterQuery
   */
  public static Filter exact(String field, String value) {
    validateField(field);
    validateValue(value);

    String expr = String.format("@%s:\"%s\"", escapeFieldName(field), value);
    return new Filter(FilterType.TEXT, field, expr, null);
  }

  /**
   * Combine filters with AND
   *
   * @param filters Filters to combine
   * @return FilterQuery
   */
  public static Filter and(Filter... filters) {
    if (filters == null || filters.length == 0) {
      throw new IllegalArgumentException("At least one filter is required");
    }
    return new Filter(FilterType.AND, null, null, Arrays.asList(filters));
  }

  /**
   * Combine filters with OR
   *
   * @param filters Filters to combine
   * @return FilterQuery
   */
  public static Filter or(Filter... filters) {
    if (filters == null || filters.length == 0) {
      throw new IllegalArgumentException("At least one filter is required");
    }
    return new Filter(FilterType.OR, null, null, Arrays.asList(filters));
  }

  /**
   * Negate a filter
   *
   * @param filter Filter to negate
   * @return FilterQuery
   */
  public static Filter not(Filter filter) {
    if (filter == null) {
      throw new IllegalArgumentException("Filter is required");
    }
    return new Filter(FilterType.NOT, null, null, List.of(filter));
  }

  /**
   * Create a custom filter expression
   *
   * @param expression Custom expression
   * @return FilterQuery
   */
  public static Filter custom(String expression) {
    if (expression == null || expression.trim().isEmpty()) {
      throw new IllegalArgumentException("Expression is required");
    }
    return new Filter(FilterType.CUSTOM, null, expression, null);
  }

  /**
   * Create a negated tag filter (tag != value)
   *
   * @param field Field name
   * @param values Tag values to exclude
   * @return FilterQuery
   */
  public static Filter tagNot(String field, String... values) {
    return not(tag(field, values));
  }

  /**
   * Create a negated text filter (text != value)
   *
   * @param field Field name
   * @param value Text value to exclude
   * @return FilterQuery
   */
  public static Filter textNot(String field, String value) {
    return not(text(field, value));
  }

  /**
   * Create a conditional text filter (value1|value2|...)
   *
   * @param field Field name
   * @param pattern Conditional pattern (e.g., "engineer|doctor")
   * @return FilterQuery
   */
  public static Filter conditional(String field, String pattern) {
    validateField(field);
    validateValue(pattern);

    String expr = String.format("@%s:(%s)", escapeFieldName(field), pattern);
    return new Filter(FilterType.TEXT, field, expr, null);
  }

  /**
   * Create a timestamp filter builder
   *
   * @param field Field name
   * @return TimestampFilterBuilder
   */
  public static TimestampFilterBuilder timestamp(String field) {
    validateField(field);
    return new TimestampFilterBuilder(field);
  }

  /**
   * Build the filter query string
   *
   * @return Query string
   */
  public String build() {
    switch (type) {
      case TEXT:
      case TAG:
      case NUMERIC:
      case GEO:
      case CUSTOM:
        return expression;

      case AND:
        // Filter out any wildcard (*) filters since they represent "match all"
        String andQuery =
            subFilters.stream()
                .map(Filter::build)
                .filter(s -> !"*".equals(s)) // Remove wildcard filters
                .collect(Collectors.joining(" "));
        // If all filters were wildcards, return wildcard
        if (andQuery.isEmpty()) {
          return "*";
        }
        return "(" + andQuery + ")";

      case OR:
        String orQuery = subFilters.stream().map(Filter::build).collect(Collectors.joining(" | "));
        return "(" + orQuery + ")";

      case NOT:
        return "-" + subFilters.get(0).build();

      default:
        throw new IllegalStateException("Unknown filter type: " + type);
    }
  }

  /** Validate field name */
  private static void validateField(String field) {
    if (field == null || field.trim().isEmpty()) {
      throw new IllegalArgumentException("Field name is required");
    }
  }

  /** Escape field name for RediSearch query */
  private static String escapeFieldName(String field) {
    if (field == null) return null;

    // For JSONPath fields in RediSearch, we need to escape special characters
    // RediSearch expects double backslash escaping for $ and . in field names
    // e.g., $.price becomes \\$\\.price in the query string
    if (field.startsWith("$.")) {
      // Escape $ and . characters with double backslash for Java string literal
      // This produces \$ and \. in the actual query sent to Redis
      return field.replace("$", "\\$").replace(".", "\\.");
    }
    return field;
  }

  /** Validate value */
  private static void validateValue(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Value is required");
    }
  }

  /** Escape special characters in search queries */
  private static String escapeSpecialCharacters(String value) {
    // Escape Redis search special characters
    return value
        .replace("\\", "\\\\")
        .replace("-", "\\-")
        .replace("@", "\\@")
        .replace(":", "\\:")
        .replace("*", "\\*")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace("+", "\\+")
        .replace("~", "\\~")
        .replace("\"", "\\\"")
        .replace("'", "\\'")
        .replace("/", "\\/")
        .replace("%", "\\%")
        .replace("<", "\\<")
        .replace(">", "\\>")
        .replace("=", "\\=")
        .replace("|", "\\|")
        .replace("&", "\\&")
        .replace("^", "\\^")
        .replace("$", "\\$")
        .replace(".", "\\.")
        .replace(",", "\\,")
        .replace("!", "\\!")
        .replace("?", "\\?")
        .replace(";", "\\;");
  }

  /** Builder for numeric filters */
  public static class NumericFilterBuilder {
    private final String field;

    private NumericFilterBuilder(String field) {
      this.field = field;
    }

    public Filter between(double min, double max) {
      // Always format doubles with decimal point for consistency
      String minStr = String.valueOf(min);
      String maxStr = String.valueOf(max);
      String expr = String.format("@%s:[%s %s]", escapeFieldName(field), minStr, maxStr);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter between(int min, int max) {
      String expr = String.format("@%s:[%d %d]", escapeFieldName(field), min, max);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter gt(double value) {
      String expr = String.format("@%s:[(%s +inf]", escapeFieldName(field), formatNumber(value));
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter gt(int value) {
      String expr = String.format("@%s:[(%d +inf]", escapeFieldName(field), value);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter gte(double value) {
      String valueStr = String.valueOf(value);
      String expr = String.format("@%s:[%s +inf]", escapeFieldName(field), valueStr);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter gte(int value) {
      String expr = String.format("@%s:[%d +inf]", escapeFieldName(field), value);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter lt(double value) {
      String expr = String.format("@%s:[-inf (%s]", escapeFieldName(field), formatNumber(value));
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter lt(int value) {
      String expr = String.format("@%s:[-inf (%d]", escapeFieldName(field), value);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter lte(double value) {
      String expr = String.format("@%s:[-inf %s]", escapeFieldName(field), formatNumber(value));
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter lte(int value) {
      String expr = String.format("@%s:[-inf %d]", escapeFieldName(field), value);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter eq(double value) {
      String expr =
          String.format(
              "@%s:[%s %s]", escapeFieldName(field), formatNumber(value), formatNumber(value));
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter eq(int value) {
      String expr = String.format("@%s:[%d %d]", escapeFieldName(field), value, value);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter ne(double value) {
      return not(eq(value));
    }

    public Filter ne(int value) {
      return not(eq(value));
    }

    private String formatNumber(double value) {
      // Format number without unnecessary decimal places
      if (value == (int) value) {
        return String.valueOf((int) value);
      }
      return String.valueOf(value);
    }
  }

  /** Builder for geo filters */
  public static class GeoFilterBuilder {
    private final String field;

    private GeoFilterBuilder(String field) {
      this.field = field;
    }

    public Filter radius(double lon, double lat, double radius, GeoUnit unit) {
      // Format coordinates without excessive decimal places
      String lonStr = String.valueOf(lon);
      String latStr = String.valueOf(lat);
      String radiusStr = formatNumber(radius);
      String expr =
          String.format(
              "@%s:[%s %s %s %s]",
              escapeFieldName(field), lonStr, latStr, radiusStr, unit.getValue());
      return new Filter(FilterType.GEO, field, expr, null);
    }

    public Filter box(double minLon, double minLat, double maxLon, double maxLat) {
      String expr =
          String.format(
              "@%s:[%f %f %f %f]", escapeFieldName(field), minLon, minLat, maxLon, maxLat);
      return new Filter(FilterType.GEO, field, expr, null);
    }

    public Filter notRadius(double lon, double lat, double radius, GeoUnit unit) {
      return not(radius(lon, lat, radius, unit));
    }

    private String formatNumber(double value) {
      // Format number without unnecessary decimal places
      if (value == (int) value) {
        return String.valueOf((int) value);
      }
      return String.valueOf(value);
    }
  }

  /** Builder for timestamp filters */
  public static class TimestampFilterBuilder {
    private final String field;

    private TimestampFilterBuilder(String field) {
      this.field = field;
    }

    // Original methods with long epoch seconds
    public Filter after(long epochSeconds) {
      String expr = String.format("@%s:[(%d +inf]", escapeFieldName(field), epochSeconds);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter before(long epochSeconds) {
      String expr = String.format("@%s:[-inf (%d]", escapeFieldName(field), epochSeconds);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter between(long startEpochSeconds, long endEpochSeconds) {
      String expr =
          String.format("@%s:[%d %d]", escapeFieldName(field), startEpochSeconds, endEpochSeconds);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    public Filter eq(long epochSeconds) {
      String expr =
          String.format("@%s:[%d %d]", escapeFieldName(field), epochSeconds, epochSeconds);
      return new Filter(FilterType.NUMERIC, field, expr, null);
    }

    // New overloaded methods for Java 8 time API support

    /** Filter for timestamps after the given instant */
    public Filter after(Instant instant) {
      return after(instant.getEpochSecond());
    }

    /** Filter for timestamps after the given datetime (assumes UTC) */
    public Filter after(LocalDateTime dateTime) {
      return after(dateTime.toInstant(ZoneOffset.UTC));
    }

    /** Filter for timestamps after the given zoned datetime */
    public Filter after(ZonedDateTime zonedDateTime) {
      return after(zonedDateTime.toInstant());
    }

    /** Filter for timestamps before the given instant */
    public Filter before(Instant instant) {
      return before(instant.getEpochSecond());
    }

    /** Filter for timestamps before the given datetime (assumes UTC) */
    public Filter before(LocalDateTime dateTime) {
      return before(dateTime.toInstant(ZoneOffset.UTC));
    }

    /** Filter for timestamps before the given zoned datetime */
    public Filter before(ZonedDateTime zonedDateTime) {
      return before(zonedDateTime.toInstant());
    }

    /** Filter for timestamps between the given instants */
    public Filter between(Instant start, Instant end) {
      return between(start.getEpochSecond(), end.getEpochSecond());
    }

    /** Filter for timestamps between the given datetimes (assumes UTC) */
    public Filter between(LocalDateTime start, LocalDateTime end) {
      return between(start.toInstant(ZoneOffset.UTC), end.toInstant(ZoneOffset.UTC));
    }

    /** Filter for timestamps between the given zoned datetimes */
    public Filter between(ZonedDateTime start, ZonedDateTime end) {
      return between(start.toInstant(), end.toInstant());
    }

    /** Filter for timestamps equal to the given instant */
    public Filter eq(Instant instant) {
      return eq(instant.getEpochSecond());
    }

    /** Filter for timestamps equal to the given datetime (assumes UTC) */
    public Filter eq(LocalDateTime dateTime) {
      return eq(dateTime.toInstant(ZoneOffset.UTC));
    }

    /** Filter for timestamps equal to the given zoned datetime */
    public Filter eq(ZonedDateTime zonedDateTime) {
      return eq(zonedDateTime.toInstant());
    }

    /** Filter for timestamps greater than the given instant */
    public Filter gt(Instant instant) {
      return after(instant);
    }

    /** Filter for timestamps greater than the given datetime (assumes UTC) */
    public Filter gt(LocalDateTime dateTime) {
      return after(dateTime);
    }

    /** Filter for timestamps less than the given instant */
    public Filter lt(Instant instant) {
      return before(instant);
    }

    /** Filter for timestamps less than the given datetime (assumes UTC) */
    public Filter lt(LocalDateTime dateTime) {
      return before(dateTime);
    }
  }
}
