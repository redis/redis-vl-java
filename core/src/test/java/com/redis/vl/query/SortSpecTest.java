package com.redis.vl.query;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SortSpec utility class.
 *
 * <p>Ported from Python: tests/unit/test_multi_field_sorting.py
 *
 * <p>Python reference: SortSpec type alias and _parse_sort_spec() method
 */
@DisplayName("SortSpec Tests")
class SortSpecTest {

  @Test
  @DisplayName("Should parse single field string (backward compatible)")
  void testParseSingleFieldString() {
    // Python: sort_by="price"
    List<SortField> result = SortSpec.parseSortSpec("price");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getFieldName()).isEqualTo("price");
    assertThat(result.get(0).isAscending()).isTrue();
    assertThat(result.get(0).getDirection()).isEqualTo("ASC");
  }

  @Test
  @DisplayName("Should parse single field with ASC direction")
  void testParseSingleFieldWithAscDirection() {
    // Python: sort_by=("price", "ASC")
    List<SortField> result = SortSpec.parseSortSpec("price", "ASC");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getFieldName()).isEqualTo("price");
    assertThat(result.get(0).isAscending()).isTrue();
  }

  @Test
  @DisplayName("Should parse single field with DESC direction")
  void testParseSingleFieldWithDescDirection() {
    // Python: sort_by=("rating", "DESC")
    List<SortField> result = SortSpec.parseSortSpec("rating", "DESC");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getFieldName()).isEqualTo("rating");
    assertThat(result.get(0).isAscending()).isFalse();
    assertThat(result.get(0).getDirection()).isEqualTo("DESC");
  }

  @Test
  @DisplayName("Should parse single SortField")
  void testParseSingleSortField() {
    SortField field = SortField.desc("price");
    List<SortField> result = SortSpec.parseSortSpec(field);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getFieldName()).isEqualTo("price");
    assertThat(result.get(0).isAscending()).isFalse();
  }

  @Test
  @DisplayName("Should parse multiple fields as list of SortFields")
  void testParseMultipleSortFields() {
    // Python: sort_by=[("price", "DESC"), ("rating", "ASC"), ("age", "DESC")]
    List<SortField> fields =
        List.of(SortField.desc("price"), SortField.asc("rating"), SortField.desc("age"));

    List<SortField> result = SortSpec.parseSortSpec(fields);

    assertThat(result).hasSize(3);
    assertThat(result.get(0).getFieldName()).isEqualTo("price");
    assertThat(result.get(0).isAscending()).isFalse();
    assertThat(result.get(1).getFieldName()).isEqualTo("rating");
    assertThat(result.get(1).isAscending()).isTrue();
    assertThat(result.get(2).getFieldName()).isEqualTo("age");
    assertThat(result.get(2).isAscending()).isFalse();
  }

  @Test
  @DisplayName("Should reject invalid sort direction")
  void testRejectInvalidSortDirection() {
    // Python: raises ValueError "Sort direction must be 'ASC' or 'DESC'"
    assertThatThrownBy(() -> SortSpec.parseSortSpec("price", "INVALID"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Sort direction must be 'ASC' or 'DESC'");

    assertThatThrownBy(() -> SortSpec.parseSortSpec("price", "ascending"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Sort direction must be 'ASC' or 'DESC'");
  }

  @Test
  @DisplayName("Should handle null field name")
  void testHandleNullFieldName() {
    assertThatThrownBy(() -> SortSpec.parseSortSpec((String) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field name cannot be null or empty");
  }

  @Test
  @DisplayName("Should handle empty field name")
  void testHandleEmptyFieldName() {
    assertThatThrownBy(() -> SortSpec.parseSortSpec(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field name cannot be null or empty");

    assertThatThrownBy(() -> SortSpec.parseSortSpec("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field name cannot be null or empty");
  }

  @Test
  @DisplayName("Should handle empty list")
  void testHandleEmptyList() {
    // Python: empty list is handled gracefully - returns empty list
    List<SortField> result = SortSpec.parseSortSpec(List.of());
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should handle null list")
  void testHandleNullList() {
    List<SortField> result = SortSpec.parseSortSpec((List<SortField>) null);
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should normalize direction strings to uppercase")
  void testNormalizeDirectionStrings() {
    // Should accept lowercase and convert to uppercase
    List<SortField> result1 = SortSpec.parseSortSpec("price", "asc");
    assertThat(result1.get(0).getDirection()).isEqualTo("ASC");

    List<SortField> result2 = SortSpec.parseSortSpec("price", "desc");
    assertThat(result2.get(0).getDirection()).isEqualTo("DESC");

    List<SortField> result3 = SortSpec.parseSortSpec("price", "AsC");
    assertThat(result3.get(0).getDirection()).isEqualTo("ASC");
  }
}
