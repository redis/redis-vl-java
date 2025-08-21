package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for FilterQuery */
@DisplayName("FilterQuery Tests")
class FilterQueryTest {

  @Test
  @DisplayName("Should create simple text filter")
  void shouldCreateSimpleTextFilter() {
    // When
    Filter filter = Filter.text("title", "redis");

    // Then
    assertThat(filter.build()).isEqualTo("@title:redis");
  }

  @Test
  @DisplayName("Should create text filter with multiple terms")
  void shouldCreateTextFilterWithMultipleTerms() {
    // When
    Filter filter = Filter.text("content", "redis database");

    // Then
    assertThat(filter.build()).isEqualTo("@content:(redis database)");
  }

  @Test
  @DisplayName("Should create tag filter")
  void shouldCreateTagFilter() {
    // When
    Filter filter = Filter.tag("category", "electronics");

    // Then
    assertThat(filter.build()).isEqualTo("@category:{electronics}");
  }

  @Test
  @DisplayName("Should create tag filter with multiple values")
  void shouldCreateTagFilterWithMultipleValues() {
    // When
    Filter filter = Filter.tag("tags", "redis", "database", "nosql");

    // Then
    assertThat(filter.build()).isEqualTo("@tags:{redis|database|nosql}");
  }

  @Test
  @DisplayName("Should create numeric range filter")
  void shouldCreateNumericRangeFilter() {
    // When
    Filter filter = Filter.numeric("price").between(10.0, 50.0);

    // Then
    assertThat(filter.build()).isEqualTo("@price:[10.0 50.0]");
  }

  @Test
  @DisplayName("Should create numeric greater than filter")
  void shouldCreateNumericGreaterThanFilter() {
    // When
    Filter filter = Filter.numeric("score").gt(75);

    // Then
    assertThat(filter.build()).isEqualTo("@score:[(75 +inf]");
  }

  @Test
  @DisplayName("Should create numeric less than filter")
  void shouldCreateNumericLessThanFilter() {
    // When
    Filter filter = Filter.numeric("age").lt(30);

    // Then
    assertThat(filter.build()).isEqualTo("@age:[-inf (30]");
  }

  @Test
  @DisplayName("Should create numeric greater than or equal filter")
  void shouldCreateNumericGreaterThanOrEqualFilter() {
    // When
    Filter filter = Filter.numeric("rating").gte(4.5);

    // Then
    assertThat(filter.build()).isEqualTo("@rating:[4.5 +inf]");
  }

  @Test
  @DisplayName("Should create numeric less than or equal filter")
  void shouldCreateNumericLessThanOrEqualFilter() {
    // When
    Filter filter = Filter.numeric("quantity").lte(100);

    // Then
    assertThat(filter.build()).isEqualTo("@quantity:[-inf 100]");
  }

  @Test
  @DisplayName("Should create geo radius filter")
  void shouldCreateGeoRadiusFilter() {
    // When
    Filter filter = Filter.geo("location").radius(-122.419, 37.774, 5, Filter.GeoUnit.KM);

    // Then
    assertThat(filter.build()).isEqualTo("@location:[-122.419 37.774 5 km]");
  }

  @Test
  @DisplayName("Should combine filters with AND")
  void shouldCombineFiltersWithAnd() {
    // When
    Filter filter =
        Filter.and(
            Filter.text("title", "redis"),
            Filter.tag("category", "database"),
            Filter.numeric("price").between(10, 100));

    // Then
    assertThat(filter.build()).isEqualTo("(@title:redis @category:{database} @price:[10 100])");
  }

  @Test
  @DisplayName("Should combine filters with OR")
  void shouldCombineFiltersWithOr() {
    // When
    Filter filter =
        Filter.or(Filter.tag("category", "electronics"), Filter.tag("category", "computers"));

    // Then
    assertThat(filter.build()).isEqualTo("(@category:{electronics} | @category:{computers})");
  }

  @Test
  @DisplayName("Should negate filter with NOT")
  void shouldNegateFilterWithNot() {
    // When
    Filter filter = Filter.not(Filter.tag("status", "discontinued"));

    // Then
    assertThat(filter.build()).isEqualTo("-@status:{discontinued}");
  }

  @Test
  @DisplayName("Should create complex nested filter")
  void shouldCreateComplexNestedFilter() {
    // When
    Filter filter =
        Filter.and(
            Filter.text("title", "redis"),
            Filter.or(Filter.tag("category", "database"), Filter.tag("category", "cache")),
            Filter.not(Filter.tag("status", "deprecated")),
            Filter.numeric("rating").gte(4.0));

    // Then
    String query = filter.build();
    assertThat(query).contains("@title:redis");
    assertThat(query).contains("@category:{database} | @category:{cache}");
    assertThat(query).contains("-@status:{deprecated}");
    assertThat(query).contains("@rating:[4.0 +inf]");
  }

  @Test
  @DisplayName("Should create wildcard filter")
  void shouldCreateWildcardFilter() {
    // When
    Filter filter = Filter.wildcard("title", "red*");

    // Then
    assertThat(filter.build()).isEqualTo("@title:red*");
  }

  @Test
  @DisplayName("Should create prefix filter")
  void shouldCreatePrefixFilter() {
    // When
    Filter filter = Filter.prefix("sku", "PROD");

    // Then
    assertThat(filter.build()).isEqualTo("@sku:PROD*");
  }

  @Test
  @DisplayName("Should create fuzzy match filter")
  void shouldCreateFuzzyMatchFilter() {
    // When
    Filter filter = Filter.fuzzy("title", "helo");

    // Then
    assertThat(filter.build()).isEqualTo("@title:%helo%");
  }

  @Test
  @DisplayName("Should create exact match filter")
  void shouldCreateExactMatchFilter() {
    // When
    Filter filter = Filter.exact("title", "Redis in Action");

    // Then
    assertThat(filter.build()).isEqualTo("@title:\"Redis in Action\"");
  }

  @Test
  @DisplayName("Should validate required field name")
  void shouldValidateRequiredFieldName() {
    // Null field name
    assertThatThrownBy(() -> Filter.text(null, "value"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field name is required");

    // Empty field name
    assertThatThrownBy(() -> Filter.text("", "value"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field name is required");
  }

  @Test
  @DisplayName("Should validate required value")
  void shouldValidateRequiredValue() {
    // Null value
    assertThatThrownBy(() -> Filter.text("field", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Value is required");
  }

  @Test
  @DisplayName("Should escape special characters")
  void shouldEscapeSpecialCharacters() {
    // When
    Filter filter = Filter.text("title", "C++ Programming");

    // Then - With spaces, it should use parentheses
    assertThat(filter.build()).isEqualTo("@title:(C\\+\\+ Programming)");
  }

  @Test
  @DisplayName("Should support custom filter expression")
  void shouldSupportCustomFilterExpression() {
    // When
    Filter filter = Filter.custom("@year:[2020 2023] @status:{active}");

    // Then
    assertThat(filter.build()).isEqualTo("@year:[2020 2023] @status:{active}");
  }

  @Test
  @DisplayName("Should create tag NOT filter")
  void shouldCreateTagNotFilter() {
    // Test single value negation
    Filter query = Filter.tagNot("status", "inactive");
    String result = query.build();
    assertThat(result).isEqualTo("-@status:{inactive}");

    // Test multiple values negation
    Filter multiQuery = Filter.tagNot("category", "electronics", "books", "toys");
    String multiResult = multiQuery.build();
    assertThat(multiResult).isEqualTo("-@category:{electronics|books|toys}");
  }

  @Test
  @DisplayName("Should create text NOT filter")
  void shouldCreateTextNotFilter() {
    Filter query = Filter.textNot("description", "obsolete");
    String result = query.build();
    assertThat(result).isEqualTo("-@description:obsolete");

    // Test with phrase
    Filter phraseQuery = Filter.textNot("title", "out of stock");
    String phraseResult = phraseQuery.build();
    assertThat(phraseResult).isEqualTo("-@title:(out of stock)");
  }

  @Test
  @DisplayName("Should create conditional filter")
  void shouldCreateConditionalFilter() {
    // Test conditional with wildcard pattern
    Filter query = Filter.conditional("status", "active*");
    String result = query.build();
    assertThat(result).isEqualTo("@status:(active*)");

    // Test conditional with complex pattern
    Filter complexQuery = Filter.conditional("code", "ABC-*-2023");
    String complexResult = complexQuery.build();
    assertThat(complexResult).isEqualTo("@code:(ABC-*-2023)");
  }

  @Test
  @DisplayName("Should create timestamp filters")
  void shouldCreateTimestampFilters() {
    // Test timestamp after
    Filter.TimestampFilterBuilder afterBuilder = Filter.timestamp("created_at");
    Filter afterQuery = afterBuilder.after(1609459200L); // 2021-01-01 00:00:00 UTC
    String afterResult = afterQuery.build();
    assertThat(afterResult).isEqualTo("@created_at:[(1609459200 +inf]");

    // Test timestamp before
    Filter.TimestampFilterBuilder beforeBuilder = Filter.timestamp("updated_at");
    Filter beforeQuery = beforeBuilder.before(1640995200L); // 2022-01-01 00:00:00 UTC
    String beforeResult = beforeQuery.build();
    assertThat(beforeResult).isEqualTo("@updated_at:[-inf (1640995200]");

    // Test timestamp between
    Filter.TimestampFilterBuilder betweenBuilder = Filter.timestamp("modified_at");
    Filter betweenQuery = betweenBuilder.between(1609459200L, 1640995200L);
    String betweenResult = betweenQuery.build();
    assertThat(betweenResult).isEqualTo("@modified_at:[1609459200 1640995200]");

    // Test timestamp equals
    Filter.TimestampFilterBuilder eqBuilder = Filter.timestamp("exact_time");
    Filter eqQuery = eqBuilder.eq(1625097600L); // 2021-07-01 00:00:00 UTC
    String eqResult = eqQuery.build();
    assertThat(eqResult).isEqualTo("@exact_time:[1625097600 1625097600]");
  }

  @Test
  @DisplayName("Should create numeric shorthand filters")
  void shouldCreateNumericShorthandFilters() {
    // Test gt (greater than)
    Filter gtQuery = Filter.numeric("score").gt(90);
    String gtResult = gtQuery.build();
    assertThat(gtResult).isEqualTo("@score:[(90 +inf]");

    // Test lt (less than)
    Filter ltQuery = Filter.numeric("age").lt(18);
    String ltResult = ltQuery.build();
    assertThat(ltResult).isEqualTo("@age:[-inf (18]");

    // Test lte (less than or equal)
    Filter lteQuery = Filter.numeric("price").lte(99.99);
    String lteResult = lteQuery.build();
    assertThat(lteResult).isEqualTo("@price:[-inf 99.99]");

    // Test ne (not equal) for double
    Filter neDoubleQuery = Filter.numeric("temperature").ne(98.6);
    String neDoubleResult = neDoubleQuery.build();
    assertThat(neDoubleResult).isEqualTo("-@temperature:[98.6 98.6]");

    // Test ne (not equal) for int
    Filter neIntQuery = Filter.numeric("count").ne(0);
    String neIntResult = neIntQuery.build();
    assertThat(neIntResult).isEqualTo("-@count:[0 0]");
  }

  @Test
  @DisplayName("Should create geo NOT radius filter")
  void shouldCreateGeoNotRadiusFilter() {
    // Test NOT within radius
    Filter query = Filter.geo("location").notRadius(-122.4194, 37.7749, 10, Filter.GeoUnit.KM);
    String result = query.build();
    assertThat(result).isEqualTo("-@location:[-122.4194 37.7749 10 km]");
  }

  @Test
  @DisplayName("Should create geo box filter")
  void shouldCreateGeoBoxFilter() {
    // Test geo box filter
    Filter query = Filter.geo("area").box(-122.5, 37.7, -122.3, 37.8);
    String result = query.build();
    assertThat(result).isEqualTo("@area:[-122.500000 37.700000 -122.300000 37.800000]");
  }

  @Test
  @DisplayName("Should handle GeoUnit conversions")
  void shouldHandleGeoUnitConversions() {
    // Test meters
    Filter metersQuery = Filter.geo("loc").radius(0, 0, 1000, Filter.GeoUnit.M);
    assertThat(metersQuery.build()).contains("1000 m");

    // Test miles
    Filter milesQuery = Filter.geo("loc").radius(0, 0, 5, Filter.GeoUnit.MI);
    assertThat(milesQuery.build()).contains("5 mi");

    // Test feet
    Filter feetQuery = Filter.geo("loc").radius(0, 0, 5280, Filter.GeoUnit.FT);
    assertThat(feetQuery.build()).contains("5280 ft");

    // Test kilometers (already tested but for completeness)
    Filter kmQuery = Filter.geo("loc").radius(0, 0, 10, Filter.GeoUnit.KM);
    assertThat(kmQuery.build()).contains("10 km");
  }

  @Test
  @DisplayName("Should combine NOT filters with AND/OR")
  void shouldCombineNotFiltersWithLogicalOperators() {
    // Test combining NOT filters with AND
    Filter notHighCredit = Filter.tagNot("credit_score", "high");
    Filter notOldAge = Filter.numeric("age").lt(65); // not old
    Filter combined = Filter.and(notHighCredit, notOldAge);

    String result = combined.build();
    assertThat(result).contains("-@credit_score:{high}");
    assertThat(result).contains("@age:[-inf (65]");

    // Test combining NOT filters with OR
    Filter notActive = Filter.tagNot("status", "active");
    Filter expired = Filter.timestamp("expiry").before(System.currentTimeMillis() / 1000);
    Filter orCombined = Filter.or(notActive, expired);

    String orResult = orCombined.build();
    assertThat(orResult).contains("-@status:{active}");
    assertThat(orResult).contains("|");
  }

  @Test
  @DisplayName("Should validate required parameters")
  void shouldValidateRequiredParameters() {
    // Test null field name throws exception
    assertThrows(IllegalArgumentException.class, () -> Filter.tagNot(null, "value"));

    assertThrows(IllegalArgumentException.class, () -> Filter.textNot("", "value"));

    assertThrows(IllegalArgumentException.class, () -> Filter.conditional("field", null));
  }

  @Test
  @DisplayName("Should handle edge cases in timestamp filters")
  void shouldHandleEdgeCasesInTimestampFilters() {
    // Test with 0 timestamp
    Filter zeroQuery = Filter.timestamp("created").after(0);
    assertThat(zeroQuery.build()).isEqualTo("@created:[(0 +inf]");

    // Test with negative timestamp (before epoch)
    Filter negativeQuery = Filter.timestamp("historical").after(-86400);
    assertThat(negativeQuery.build()).isEqualTo("@historical:[(-86400 +inf]");

    // Test with very large timestamp
    Filter futureQuery = Filter.timestamp("future").before(2147483647L);
    assertThat(futureQuery.build()).isEqualTo("@future:[-inf (2147483647]");
  }

  @Test
  @DisplayName("Should create timestamp filters with Java datetime objects")
  void shouldCreateTimestampFiltersWithDateTimeObjects() {
    // Test with Instant
    Instant instant = Instant.ofEpochSecond(1742147139L);
    Filter afterInstant = Filter.timestamp("last_updated").after(instant);
    assertThat(afterInstant.build()).isEqualTo("@last_updated:[(1742147139 +inf]");

    Filter beforeInstant = Filter.timestamp("last_updated").before(instant);
    assertThat(beforeInstant.build()).isEqualTo("@last_updated:[-inf (1742147139]");

    // Test with LocalDateTime (assumes UTC)
    LocalDateTime dateTime = LocalDateTime.of(2025, 3, 16, 13, 45, 39);
    long expectedEpoch = dateTime.toInstant(ZoneOffset.UTC).getEpochSecond();

    Filter afterDateTime = Filter.timestamp("last_updated").after(dateTime);
    assertThat(afterDateTime.build()).isEqualTo("@last_updated:[(" + expectedEpoch + " +inf]");

    Filter beforeDateTime = Filter.timestamp("last_updated").before(dateTime);
    assertThat(beforeDateTime.build()).isEqualTo("@last_updated:[-inf (" + expectedEpoch + "]");

    // Test with ZonedDateTime
    ZonedDateTime zonedDateTime =
        ZonedDateTime.of(2025, 3, 16, 13, 45, 39, 0, ZoneId.of("America/New_York"));
    long zonedEpoch = zonedDateTime.toInstant().getEpochSecond();

    Filter afterZoned = Filter.timestamp("last_updated").after(zonedDateTime);
    assertThat(afterZoned.build()).isEqualTo("@last_updated:[(" + zonedEpoch + " +inf]");

    Filter beforeZoned = Filter.timestamp("last_updated").before(zonedDateTime);
    assertThat(beforeZoned.build()).isEqualTo("@last_updated:[-inf (" + zonedEpoch + "]");
  }

  @Test
  @DisplayName("Should create timestamp between filters with datetime objects")
  void shouldCreateTimestampBetweenFiltersWithDateTimeObjects() {
    // Test between with Instant
    Instant start = Instant.ofEpochSecond(1736880339L);
    Instant end = Instant.ofEpochSecond(1742147139L);

    Filter betweenInstants = Filter.timestamp("last_updated").between(start, end);
    assertThat(betweenInstants.build()).isEqualTo("@last_updated:[1736880339 1742147139]");

    // Test between with LocalDateTime
    LocalDateTime startDt = LocalDateTime.of(2025, 1, 14, 13, 45, 39);
    LocalDateTime endDt = LocalDateTime.of(2025, 3, 16, 13, 45, 39);
    long startEpoch = startDt.toInstant(ZoneOffset.UTC).getEpochSecond();
    long endEpoch = endDt.toInstant(ZoneOffset.UTC).getEpochSecond();

    Filter betweenDates = Filter.timestamp("last_updated").between(startDt, endDt);
    assertThat(betweenDates.build())
        .isEqualTo("@last_updated:[" + startEpoch + " " + endEpoch + "]");
  }

  @Test
  @DisplayName("Should create timestamp gt and lt filters with datetime objects")
  void shouldCreateTimestampGtLtFiltersWithDateTimeObjects() {
    LocalDateTime dateTime = LocalDateTime.of(2025, 3, 16, 13, 45, 39);
    long expectedEpoch = dateTime.toInstant(ZoneOffset.UTC).getEpochSecond();

    // Test gt (greater than) - same as after
    Filter gtFilter = Filter.timestamp("last_updated").gt(dateTime);
    assertThat(gtFilter.build()).isEqualTo("@last_updated:[(" + expectedEpoch + " +inf]");

    // Test lt (less than) - same as before
    Filter ltFilter = Filter.timestamp("last_updated").lt(dateTime);
    assertThat(ltFilter.build()).isEqualTo("@last_updated:[-inf (" + expectedEpoch + "]");

    // Test with Instant
    Instant instant = Instant.ofEpochSecond(1742147139L);
    Filter gtInstant = Filter.timestamp("last_updated").gt(instant);
    assertThat(gtInstant.build()).isEqualTo("@last_updated:[(1742147139 +inf]");

    Filter ltInstant = Filter.timestamp("last_updated").lt(instant);
    assertThat(ltInstant.build()).isEqualTo("@last_updated:[-inf (1742147139]");
  }
}
