package com.redis.vl.query;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for multi-field sorting in query classes.
 *
 * <p>Ported from Python: tests/unit/test_multi_field_sorting.py
 */
@DisplayName("Multi-Field Sorting Tests")
class MultiFieldSortingTest {

  @Test
  @DisplayName("FilterQuery: Should accept single field string (backward compatible)")
  void testFilterQuerySingleFieldString() {
    // Python: FilterQuery(sort_by="price")
    FilterQuery query = FilterQuery.builder().sortBy("price").build();

    assertThat(query.getSortBy()).isEqualTo("price");
    // Default is ascending - Query should be built successfully
    assertThat(query.buildRedisQuery()).isNotNull();
  }

  @Test
  @DisplayName("FilterQuery: Should accept single field with direction tuple")
  void testFilterQuerySingleFieldWithDirection() {
    // Python: FilterQuery(sort_by=("price", "DESC"))
    FilterQuery query = FilterQuery.builder().sortBy("price", "DESC").build();

    assertThat(query.getSortBy()).isEqualTo("price");
    // Should build Redis query successfully
    assertThat(query.buildRedisQuery()).isNotNull();
  }

  @Test
  @DisplayName("FilterQuery: Should accept single SortField")
  void testFilterQuerySingleSortField() {
    // Python: FilterQuery(sort_by=("rating", "DESC"))
    FilterQuery query = FilterQuery.builder().sortBy(SortField.desc("rating")).build();

    assertThat(query.getSortBy()).isEqualTo("rating");
  }

  @Test
  @DisplayName("FilterQuery: Should accept multiple fields as list")
  void testFilterQueryMultipleFields() {
    // Python: FilterQuery(sort_by=[("price", "DESC"), ("rating", "ASC"), "stock"])
    List<SortField> sortFields =
        List.of(SortField.desc("price"), SortField.asc("rating"), SortField.asc("stock"));

    FilterQuery query = FilterQuery.builder().sortBy(sortFields).build();

    // Only first field is used (Redis limitation)
    assertThat(query.getSortBy()).isEqualTo("price");
  }

  @Test
  @DisplayName("FilterQuery: Should log warning for multiple fields")
  void testFilterQueryMultipleFieldsWarning() {
    // Python: logs warning "Multiple sort fields specified" and "Using first field: 'price'"
    // This is tested in integration tests with caplog
    List<SortField> sortFields = List.of(SortField.desc("price"), SortField.asc("rating"));

    FilterQuery query = FilterQuery.builder().sortBy(sortFields).build();

    // Should use only first field
    assertThat(query.getSortBy()).isEqualTo("price");
  }

  @Test
  @DisplayName("FilterQuery: Should handle empty list gracefully")
  void testFilterQueryEmptyList() {
    // Python: FilterQuery(sort_by=[], num_results=10) - handled gracefully
    FilterQuery query = FilterQuery.builder().sortBy(List.of()).build();

    // Should have no sort field
    assertThat(query.getSortBy()).isNull();
  }

  @Test
  @DisplayName("FilterQuery: Should reject invalid direction")
  void testFilterQueryInvalidDirection() {
    // Python: raises ValueError "Sort direction must be 'ASC' or 'DESC'"
    assertThatThrownBy(() -> FilterQuery.builder().sortBy("price", "INVALID").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Sort direction must be 'ASC' or 'DESC'");
  }

  @Test
  @DisplayName("FilterQuery: Backward compatibility with sortAscending method")
  void testFilterQueryBackwardCompatibility() {
    // Python: query.sort_by("price", asc=False)
    FilterQuery query = FilterQuery.builder().sortBy("price").sortAscending(false).build();

    assertThat(query.getSortBy()).isEqualTo("price");
    // Should be descending - Query should be built successfully
    assertThat(query.buildRedisQuery()).isNotNull();
  }

  @Test
  @DisplayName("VectorQuery: Should accept single field string")
  void testVectorQuerySingleFieldString() {
    // Python: VectorQuery(..., sort_by="price")
    VectorQuery query =
        VectorQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .sortBy("price")
            .build();

    assertThat(query.getSortBy()).isEqualTo("price");
  }

  @Test
  @DisplayName("VectorQuery: Should accept single field with direction")
  void testVectorQuerySingleFieldWithDirection() {
    // Python: VectorQuery(..., sort_by=("price", "ASC"))
    VectorQuery query =
        VectorQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .sortBy("price", "ASC")
            .build();

    assertThat(query.getSortBy()).isEqualTo("price");
    assertThat(query.isSortDescending()).isFalse();
  }

  @Test
  @DisplayName("VectorQuery: Should accept single SortField")
  void testVectorQuerySingleSortField() {
    VectorQuery query =
        VectorQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .sortBy(SortField.desc("rating"))
            .build();

    assertThat(query.getSortBy()).isEqualTo("rating");
    assertThat(query.isSortDescending()).isTrue();
  }

  @Test
  @DisplayName("VectorQuery: Should accept multiple fields")
  void testVectorQueryMultipleFields() {
    // Python: VectorQuery(..., sort_by=[("rating", "DESC"), "price"])
    List<SortField> sortFields = List.of(SortField.desc("rating"), SortField.asc("price"));

    VectorQuery query =
        VectorQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .sortBy(sortFields)
            .build();

    // Only first field is used
    assertThat(query.getSortBy()).isEqualTo("rating");
    assertThat(query.isSortDescending()).isTrue();
  }

  @Test
  @DisplayName("VectorRangeQuery: Should accept single field string")
  void testVectorRangeQuerySingleFieldString() {
    VectorRangeQuery query =
        VectorRangeQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .sortBy("price")
            .build();

    assertThat(query.getSortBy()).isEqualTo("price");
  }

  @Test
  @DisplayName("VectorRangeQuery: Should accept single field with direction")
  void testVectorRangeQuerySingleFieldWithDirection() {
    VectorRangeQuery query =
        VectorRangeQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .sortBy("price", "DESC")
            .build();

    assertThat(query.getSortBy()).isEqualTo("price");
    assertThat(query.isSortDescending()).isTrue();
  }

  @Test
  @DisplayName("VectorRangeQuery: Should accept single SortField")
  void testVectorRangeQuerySingleSortField() {
    VectorRangeQuery query =
        VectorRangeQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .sortBy(SortField.asc("rating"))
            .build();

    assertThat(query.getSortBy()).isEqualTo("rating");
    assertThat(query.isSortDescending()).isFalse();
  }

  @Test
  @DisplayName("VectorRangeQuery: Should accept multiple fields")
  void testVectorRangeQueryMultipleFields() {
    List<SortField> sortFields = List.of(SortField.desc("price"), SortField.asc("stock"));

    VectorRangeQuery query =
        VectorRangeQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .sortBy(sortFields)
            .build();

    // Only first field is used
    assertThat(query.getSortBy()).isEqualTo("price");
    assertThat(query.isSortDescending()).isTrue();
  }

  @Test
  @DisplayName("TextQuery: Should accept single field string")
  void testTextQuerySingleFieldString() {
    // Python: TextQuery(..., sort_by=("price", "DESC"))
    TextQuery query =
        TextQuery.builder().text("search query").textField("description").sortBy("price").build();

    assertThat(query.getSortBy()).isEqualTo("price");
  }

  @Test
  @DisplayName("TextQuery: Should accept single field with direction")
  void testTextQuerySingleFieldWithDirection() {
    TextQuery query =
        TextQuery.builder()
            .text("search query")
            .textField("description")
            .sortBy("price", "DESC")
            .build();

    assertThat(query.getSortBy()).isEqualTo("price");
    assertThat(query.isSortDescending()).isTrue();
  }

  @Test
  @DisplayName("TextQuery: Should accept single SortField")
  void testTextQuerySingleSortField() {
    TextQuery query =
        TextQuery.builder()
            .text("search query")
            .textField("description")
            .sortBy(SortField.asc("rating"))
            .build();

    assertThat(query.getSortBy()).isEqualTo("rating");
    assertThat(query.isSortDescending()).isFalse();
  }

  @Test
  @DisplayName("TextQuery: Should accept multiple fields")
  void testTextQueryMultipleFields() {
    List<SortField> sortFields = List.of(SortField.asc("price"), SortField.desc("rating"));

    TextQuery query =
        TextQuery.builder()
            .text("search query")
            .textField("description")
            .sortBy(sortFields)
            .build();

    // Only first field is used
    assertThat(query.getSortBy()).isEqualTo("price");
    assertThat(query.isSortDescending()).isFalse();
  }
}
