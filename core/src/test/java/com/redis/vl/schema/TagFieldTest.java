package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.schemafields.SchemaField;

/** Unit tests for TagField */
@DisplayName("TagField Tests")
class TagFieldTest {

  @Test
  @DisplayName("Should create TagField with name")
  void shouldCreateTagFieldWithName() {
    // Given
    String fieldName = "category";

    // When
    TagField field = new TagField(fieldName);

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getFieldType()).isEqualTo(FieldType.TAG);
    assertThat(field.isIndexed()).isTrue();
    assertThat(field.isSortable()).isFalse();
    assertThat(field.isCaseSensitive()).isFalse();
    assertThat(field.getSeparator()).isEqualTo(",");
  }

  @Test
  @DisplayName("Should create TagField with builder")
  void shouldCreateTagFieldWithBuilder() {
    // Given
    String fieldName = "tags";
    String alias = "doc_tags";
    String separator = "|";

    // When
    TagField field =
        TagField.builder()
            .name(fieldName)
            .alias(alias)
            .sortable(true)
            .caseSensitive(true)
            .separator(separator)
            .build();

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getAlias()).isEqualTo(alias);
    assertThat(field.isSortable()).isTrue();
    assertThat(field.isCaseSensitive()).isTrue();
    assertThat(field.getSeparator()).isEqualTo(separator);
  }

  @Test
  @DisplayName("Should convert to Jedis TagField")
  void shouldConvertToJedisTagField() {
    // Given
    TagField field =
        TagField.builder().name("status").sortable(true).caseSensitive(true).separator(";").build();

    // When
    SchemaField jedisField = field.toJedisSchemaField();

    // Then
    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.TagField.class);

    redis.clients.jedis.search.schemafields.TagField tagField =
        (redis.clients.jedis.search.schemafields.TagField) jedisField;
    assertThat(tagField.getName()).isEqualTo("status");
  }

  @Test
  @DisplayName("Should create TagField with fluent API")
  void shouldCreateTagFieldWithFluentApi() {
    // When
    TagField field =
        TagField.of("labels")
            .withAlias("doc_labels")
            .withSeparator("|")
            .sortable()
            .caseSensitive()
            .build();

    // Then
    assertThat(field.getName()).isEqualTo("labels");
    assertThat(field.getAlias()).isEqualTo("doc_labels");
    assertThat(field.getSeparator()).isEqualTo("|");
    assertThat(field.isSortable()).isTrue();
    assertThat(field.isCaseSensitive()).isTrue();
  }

  @Test
  @DisplayName("Should serialize TagField to JSON")
  void shouldSerializeToJson() {
    // Given
    TagField field = TagField.builder().name("keywords").separator("|").caseSensitive(true).build();

    // When
    String json = field.toJson();

    // Then
    assertThat(json).contains("\"name\":\"keywords\"");
    assertThat(json).contains("\"type\":\"TAG\"");
    assertThat(json).contains("\"separator\":\"|\"");
    assertThat(json).contains("\"caseSensitive\":true");
  }

  @Test
  @DisplayName("Should handle empty separator as default")
  void shouldHandleEmptySeparatorAsDefault() {
    // Given
    TagField field = TagField.builder().name("tags").separator("").build();

    // Then
    assertThat(field.getSeparator()).isEqualTo(",");
  }
}
