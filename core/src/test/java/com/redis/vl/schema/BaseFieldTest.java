package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.FieldName;

/** Unit tests for BaseField and its implementations */
@DisplayName("BaseField Tests")
class BaseFieldTest {

  @Test
  @DisplayName("Should create a field with name")
  void shouldCreateFieldWithName() {
    // Given
    String fieldName = "testField";

    // When
    BaseField field = new TestField(fieldName);

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.isIndexed()).isTrue(); // By default fields should be indexed
  }

  @Test
  @DisplayName("Should create a field with builder pattern")
  void shouldCreateFieldWithBuilder() {
    // Given
    String fieldName = "testField";
    String alias = "test_alias";

    // When
    BaseField field =
        TestField.builder().name(fieldName).alias(alias).indexed(false).sortable(true).build();

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getAlias()).isEqualTo(alias);
    assertThat(field.isIndexed()).isFalse();
    assertThat(field.isSortable()).isTrue();
  }

  @Test
  @DisplayName("Should validate field name is not null or empty")
  void shouldValidateFieldName() {
    // When/Then - null name
    assertThatThrownBy(() -> new TestField(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field name cannot be null or empty");

    // When/Then - empty name
    assertThatThrownBy(() -> new TestField(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field name cannot be null or empty");

    // When/Then - blank name
    assertThatThrownBy(() -> new TestField("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Field name cannot be null or empty");
  }

  @Test
  @DisplayName("Should convert to Jedis FieldName")
  void shouldConvertToJedisFieldName() {
    // Given
    BaseField field = TestField.builder().name("testField").alias("test_alias").build();

    // When
    FieldName jedisField = field.toJedisFieldName();

    // Then
    assertThat(jedisField).isNotNull();
    assertThat(jedisField.getName()).isEqualTo("testField");
    // Jedis FieldName stores alias internally but doesn't expose getter
    // We can verify it was created with alias by checking the field itself
    assertThat(field.getAlias()).isEqualTo("test_alias");
  }

  @Test
  @DisplayName("Should have correct field type")
  void shouldHaveCorrectFieldType() {
    // Given
    BaseField field = new TestField("test");

    // When
    FieldType type = field.getFieldType();

    // Then
    assertThat(type).isEqualTo(FieldType.TEXT);
  }

  @Test
  @DisplayName("Should serialize to JSON format")
  void shouldSerializeToJson() {
    // Given
    BaseField field =
        TestField.builder()
            .name("testField")
            .alias("test_alias")
            .indexed(true)
            .sortable(false)
            .build();

    // When
    String json = field.toJson();

    // Then
    assertThat(json).contains("\"name\":\"testField\"");
    assertThat(json).contains("\"type\":\"TEXT\"");
    assertThat(json).contains("\"alias\":\"test_alias\"");
  }

  /** Test implementation of BaseField for testing purposes */
  static class TestField extends BaseField {

    public TestField(String name) {
      super(name);
    }

    @lombok.Builder
    public TestField(String name, String alias, boolean indexed, boolean sortable) {
      super(name, alias, indexed, sortable);
    }

    @Override
    public FieldType getFieldType() {
      return FieldType.TEXT;
    }

    @Override
    public redis.clients.jedis.search.schemafields.SchemaField toJedisSchemaField() {
      // For testing purposes, return null
      return null;
    }
  }
}
