package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

/** Unit tests for VectorField */
@DisplayName("VectorField Tests")
class VectorFieldTest {

  @Test
  @DisplayName("Should create VectorField with name and dimensions")
  void shouldCreateVectorFieldWithNameAndDimensions() {
    // Given
    String fieldName = "embedding";
    int dimensions = 768;

    // When
    VectorField field = new VectorField(fieldName, dimensions);

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getFieldType()).isEqualTo(FieldType.VECTOR);
    assertThat(field.getDimensions()).isEqualTo(dimensions);
    assertThat(field.getAlgorithm()).isEqualTo(VectorField.Algorithm.FLAT);
    assertThat(field.getDistanceMetric()).isEqualTo(VectorField.DistanceMetric.COSINE);
    assertThat(field.getDataType()).isEqualTo(VectorField.VectorDataType.FLOAT32);
    assertThat(field.isIndexed()).isTrue();
  }

  @Test
  @DisplayName("Should create FLAT VectorField with builder")
  void shouldCreateFlatVectorFieldWithBuilder() {
    // Given
    String fieldName = "text_embedding";
    int dimensions = 1536;

    // When
    VectorField field =
        VectorField.builder()
            .name(fieldName)
            .dimensions(dimensions)
            .algorithm(VectorAlgorithm.FLAT)
            .distanceMetric(VectorField.DistanceMetric.L2)
            .dataType(VectorField.VectorDataType.FLOAT32)
            .initialCapacity(1000)
            .blockSize(100)
            .build();

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getDimensions()).isEqualTo(dimensions);
    assertThat(field.getAlgorithm()).isEqualTo(VectorField.Algorithm.FLAT);
    assertThat(field.getDistanceMetric()).isEqualTo(VectorField.DistanceMetric.L2);
    assertThat(field.getDataType()).isEqualTo(VectorField.VectorDataType.FLOAT32);
    assertThat(field.getInitialCapacity()).isEqualTo(1000);
    assertThat(field.getBlockSize()).isEqualTo(100);
  }

  @Test
  @DisplayName("Should create HNSW VectorField with builder")
  void shouldCreateHnswVectorFieldWithBuilder() {
    // Given
    String fieldName = "image_embedding";
    int dimensions = 2048;

    // When
    VectorField field =
        VectorField.builder()
            .name(fieldName)
            .dimensions(dimensions)
            .algorithm(VectorAlgorithm.HNSW)
            .distanceMetric(VectorField.DistanceMetric.IP)
            .dataType(VectorField.VectorDataType.FLOAT32)
            .hnswM(16)
            .hnswEfConstruction(200)
            .hnswEfRuntime(10)
            .epsilon(0.01)
            .build();

    // Then
    assertThat(field.getName()).isEqualTo(fieldName);
    assertThat(field.getDimensions()).isEqualTo(dimensions);
    assertThat(field.getAlgorithm()).isEqualTo(VectorField.Algorithm.HNSW);
    assertThat(field.getDistanceMetric()).isEqualTo(VectorField.DistanceMetric.IP);
    assertThat(field.getHnswM()).isEqualTo(16);
    assertThat(field.getHnswEfConstruction()).isEqualTo(200);
    assertThat(field.getHnswEfRuntime()).isEqualTo(10);
    assertThat(field.getEpsilon()).isEqualTo(0.01);
  }

  @Test
  @DisplayName("Should convert to Jedis VectorField")
  void shouldConvertToJedisVectorField() {
    // Given
    VectorField field =
        VectorField.builder()
            .name("vector")
            .dimensions(384)
            .algorithm(VectorAlgorithm.FLAT)
            .distanceMetric(VectorField.DistanceMetric.COSINE)
            .build();

    // When
    SchemaField jedisField = field.toJedisSchemaField();

    // Then
    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.VectorField.class);

    redis.clients.jedis.search.schemafields.VectorField vectorField =
        (redis.clients.jedis.search.schemafields.VectorField) jedisField;
    assertThat(vectorField.getName()).isEqualTo("vector");
  }

  @Test
  @DisplayName("Should create VectorField with fluent API")
  void shouldCreateVectorFieldWithFluentApi() {
    // When
    VectorField field =
        VectorField.of("content_vector", 512)
            .withAlias("doc_vector")
            .withAlgorithm(VectorAlgorithm.HNSW)
            .withDistanceMetric(VectorField.DistanceMetric.L2)
            .withHnswM(32)
            .withHnswEfConstruction(400)
            .build();

    // Then
    assertThat(field.getName()).isEqualTo("content_vector");
    assertThat(field.getAlias()).isEqualTo("doc_vector");
    assertThat(field.getDimensions()).isEqualTo(512);
    assertThat(field.getAlgorithm()).isEqualTo(VectorField.Algorithm.HNSW);
    assertThat(field.getDistanceMetric()).isEqualTo(VectorField.DistanceMetric.L2);
    assertThat(field.getHnswM()).isEqualTo(32);
    assertThat(field.getHnswEfConstruction()).isEqualTo(400);
  }

  @Test
  @DisplayName("Should serialize VectorField to JSON")
  void shouldSerializeToJson() {
    // Given
    VectorField field =
        VectorField.builder()
            .name("embedding")
            .dimensions(768)
            .algorithm(VectorAlgorithm.HNSW)
            .distanceMetric(VectorField.DistanceMetric.COSINE)
            .build();

    // When
    String json = field.toJson();

    // Then
    assertThat(json).contains("\"name\":\"embedding\"");
    assertThat(json).contains("\"type\":\"VECTOR\"");
    assertThat(json).contains("\"dimensions\":768");
    assertThat(json).contains("\"algorithm\":\"HNSW\"");
    assertThat(json).contains("\"distanceMetric\":\"COSINE\"");
  }

  @Test
  @DisplayName("Should validate dimensions are positive")
  void shouldValidateDimensionsArePositive() {
    // When/Then
    assertThatThrownBy(() -> new VectorField("embedding", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Dimensions must be positive");

    assertThatThrownBy(() -> new VectorField("embedding", -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Dimensions must be positive");
  }

  @Test
  @DisplayName("Should support different vector data types")
  void shouldSupportDifferentVectorDataTypes() {
    // Given
    VectorField float32Field =
        VectorField.builder()
            .name("float32_vec")
            .dimensions(128)
            .dataType(VectorField.VectorDataType.FLOAT32)
            .build();

    VectorField float64Field =
        VectorField.builder()
            .name("float64_vec")
            .dimensions(128)
            .dataType(VectorField.VectorDataType.FLOAT64)
            .build();

    // Then
    assertThat(float32Field.getDataType()).isEqualTo(VectorField.VectorDataType.FLOAT32);
    assertThat(float64Field.getDataType()).isEqualTo(VectorField.VectorDataType.FLOAT64);
  }
}
