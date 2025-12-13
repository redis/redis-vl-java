package com.redis.vl.test.vcr;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for VCRCassetteStore. Tests the cassette storage abstraction without requiring a Redis
 * instance.
 */
@DisplayName("VCRCassetteStore")
class VCRCassetteStoreTest {

  @Nested
  @DisplayName("Cassette Key Format")
  class CassetteKeyFormat {

    @Test
    @DisplayName("should generate proper cassette key format")
    void shouldGenerateProperKeyFormat() {
      String key = VCRCassetteStore.formatKey("embedding", "MyTest.testMethod", 1);
      assertEquals("vcr:embedding:MyTest.testMethod:0001", key);
    }

    @Test
    @DisplayName("should pad call index with zeros")
    void shouldPadCallIndex() {
      assertEquals("vcr:llm:Test.method:0001", VCRCassetteStore.formatKey("llm", "Test.method", 1));
      assertEquals(
          "vcr:llm:Test.method:0042", VCRCassetteStore.formatKey("llm", "Test.method", 42));
      assertEquals(
          "vcr:llm:Test.method:9999", VCRCassetteStore.formatKey("llm", "Test.method", 9999));
    }

    @Test
    @DisplayName("should handle different cassette types")
    void shouldHandleDifferentTypes() {
      assertEquals("vcr:llm:Test:0001", VCRCassetteStore.formatKey("llm", "Test", 1));
      assertEquals("vcr:embedding:Test:0001", VCRCassetteStore.formatKey("embedding", "Test", 1));
      assertEquals("vcr:chat:Test:0001", VCRCassetteStore.formatKey("chat", "Test", 1));
    }

    @Test
    @DisplayName("should parse key components")
    void shouldParseKeyComponents() {
      String key = "vcr:embedding:MyTest.testMethod:0042";
      String[] parts = VCRCassetteStore.parseKey(key);

      assertEquals(4, parts.length);
      assertEquals("vcr", parts[0]);
      assertEquals("embedding", parts[1]);
      assertEquals("MyTest.testMethod", parts[2]);
      assertEquals("0042", parts[3]);
    }
  }

  @Nested
  @DisplayName("Cassette Data Serialization")
  class CassetteDataSerialization {

    @Test
    @DisplayName("should serialize embedding data")
    void shouldSerializeEmbeddingData() {
      float[] embedding = new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
      String testId = "TestClass.testMethod";

      JsonObject cassette = VCRCassetteStore.createEmbeddingCassette(embedding, testId, "model-1");

      assertEquals("embedding", cassette.get("type").getAsString());
      assertEquals(testId, cassette.get("testId").getAsString());
      assertEquals("model-1", cassette.get("model").getAsString());
      assertEquals(5, cassette.getAsJsonArray("embedding").size());
    }

    @Test
    @DisplayName("should deserialize embedding data")
    void shouldDeserializeEmbeddingData() {
      float[] original = new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
      JsonObject cassette =
          VCRCassetteStore.createEmbeddingCassette(original, "test", "all-minilm-l6-v2");

      float[] retrieved = VCRCassetteStore.extractEmbedding(cassette);

      assertNotNull(retrieved);
      assertEquals(original.length, retrieved.length);
      for (int i = 0; i < original.length; i++) {
        assertEquals(original[i], retrieved[i], 0.0001f);
      }
    }

    @Test
    @DisplayName("should serialize batch embedding data")
    void shouldSerializeBatchEmbeddingData() {
      float[][] embeddings =
          new float[][] {{0.1f, 0.2f, 0.3f}, {0.4f, 0.5f, 0.6f}, {0.7f, 0.8f, 0.9f}};

      JsonObject cassette =
          VCRCassetteStore.createBatchEmbeddingCassette(embeddings, "test", "model-1");

      assertEquals("batch_embedding", cassette.get("type").getAsString());
      assertEquals(3, cassette.getAsJsonArray("embeddings").size());
    }

    @Test
    @DisplayName("should deserialize batch embedding data")
    void shouldDeserializeBatchEmbeddingData() {
      float[][] original =
          new float[][] {{0.1f, 0.2f, 0.3f}, {0.4f, 0.5f, 0.6f}, {0.7f, 0.8f, 0.9f}};

      JsonObject cassette =
          VCRCassetteStore.createBatchEmbeddingCassette(original, "test", "model-1");
      float[][] retrieved = VCRCassetteStore.extractBatchEmbeddings(cassette);

      assertNotNull(retrieved);
      assertEquals(original.length, retrieved.length);
      for (int i = 0; i < original.length; i++) {
        assertArrayEquals(original[i], retrieved[i], 0.0001f);
      }
    }

    @Test
    @DisplayName("should handle empty embedding array")
    void shouldHandleEmptyEmbeddingArray() {
      float[] empty = new float[0];
      JsonObject cassette = VCRCassetteStore.createEmbeddingCassette(empty, "test", "model");

      float[] retrieved = VCRCassetteStore.extractEmbedding(cassette);
      assertNotNull(retrieved);
      assertEquals(0, retrieved.length);
    }

    @Test
    @DisplayName("should include metadata in cassette")
    void shouldIncludeMetadata() {
      float[] embedding = new float[] {0.1f, 0.2f};
      JsonObject cassette =
          VCRCassetteStore.createEmbeddingCassette(embedding, "test", "all-minilm-l6-v2");

      assertTrue(cassette.has("timestamp"));
      assertTrue(cassette.get("timestamp").getAsLong() > 0);
    }
  }

  @Nested
  @DisplayName("Null and Edge Cases")
  class NullAndEdgeCases {

    @Test
    @DisplayName("should throw on null embedding")
    void shouldThrowOnNullEmbedding() {
      assertThrows(
          NullPointerException.class,
          () -> VCRCassetteStore.createEmbeddingCassette(null, "test", "model"));
    }

    @Test
    @DisplayName("should throw on null testId")
    void shouldThrowOnNullTestId() {
      assertThrows(
          NullPointerException.class,
          () -> VCRCassetteStore.createEmbeddingCassette(new float[] {0.1f}, null, "model"));
    }

    @Test
    @DisplayName("should throw on null model")
    void shouldThrowOnNullModel() {
      assertThrows(
          NullPointerException.class,
          () -> VCRCassetteStore.createEmbeddingCassette(new float[] {0.1f}, "test", null));
    }

    @Test
    @DisplayName("should return null for invalid key format")
    void shouldReturnNullForInvalidKey() {
      String[] parts = VCRCassetteStore.parseKey("invalid");
      assertNull(parts);
    }

    @Test
    @DisplayName("should extract null from malformed cassette")
    void shouldExtractNullFromMalformedCassette() {
      JsonObject malformed = new JsonObject();
      malformed.addProperty("type", "embedding");
      // Missing embedding field

      float[] result = VCRCassetteStore.extractEmbedding(malformed);
      assertNull(result);
    }
  }
}
