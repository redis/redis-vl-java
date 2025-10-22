package com.redis.vl.query;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for skip_decode parameter in query classes (issue #252).
 *
 * <p>Ported from Python: tests/unit/test_skip_decode_fields.py
 *
 * <p>Python reference: PR #389 - skip_decode parameter for return_fields method
 */
@DisplayName("Skip Decode Fields Tests")
class SkipDecodeFieldsTest {

  @Test
  @DisplayName("FilterQuery: Should accept skip_decode for single field")
  void testFilterQuerySkipDecodeSingleField() {
    // Python: query.return_fields("title", "year", "embedding", skip_decode=["embedding"])
    FilterQuery query =
        FilterQuery.builder()
            .returnFields(List.of("title", "year", "embedding"))
            .skipDecodeFields(List.of("embedding"))
            .build();

    // Check that fields are set correctly
    assertThat(query.getReturnFields()).contains("title", "year", "embedding");

    // Check that skip_decode settings are tracked
    assertThat(query.getSkipDecodeFields()).contains("embedding");
  }

  @Test
  @DisplayName("FilterQuery: Should accept skip_decode for multiple fields")
  void testFilterQuerySkipDecodeMultipleFields() {
    // Python: skip_decode=["embedding", "image_data"]
    FilterQuery query =
        FilterQuery.builder()
            .returnFields(List.of("title", "year", "embedding", "image_data"))
            .skipDecodeFields(List.of("embedding", "image_data"))
            .build();

    assertThat(query.getReturnFields()).hasSize(4);
    assertThat(query.getSkipDecodeFields()).contains("embedding", "image_data").hasSize(2);
  }

  @Test
  @DisplayName("VectorQuery: Should accept skip_decode for single field")
  void testVectorQuerySkipDecodeSingleField() {
    // Python: query.return_fields("id", "vector_field", "metadata", skip_decode=["vector_field"])
    VectorQuery query =
        VectorQuery.builder()
            .field("vector_field")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .returnFields("id", "vector_field", "metadata")
            .skipDecodeFields(List.of("vector_field"))
            .build();

    assertThat(query.getReturnFields()).contains("id", "vector_field", "metadata");
    assertThat(query.getSkipDecodeFields()).contains("vector_field");
  }

  @Test
  @DisplayName("VectorRangeQuery: Should accept skip_decode")
  void testVectorRangeQuerySkipDecode() {
    // Python: query.return_fields("doc_id", "text", "embedding", skip_decode=["embedding"])
    VectorRangeQuery query =
        VectorRangeQuery.builder()
            .field("embedding")
            .vector(new float[] {0.1f, 0.2f, 0.3f})
            .returnFields("doc_id", "text", "embedding")
            .skipDecodeFields(List.of("embedding"))
            .build();

    assertThat(query.getReturnFields()).contains("doc_id", "text", "embedding");
    assertThat(query.getSkipDecodeFields()).contains("embedding");
  }

  @Test
  @DisplayName("Should handle empty skip_decode list")
  void testSkipDecodeEmptyList() {
    // Python: skip_decode=[]
    FilterQuery query =
        FilterQuery.builder()
            .returnFields(List.of("field1", "field2", "field3"))
            .skipDecodeFields(Collections.emptyList())
            .build();

    assertThat(query.getReturnFields()).hasSize(3);
    assertThat(query.getSkipDecodeFields()).isEmpty();
  }

  @Test
  @DisplayName("Should default to empty skip_decode (backwards compatible)")
  void testSkipDecodeNoneDefault() {
    // Python: No skip_decode parameter (backwards compatibility)
    FilterQuery query = FilterQuery.builder().returnFields(List.of("field1", "field2")).build();

    assertThat(query.getReturnFields()).hasSize(2);
    // Should have empty skip_decode by default
    assertThat(query.getSkipDecodeFields()).isEmpty();
  }

  @Test
  @DisplayName("Should accept skip_decode with varargs")
  void testSkipDecodeVarargs() {
    // Java convenience: accept varargs in addition to List
    FilterQuery query =
        FilterQuery.builder()
            .returnFields(List.of("field1", "field2", "field3"))
            .skipDecodeFields("field1", "field3")
            .build();

    assertThat(query.getSkipDecodeFields()).contains("field1", "field3").hasSize(2);
  }

  @Test
  @DisplayName("TextQuery: Should accept skip_decode")
  void testTextQuerySkipDecode() {
    TextQuery query =
        TextQuery.builder()
            .text("search query")
            .textField("description")
            .skipDecodeFields(List.of("embedding", "image_data"))
            .build();

    assertThat(query.getSkipDecodeFields()).contains("embedding", "image_data");
  }

  @Test
  @DisplayName("Should validate skip_decode field names are not null")
  void testSkipDecodeValidation() {
    // Null list should be handled gracefully (empty)
    FilterQuery query1 =
        FilterQuery.builder()
            .returnFields(List.of("field1"))
            .skipDecodeFields((List<String>) null)
            .build();

    assertThat(query1.getSkipDecodeFields()).isEmpty();

    // Null values in list should be rejected
    assertThatThrownBy(
            () ->
                FilterQuery.builder()
                    .returnFields(List.of("field1"))
                    .skipDecodeFields(Arrays.asList("field1", null))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot contain null");
  }

  @Test
  @DisplayName("Should allow fields in skip_decode that are not in return_fields")
  void testSkipDecodeFieldNotInReturnFields() {
    // Python: Allows this - skip_decode field might not be in return_fields
    FilterQuery query =
        FilterQuery.builder()
            .returnFields(List.of("field1", "field2"))
            .skipDecodeFields(List.of("field3"))
            .build();

    assertThat(query.getReturnFields()).hasSize(2);
    assertThat(query.getSkipDecodeFields()).contains("field3");
  }
}
