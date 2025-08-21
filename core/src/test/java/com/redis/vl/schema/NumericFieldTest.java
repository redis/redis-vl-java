package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.schemafields.SchemaField;

/** Unit tests for NumericField */
@DisplayName("NumericField Tests")
class NumericFieldTest {

  @Test
  @DisplayName("Should create NumericField with name")
  void shouldCreateNumericFieldWithName() {
    // Given
    String fieldName = "price";

    // When
    NumericField field = new NumericField(fieldName);

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getFieldType()).isEqualTo(FieldType.NUMERIC);
    assertThat(field.isIndexed()).isTrue();
    assertThat(field.isSortable()).isFalse();
  }

  @Test
  @DisplayName("Should create NumericField with builder")
  void shouldCreateNumericFieldWithBuilder() {
    // Given
    String fieldName = "age";
    String alias = "user_age";

    // When
    NumericField field =
        NumericField.builder().name(fieldName).alias(alias).sortable(true).indexed(true).build();

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getAlias()).isEqualTo(alias);
    assertThat(field.isSortable()).isTrue();
    assertThat(field.isIndexed()).isTrue();
  }

  @Test
  @DisplayName("Should convert to Jedis NumericField")
  void shouldConvertToJedisNumericField() {
    // Given
    NumericField field = NumericField.builder().name("score").sortable(true).build();

    // When
    SchemaField jedisField = field.toJedisSchemaField();

    // Then
    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.NumericField.class);

    redis.clients.jedis.search.schemafields.NumericField numericField =
        (redis.clients.jedis.search.schemafields.NumericField) jedisField;
    assertThat(numericField.getName()).isEqualTo("score");
  }

  @Test
  @DisplayName("Should create NumericField with fluent API")
  void shouldCreateNumericFieldWithFluentApi() {
    // When
    NumericField field = NumericField.of("rating").withAlias("product_rating").sortable().build();

    // Then
    assertThat(field.getName()).isEqualTo("rating");
    assertThat(field.getAlias()).isEqualTo("product_rating");
    assertThat(field.isSortable()).isTrue();
  }

  @Test
  @DisplayName("Should serialize NumericField to JSON")
  void shouldSerializeToJson() {
    // Given
    NumericField field = NumericField.builder().name("quantity").sortable(true).build();

    // When
    String json = field.toJson();

    // Then
    assertThat(json).contains("\"name\":\"quantity\"");
    assertThat(json).contains("\"type\":\"NUMERIC\"");
    assertThat(json).contains("\"sortable\":true");
  }
}
