package com.redis.vl.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.query.VectorQuery;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Simple Hash Query Test")
class SimpleHashQueryTest extends BaseIntegrationTest {

  private SearchIndex index;

  @AfterEach
  void cleanup() {
    if (index != null) {
      try {
        index.delete(true);
      } catch (Exception e) {
        // Ignore
      }
    }
  }

  @Test
  @DisplayName("Simple hash vector search")
  void simpleHashVectorSearch() {
    // Create minimal hash schema
    Map<String, Object> schema =
        Map.of(
            "index",
                Map.of(
                    "name", "simple-hash",
                    "prefix", "simple",
                    "storage_type", "hash"),
            "fields",
                List.of(
                    Map.of("name", "name", "type", "tag"),
                    Map.of(
                        "name", "embedding",
                        "type", "vector",
                        "attrs",
                            Map.of(
                                "dims", 3,
                                "distance_metric", "cosine",
                                "algorithm", "flat",
                                "datatype", "float32"))));

    // Create index
    index = SearchIndex.fromDict(schema, unifiedJedis);
    index.create(true);

    // Create simple data with byte array vectors
    List<Map<String, Object>> data = new ArrayList<>();
    data.add(Map.of("name", "doc1", "embedding", floatsToBytes(new float[] {0.1f, 0.1f, 0.5f})));
    data.add(Map.of("name", "doc2", "embedding", floatsToBytes(new float[] {0.9f, 0.9f, 0.1f})));

    // Load data
    List<String> keys = index.load(data);
    assertThat(keys).hasSize(2);

    // Wait for indexing
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // Ignore
    }

    // Check index info
    Map<String, Object> info = index.info();
    assertThat(info).containsKey("num_docs");
    // The value could be a Long or String depending on Redis version
    Object numDocs = info.get("num_docs");
    if (numDocs instanceof Long) {
      assertThat(numDocs).isEqualTo(2L);
    } else {
      assertThat(numDocs.toString()).isEqualTo("2");
    }

    // Verify document was stored correctly
    Map<String, Object> doc = index.fetch(keys.get(0));
    assertThat(doc).isNotNull();
    assertThat(doc).containsKeys("name", "embedding");

    // Check what's in the embedding field
    Object embedding = doc.get("embedding");
    assertThat(embedding).isNotNull();
    assertThat(embedding).isInstanceOf(byte[].class);
    assertThat(((byte[]) embedding)).hasSize(12); // 3 floats * 4 bytes

    // Verify Redis storage
    String docKey = keys.get(0);
    Map<String, String> allFields = unifiedJedis.hgetAll(docKey);
    assertThat(allFields).containsKey("name");

    // Verify vector field exists
    byte[] vectorData = unifiedJedis.hget(docKey.getBytes(), "embedding".getBytes());
    assertThat(vectorData).isNotNull();
    assertThat(vectorData).hasSize(12);

    // Check if field exists
    boolean fieldExists = unifiedJedis.hexists(docKey, "embedding");
    assertThat(fieldExists).isTrue();

    // Simple vector query
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("embedding")
            .returnFields("name")
            .numResults(2)
            .build();

    // Execute query
    List<Map<String, Object>> results = index.query(query);

    assertThat(results).isNotEmpty();
  }

  @Test
  @DisplayName("Simple JSON vector search for comparison")
  void simpleJsonVectorSearch() {
    // Create minimal JSON schema
    Map<String, Object> schema =
        Map.of(
            "index",
                Map.of(
                    "name", "simple-json",
                    "prefix", "simple-json",
                    "storage_type", "json"),
            "fields",
                List.of(
                    Map.of("name", "name", "type", "tag"),
                    Map.of(
                        "name", "embedding",
                        "type", "vector",
                        "attrs",
                            Map.of(
                                "dims", 3,
                                "distance_metric", "cosine",
                                "algorithm", "flat",
                                "datatype", "float32"))));

    // Create index
    index = SearchIndex.fromDict(schema, unifiedJedis);
    index.create(true);

    // Create simple data with float array vectors (for JSON)
    List<Map<String, Object>> data = new ArrayList<>();
    data.add(Map.of("name", "doc1", "embedding", new float[] {0.1f, 0.1f, 0.5f}));
    data.add(Map.of("name", "doc2", "embedding", new float[] {0.9f, 0.9f, 0.1f}));

    // Load data
    List<String> keys = index.load(data);
    assertThat(keys).hasSize(2);

    // Wait for indexing
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      // Ignore
    }

    // Simple vector query
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("$.embedding") // JSON uses JSONPath
            .returnFields("name")
            .numResults(2)
            .build();

    // Execute query
    List<Map<String, Object>> results = index.query(query);

    assertThat(results).isNotEmpty();
  }

  private byte[] floatsToBytes(float[] floats) {
    ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    for (float f : floats) {
      buffer.putFloat(f);
    }
    return buffer.array();
  }
}
