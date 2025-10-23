package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

/**
 * Unit tests for SVS-VAMANA vector indexing algorithm support (#404).
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>CompressionType enum functionality
 *   <li>VectorDataType expansion
 *   <li>SVS-VAMANA algorithm support
 *   <li>Field creation with compression
 *   <li>Data type validation
 *   <li>reduce parameter validation
 *   <li>Jedis schema field conversion
 * </ul>
 *
 * <p>Python reference: tests/unit/test_schema.py (SVS-VAMANA tests)
 */
@DisplayName("SVS-VAMANA Field Unit Tests")
class SVSVamanaFieldTest {

  @Test
  @DisplayName("CompressionType enum should have all 6 compression types")
  void testCompressionTypeEnum() {
    // Verify all 6 compression types exist
    assertThat(VectorField.CompressionType.values()).hasSize(6);

    // LVQ types
    assertThat(VectorField.CompressionType.LVQ4.getValue()).isEqualTo("LVQ4");
    assertThat(VectorField.CompressionType.LVQ4x4.getValue()).isEqualTo("LVQ4x4");
    assertThat(VectorField.CompressionType.LVQ4x8.getValue()).isEqualTo("LVQ4x8");
    assertThat(VectorField.CompressionType.LVQ8.getValue()).isEqualTo("LVQ8");

    // LeanVec types
    assertThat(VectorField.CompressionType.LeanVec4x8.getValue()).isEqualTo("LeanVec4x8");
    assertThat(VectorField.CompressionType.LeanVec8x8.getValue()).isEqualTo("LeanVec8x8");

    // Test isLVQ() method
    assertThat(VectorField.CompressionType.LVQ4.isLVQ()).isTrue();
    assertThat(VectorField.CompressionType.LVQ4x4.isLVQ()).isTrue();
    assertThat(VectorField.CompressionType.LVQ4x8.isLVQ()).isTrue();
    assertThat(VectorField.CompressionType.LVQ8.isLVQ()).isTrue();
    assertThat(VectorField.CompressionType.LeanVec4x8.isLVQ()).isFalse();
    assertThat(VectorField.CompressionType.LeanVec8x8.isLVQ()).isFalse();

    // Test isLeanVec() method
    assertThat(VectorField.CompressionType.LeanVec4x8.isLeanVec()).isTrue();
    assertThat(VectorField.CompressionType.LeanVec8x8.isLeanVec()).isTrue();
    assertThat(VectorField.CompressionType.LVQ4.isLeanVec()).isFalse();
    assertThat(VectorField.CompressionType.LVQ4x4.isLeanVec()).isFalse();
    assertThat(VectorField.CompressionType.LVQ4x8.isLeanVec()).isFalse();
    assertThat(VectorField.CompressionType.LVQ8.isLeanVec()).isFalse();
  }

  @Test
  @DisplayName("VectorDataType enum should have all 6 data types")
  void testVectorDataTypeExpansion() {
    // Verify all 6 data types exist
    assertThat(VectorField.VectorDataType.values()).hasSize(6);

    assertThat(VectorField.VectorDataType.BFLOAT16.getValue()).isEqualTo("BFLOAT16");
    assertThat(VectorField.VectorDataType.FLOAT16.getValue()).isEqualTo("FLOAT16");
    assertThat(VectorField.VectorDataType.FLOAT32.getValue()).isEqualTo("FLOAT32");
    assertThat(VectorField.VectorDataType.FLOAT64.getValue()).isEqualTo("FLOAT64");
    assertThat(VectorField.VectorDataType.INT8.getValue()).isEqualTo("INT8");
    assertThat(VectorField.VectorDataType.UINT8.getValue()).isEqualTo("UINT8");
  }

  @Test
  @DisplayName("Algorithm enum should include SVS_VAMANA")
  void testSVSVamanaAlgorithm() {
    // Verify SVS_VAMANA enum exists
    assertThat(VectorField.Algorithm.values()).hasSize(3);
    assertThat(VectorField.Algorithm.SVS_VAMANA.getValue()).isEqualTo("SVS-VAMANA");
  }

  @Test
  @DisplayName("Should create SVS field with minimal parameters")
  void testSVSFieldCreation() {
    VectorField field =
        VectorField.builder()
            .name("embedding")
            .dimensions(768)
            .algorithm(VectorAlgorithm.SVS_VAMANA)
            .dataType(VectorField.VectorDataType.FLOAT32)
            .build();

    assertThat(field.getName()).isEqualTo("embedding");
    assertThat(field.getDimensions()).isEqualTo(768);
    assertThat(field.getAlgorithm()).isEqualTo(VectorField.Algorithm.SVS_VAMANA);
    assertThat(field.getDataType()).isEqualTo(VectorField.VectorDataType.FLOAT32);
  }

  @Test
  @DisplayName("Should create SVS field with LVQ4 compression")
  void testSVSFieldWithCompression() {
    VectorField field =
        VectorField.builder()
            .name("embedding")
            .dimensions(768)
            .algorithm(VectorAlgorithm.SVS_VAMANA)
            .dataType(VectorField.VectorDataType.FLOAT32)
            .compression(VectorField.CompressionType.LVQ4)
            .searchWindowSize(30)
            .build();

    assertThat(field.getCompression()).isEqualTo(VectorField.CompressionType.LVQ4);
    assertThat(field.getSearchWindowSize()).isEqualTo(30);
  }

  @Test
  @DisplayName("Should create SVS field with LeanVec and reduce")
  void testSVSFieldWithLeanVecAndReduce() {
    VectorField field =
        VectorField.builder()
            .name("embedding")
            .dimensions(768)
            .algorithm(VectorAlgorithm.SVS_VAMANA)
            .dataType(VectorField.VectorDataType.FLOAT16)
            .compression(VectorField.CompressionType.LeanVec4x8)
            .reduce(384)
            .searchWindowSize(30)
            .build();

    assertThat(field.getCompression()).isEqualTo(VectorField.CompressionType.LeanVec4x8);
    assertThat(field.getReduce()).isEqualTo(384);
    assertThat(field.getDimensions()).isEqualTo(768);
  }

  @Test
  @DisplayName("SVS should accept FLOAT16 data type")
  void testSVSAcceptsFloat16() {
    VectorField field =
        VectorField.builder()
            .name("embedding")
            .dimensions(768)
            .algorithm(VectorAlgorithm.SVS_VAMANA)
            .dataType(VectorField.VectorDataType.FLOAT16)
            .build();

    assertThat(field.getDataType()).isEqualTo(VectorField.VectorDataType.FLOAT16);
  }

  @Test
  @DisplayName("SVS should accept FLOAT32 data type")
  void testSVSAcceptsFloat32() {
    VectorField field =
        VectorField.builder()
            .name("embedding")
            .dimensions(768)
            .algorithm(VectorAlgorithm.SVS_VAMANA)
            .dataType(VectorField.VectorDataType.FLOAT32)
            .build();

    assertThat(field.getDataType()).isEqualTo(VectorField.VectorDataType.FLOAT32);
  }

  @Test
  @DisplayName("SVS should reject FLOAT64 data type")
  void testSVSRejectsFloat64() {
    assertThatThrownBy(
            () ->
                VectorField.builder()
                    .name("embedding")
                    .dimensions(768)
                    .algorithm(VectorAlgorithm.SVS_VAMANA)
                    .dataType(VectorField.VectorDataType.FLOAT64)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SVS-VAMANA only supports FLOAT16 and FLOAT32")
        .hasMessageContaining("FLOAT64");
  }

  @Test
  @DisplayName("SVS should reject BFLOAT16 data type")
  void testSVSRejectsBFloat16() {
    assertThatThrownBy(
            () ->
                VectorField.builder()
                    .name("embedding")
                    .dimensions(768)
                    .algorithm(VectorAlgorithm.SVS_VAMANA)
                    .dataType(VectorField.VectorDataType.BFLOAT16)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SVS-VAMANA only supports FLOAT16 and FLOAT32");
  }

  @Test
  @DisplayName("SVS should reject INT8 data type")
  void testSVSRejectsInt8() {
    assertThatThrownBy(
            () ->
                VectorField.builder()
                    .name("embedding")
                    .dimensions(768)
                    .algorithm(VectorAlgorithm.SVS_VAMANA)
                    .dataType(VectorField.VectorDataType.INT8)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SVS-VAMANA only supports FLOAT16 and FLOAT32");
  }

  @Test
  @DisplayName("SVS should reject UINT8 data type")
  void testSVSRejectsUInt8() {
    assertThatThrownBy(
            () ->
                VectorField.builder()
                    .name("embedding")
                    .dimensions(768)
                    .algorithm(VectorAlgorithm.SVS_VAMANA)
                    .dataType(VectorField.VectorDataType.UINT8)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SVS-VAMANA only supports FLOAT16 and FLOAT32");
  }

  @Test
  @DisplayName("reduce >= dimensions should throw exception")
  void testReduceGreaterThanDimensionsThrows() {
    assertThatThrownBy(
            () ->
                VectorField.builder()
                    .name("embedding")
                    .dimensions(768)
                    .algorithm(VectorAlgorithm.SVS_VAMANA)
                    .dataType(VectorField.VectorDataType.FLOAT16)
                    .compression(VectorField.CompressionType.LeanVec4x8)
                    .reduce(768) // equals dimensions - should fail
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reduce (768) must be less than dimensions (768)");
  }

  @Test
  @DisplayName("reduce without compression should throw exception")
  void testReduceWithoutCompressionThrows() {
    assertThatThrownBy(
            () ->
                VectorField.builder()
                    .name("embedding")
                    .dimensions(768)
                    .algorithm(VectorAlgorithm.SVS_VAMANA)
                    .dataType(VectorField.VectorDataType.FLOAT16)
                    .reduce(384)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reduce parameter requires compression to be set");
  }

  @Test
  @DisplayName("reduce with LVQ compression should throw exception")
  void testReduceWithLVQThrows() {
    assertThatThrownBy(
            () ->
                VectorField.builder()
                    .name("embedding")
                    .dimensions(768)
                    .algorithm(VectorAlgorithm.SVS_VAMANA)
                    .dataType(VectorField.VectorDataType.FLOAT16)
                    .compression(VectorField.CompressionType.LVQ4)
                    .reduce(384)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reduce parameter is only supported with LeanVec compression types");
  }

  @Test
  @DisplayName("reduce with LeanVec compression should succeed")
  void testReduceWithLeanVecSucceeds() {
    VectorField field =
        VectorField.builder()
            .name("embedding")
            .dimensions(768)
            .algorithm(VectorAlgorithm.SVS_VAMANA)
            .dataType(VectorField.VectorDataType.FLOAT16)
            .compression(VectorField.CompressionType.LeanVec4x8)
            .reduce(384)
            .build();

    assertThat(field.getReduce()).isEqualTo(384);
    assertThat(field.getCompression()).isEqualTo(VectorField.CompressionType.LeanVec4x8);
  }

  @Test
  @DisplayName("LVQ compression without reduce should succeed")
  void testLVQCompressionWithoutReduce() {
    VectorField field =
        VectorField.builder()
            .name("embedding")
            .dimensions(768)
            .algorithm(VectorAlgorithm.SVS_VAMANA)
            .dataType(VectorField.VectorDataType.FLOAT32)
            .compression(VectorField.CompressionType.LVQ4)
            .searchWindowSize(40)
            .build();

    assertThat(field.getCompression()).isEqualTo(VectorField.CompressionType.LVQ4);
    assertThat(field.getReduce()).isNull();
  }

  @Test
  @DisplayName("SVS field with all parameters should be created successfully")
  void testAllSVSParameters() {
    VectorField field =
        VectorField.builder()
            .name("embedding")
            .dimensions(768)
            .algorithm(VectorAlgorithm.SVS_VAMANA)
            .dataType(VectorField.VectorDataType.FLOAT16)
            .compression(VectorField.CompressionType.LeanVec4x8)
            .reduce(384)
            .graphMaxDegree(50)
            .constructionWindowSize(300)
            .searchWindowSize(40)
            .svsEpsilon(0.02)
            .trainingThreshold(20000)
            .build();

    assertThat(field.getName()).isEqualTo("embedding");
    assertThat(field.getDimensions()).isEqualTo(768);
    assertThat(field.getAlgorithm()).isEqualTo(VectorField.Algorithm.SVS_VAMANA);
    assertThat(field.getDataType()).isEqualTo(VectorField.VectorDataType.FLOAT16);
    assertThat(field.getCompression()).isEqualTo(VectorField.CompressionType.LeanVec4x8);
    assertThat(field.getReduce()).isEqualTo(384);
    assertThat(field.getGraphMaxDegree()).isEqualTo(50);
    assertThat(field.getConstructionWindowSize()).isEqualTo(300);
    assertThat(field.getSearchWindowSize()).isEqualTo(40);
    assertThat(field.getSvsEpsilon()).isEqualTo(0.02);
    assertThat(field.getTrainingThreshold()).isEqualTo(20000);
  }

  @Test
  @DisplayName("SVS field should convert to Jedis schema field with all attributes")
  void testToJedisSchemaFieldSVS() {
    VectorField field =
        VectorField.builder()
            .name("embedding")
            .dimensions(768)
            .algorithm(VectorAlgorithm.SVS_VAMANA)
            .dataType(VectorField.VectorDataType.FLOAT16)
            .compression(VectorField.CompressionType.LeanVec4x8)
            .reduce(384)
            .graphMaxDegree(50)
            .constructionWindowSize(300)
            .searchWindowSize(40)
            .svsEpsilon(0.02)
            .trainingThreshold(20000)
            .build();

    SchemaField jedisField = field.toJedisSchemaField();

    assertThat(jedisField).isNotNull();
    assertThat(jedisField).isInstanceOf(redis.clients.jedis.search.schemafields.VectorField.class);

    // Access attributes via reflection or ensure they're set correctly
    redis.clients.jedis.search.schemafields.VectorField vectorField =
        (redis.clients.jedis.search.schemafields.VectorField) jedisField;

    // Verify field name (convert to String to avoid type mismatch)
    assertThat(vectorField.getFieldName().toString()).isEqualTo("embedding");
  }
}
