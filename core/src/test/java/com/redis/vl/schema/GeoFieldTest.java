package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.schemafields.SchemaField;

/** Unit tests for GeoField */
@DisplayName("GeoField Tests")
class GeoFieldTest {

  @Test
  @DisplayName("Should create GeoField with name")
  void shouldCreateGeoFieldWithName() {
    // Given
    String fieldName = "location";

    // When
    GeoField field = new GeoField(fieldName);

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getFieldType()).isEqualTo(FieldType.GEO);
    assertThat(field.isIndexed()).isTrue();
    assertThat(field.isSortable()).isFalse();
  }

  @Test
  @DisplayName("Should create GeoField with builder")
  void shouldCreateGeoFieldWithBuilder() {
    // Given
    String fieldName = "coordinates";
    String alias = "geo_coords";

    // When
    GeoField field =
        GeoField.builder().name(fieldName).alias(alias).sortable(true).indexed(true).build();

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getAlias()).isEqualTo(alias);
    assertThat(field.isSortable()).isTrue();
    assertThat(field.isIndexed()).isTrue();
  }

  @Test
  @DisplayName("Should convert to Jedis GeoField")
  void shouldConvertToJedisGeoField() {
    // Given
    GeoField field = GeoField.builder().name("store_location").sortable(true).build();

    // When
    SchemaField jedisField = field.toJedisSchemaField();

    // Then
    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.GeoField.class);

    redis.clients.jedis.search.schemafields.GeoField geoField =
        (redis.clients.jedis.search.schemafields.GeoField) jedisField;
    assertThat(geoField.getName()).isEqualTo("store_location");
  }

  @Test
  @DisplayName("Should create GeoField with fluent API")
  void shouldCreateGeoFieldWithFluentApi() {
    // When
    GeoField field = GeoField.of("poi").withAlias("point_of_interest").sortable().build();

    // Then
    assertThat(field.getName()).isEqualTo("poi");
    assertThat(field.getAlias()).isEqualTo("point_of_interest");
    assertThat(field.isSortable()).isTrue();
  }

  @Test
  @DisplayName("Should serialize GeoField to JSON")
  void shouldSerializeToJson() {
    // Given
    GeoField field = GeoField.builder().name("address").sortable(true).build();

    // When
    String json = field.toJson();

    // Then
    assertThat(json).contains("\"name\":\"address\"");
    assertThat(json).contains("\"type\":\"GEO\"");
    assertThat(json).contains("\"sortable\":true");
  }
}
