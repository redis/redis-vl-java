package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.schemafields.SchemaField;

/** Unit tests for TextField */
@DisplayName("TextField Tests")
class TextFieldTest {

  @Test
  @DisplayName("Should create TextField with name")
  void shouldCreateTextFieldWithName() {
    // Given
    String fieldName = "description";

    // When
    TextField field = new TextField(fieldName);

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getFieldType()).isEqualTo(FieldType.TEXT);
    assertThat(field.isIndexed()).isTrue();
    assertThat(field.isSortable()).isFalse();
    assertThat(field.isNoStem()).isFalse();
    assertThat(field.getWeight()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Should create TextField with builder")
  void shouldCreateTextFieldWithBuilder() {
    // Given
    String fieldName = "content";
    String alias = "doc_content";
    double weight = 2.5;

    // When
    TextField field =
        TextField.builder()
            .name(fieldName)
            .alias(alias)
            .sortable(true)
            .noStem(true)
            .weight(weight)
            .phonetic("dm:en")
            .build();

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getAlias()).isEqualTo(alias);
    assertThat(field.isSortable()).isTrue();
    assertThat(field.isNoStem()).isTrue();
    assertThat(field.getWeight()).isEqualTo(weight);
    assertThat(field.getPhonetic()).isEqualTo("dm:en");
  }

  @Test
  @DisplayName("Should convert to Jedis TextField")
  void shouldConvertToJedisTextField() {
    // Given
    TextField field =
        TextField.builder().name("title").sortable(true).weight(3.0).noStem(true).build();

    // When
    SchemaField jedisField = field.toJedisSchemaField();

    // Then
    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.TextField.class);

    redis.clients.jedis.search.schemafields.TextField textField =
        (redis.clients.jedis.search.schemafields.TextField) jedisField;
    assertThat(textField.getName()).isEqualTo("title");
  }

  @Test
  @DisplayName("Should create TextField with fluent API")
  void shouldCreateTextFieldWithFluentApi() {
    // When
    TextField field =
        TextField.of("summary")
            .withAlias("doc_summary")
            .withWeight(1.5)
            .sortable()
            .noStem()
            .build();

    // Then
    assertThat(field.getName()).isEqualTo("summary");
    assertThat(field.getAlias()).isEqualTo("doc_summary");
    assertThat(field.getWeight()).isEqualTo(1.5);
    assertThat(field.isSortable()).isTrue();
    assertThat(field.isNoStem()).isTrue();
  }

  @Test
  @DisplayName("Should serialize TextField to JSON")
  void shouldSerializeToJson() {
    // Given
    TextField field = TextField.builder().name("body").weight(2.0).noStem(true).build();

    // When
    String json = field.toJson();

    // Then
    assertThat(json).contains("\"name\":\"body\"");
    assertThat(json).contains("\"type\":\"TEXT\"");
    assertThat(json).contains("\"weight\":2.0");
    assertThat(json).contains("\"noStem\":true");
  }
}
