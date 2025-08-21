package com.redis.vl.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.query.Filter;
import com.redis.vl.query.VectorQuery;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.razorvine.pickle.Unpickler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Integration tests that exactly match the 05_hash_vs_json.ipynb notebook */
@DisplayName("Notebook Hash vs JSON Integration Tests")
class NotebookHashVsJsonTest extends BaseIntegrationTest {

  private SearchIndex hashIndex;
  private SearchIndex jsonIndex;
  private SearchIndex bikeIndex;
  private List<Map<String, Object>> hybridData;

  @BeforeEach
  void setup() throws Exception {
    // Load the exact pickle data used in the notebook
    loadHybridExampleData();
  }

  @AfterEach
  void cleanup() {
    cleanupIndex(hashIndex);
    cleanupIndex(jsonIndex);
    cleanupIndex(bikeIndex);
  }

  private void cleanupIndex(SearchIndex index) {
    if (index != null) {
      try {
        index.delete(true);
      } catch (Exception e) {
        // Ignore
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void loadHybridExampleData() throws Exception {
    // Load pickle data from test resources
    InputStream stream = getClass().getResourceAsStream("/hybrid_example_data.pkl");
    if (stream == null) {
      throw new RuntimeException("Could not find hybrid_example_data.pkl in test resources");
    }

    Unpickler unpickler = new Unpickler();
    hybridData = (List<Map<String, Object>>) unpickler.load(stream);
    stream.close();

    assertThat(hybridData).isNotEmpty();

    // Verify sample data structure
    if (!hybridData.isEmpty()) {
      Map<String, Object> sample = hybridData.get(0);
      assertThat(sample).containsKey("user");
      assertThat(sample).containsKey("user_embedding");

      Object embedding = sample.get("user_embedding");
      if (embedding instanceof byte[]) {
        assertThat(((byte[]) embedding)).isNotEmpty();
      }
    }
  }

  @Test
  @DisplayName("Test hash storage matching notebook example")
  void testHashStorageAsInNotebook() throws InterruptedException {
    // Define the hash index schema exactly as in notebook
    Map<String, Object> hashSchema =
        Map.of(
            "index",
                Map.of(
                    "name", "user-hash",
                    "prefix", "user-hash-docs",
                    "storage_type", "hash" // default setting -- HASH
                    ),
            "fields",
                List.of(
                    Map.of("name", "user", "type", "tag"),
                    Map.of("name", "credit_score", "type", "tag"),
                    Map.of("name", "job", "type", "text"),
                    Map.of("name", "age", "type", "numeric"),
                    Map.of("name", "office_location", "type", "geo"),
                    Map.of(
                        "name", "user_embedding",
                        "type", "vector",
                        "attrs",
                            Map.of(
                                "dims", 3,
                                "distance_metric", "cosine",
                                "algorithm", "flat",
                                "datatype", "float32"))));

    // Create index
    hashIndex = SearchIndex.fromDict(hashSchema, unifiedJedis);
    hashIndex.create(true);

    assertThat(hashIndex.getStorageType().toString()).isEqualTo("HASH");

    // Load hash data (vectors remain as byte arrays)
    List<String> keys = hashIndex.load(hybridData, "user");
    assertThat(keys).hasSize(7);

    // Wait for indexing
    Thread.sleep(1000);

    // Create the exact combined filter expression from notebook
    Filter creditFilter = Filter.tag("credit_score", "high");
    Filter jobFilter = Filter.prefix("job", "enginee");
    Filter ageFilter = Filter.numeric("age").gt(17);
    Filter combinedFilter = Filter.and(creditFilter, jobFilter, ageFilter);

    // Create vector query with filter - exactly as in notebook
    VectorQuery v =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job", "office_location")
            .withPreFilter(combinedFilter.build())
            .build();

    // Execute query
    List<Map<String, Object>> results = hashIndex.query(v);

    // The query should find users matching the criteria
    // With the filter: credit_score=high AND job starts with "enginee" AND age > 17
    // This should match users like john (engineer, high credit, age > 17)
    assertThat(results).as("Hash query with filters should return results").isNotEmpty();

    // Verify the results match our filter criteria
    for (Map<String, Object> result : results) {
      String creditScore = (String) result.get("credit_score");
      String job = (String) result.get("job");
      Object ageObj = result.get("age");

      assertThat(creditScore).isEqualTo("high");
      assertThat(job).startsWith("enginee");

      if (ageObj != null) {
        int age = Integer.parseInt(ageObj.toString());
        assertThat(age).isGreaterThan(17);
      }
    }
  }

  @Test
  @DisplayName("Test JSON storage matching notebook example")
  void testJsonStorageAsInNotebook() throws InterruptedException {
    // Define the json index schema exactly as in notebook
    Map<String, Object> jsonSchema =
        Map.of(
            "index",
                Map.of(
                    "name", "user-json",
                    "prefix", "user-json-docs",
                    "storage_type", "json" // JSON storage type
                    ),
            "fields",
                List.of(
                    Map.of("name", "user", "type", "tag"),
                    Map.of("name", "credit_score", "type", "tag"),
                    Map.of("name", "job", "type", "text"),
                    Map.of("name", "age", "type", "numeric"),
                    Map.of("name", "office_location", "type", "geo"),
                    Map.of(
                        "name", "user_embedding",
                        "type", "vector",
                        "attrs",
                            Map.of(
                                "dims", 3,
                                "distance_metric", "cosine",
                                "algorithm", "flat",
                                "datatype", "float32"))));

    // Create index
    jsonIndex = SearchIndex.fromDict(jsonSchema, unifiedJedis);
    jsonIndex.create(true);

    // Convert byte array vectors to float arrays for JSON storage
    List<Map<String, Object>> jsonData = new ArrayList<>();
    for (Map<String, Object> user : hybridData) {
      Map<String, Object> jsonUser = new HashMap<>(user);

      // Convert byte array embedding to float array for JSON
      Object embedding = user.get("user_embedding");
      if (embedding instanceof byte[]) {
        byte[] embBytes = (byte[]) embedding;
        ByteBuffer buffer = ByteBuffer.wrap(embBytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] floats = new float[3];
        for (int i = 0; i < 3; i++) {
          floats[i] = buffer.getFloat();
        }
        jsonUser.put("user_embedding", floats);
      }

      jsonData.add(jsonUser);
    }

    // Load JSON data
    List<String> jsonKeys = jsonIndex.load(jsonData, "user");
    assertThat(jsonKeys).hasSize(7);

    // Wait for indexing
    Thread.sleep(1000);

    // Use the same query as for hash
    Filter creditFilter = Filter.tag("$.credit_score", "high");
    Filter jobFilter = Filter.prefix("$.job", "enginee");
    Filter ageFilter = Filter.numeric("$.age").gt(17);
    Filter combinedFilter = Filter.and(creditFilter, jobFilter, ageFilter);

    VectorQuery v =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("$.user_embedding")
            .returnFields("user", "credit_score", "age", "job", "office_location")
            .withPreFilter(combinedFilter.build())
            .build();

    // Execute query
    List<Map<String, Object>> jsonResults = jsonIndex.query(v);

    // Should get same results as hash query
    assertThat(jsonResults).as("JSON query with filters should return results").isNotEmpty();
  }

  @Test
  @DisplayName("Test nested JSON with bike data as in notebook")
  void testNestedJsonBikeDataAsInNotebook() throws InterruptedException {
    // Create bike data with nested metadata - exactly as in notebook
    List<Map<String, Object>> bikeData = new ArrayList<>();

    // Bike 1
    Map<String, Object> bike1 = new HashMap<>();
    bike1.put("name", "Specialized Stumpjumper");
    Map<String, Object> metadata1 =
        Map.of(
            "model", "Stumpjumper",
            "brand", "Specialized",
            "type", "Enduro bikes",
            "price", 3000);
    bike1.put("metadata", metadata1);
    String desc1 =
        "The Specialized Stumpjumper is a versatile enduro bike that dominates both climbs and descents. "
            + "Features a FACT 11m carbon fiber frame, FOX FLOAT suspension with 160mm travel, and SRAM X01 Eagle drivetrain. "
            + "The asymmetric frame design and internal storage compartment make it a practical choice for all-day adventures.";
    bike1.put("description", desc1);

    // For testing, use a simple embedding (notebook will use real embeddings)
    float[] embedding1 = new float[384];
    for (int i = 0; i < 384; i++) {
      embedding1[i] = (float) (Math.random() * 0.1);
    }
    bike1.put("bike_embedding", embedding1);

    // Bike 2
    Map<String, Object> bike2 = new HashMap<>();
    bike2.put("name", "bike_2");
    Map<String, Object> metadata2 =
        Map.of(
            "model", "Slash",
            "brand", "Trek",
            "type", "Enduro bikes",
            "price", 5000);
    bike2.put("metadata", metadata2);
    String desc2 =
        "Trek's Slash is built for aggressive enduro riding and racing. "
            + "Featuring Trek's Alpha Aluminum frame with RE:aktiv suspension technology, 160mm travel, and Knock Block frame protection. "
            + "Equipped with Bontrager components and a Shimano XT drivetrain, this bike excels on technical trails and enduro race courses.";
    bike2.put("description", desc2);

    float[] embedding2 = new float[384];
    for (int i = 0; i < 384; i++) {
      embedding2[i] = (float) (Math.random() * 0.1 + 0.05);
    }
    bike2.put("bike_embedding", embedding2);

    bikeData.add(bike1);
    bikeData.add(bike2);

    // Define bike schema with nested JSON paths - exactly as in notebook
    Map<String, Object> bikeSchema =
        Map.of(
            "index",
                Map.of(
                    "name", "bike-json",
                    "prefix", "bike-json",
                    "storage_type", "json"),
            "fields",
                List.of(
                    Map.of(
                        "name", "model",
                        "type", "tag",
                        "path", "$.metadata.model"),
                    Map.of(
                        "name", "brand",
                        "type", "tag",
                        "path", "$.metadata.brand"),
                    Map.of(
                        "name", "price",
                        "type", "numeric",
                        "path", "$.metadata.price"),
                    Map.of(
                        "name", "bike_embedding",
                        "type", "vector",
                        "attrs",
                            Map.of(
                                "dims", 384,
                                "distance_metric", "cosine",
                                "algorithm", "flat",
                                "datatype", "float32"))));

    // Create index
    bikeIndex = SearchIndex.fromDict(bikeSchema, unifiedJedis);
    bikeIndex.create(true);

    // Load bike data
    List<String> bikeKeys = bikeIndex.load(bikeData);
    assertThat(bikeKeys).hasSize(2);

    // Wait for indexing
    Thread.sleep(1000);

    // Create a query embedding
    float[] queryVector = new float[384];
    for (int i = 0; i < 384; i++) {
      queryVector[i] = (float) (Math.random() * 0.1 + 0.025);
    }

    VectorQuery bikeQuery =
        VectorQuery.builder()
            .vector(queryVector)
            .field("$.bike_embedding")
            .returnFields("$.metadata.brand", "$.name", "$.metadata.type")
            .numResults(2)
            .build();

    List<Map<String, Object>> bikeResults = bikeIndex.query(bikeQuery);
    assertThat(bikeResults).as("Bike query should return results").isNotEmpty();

    // Verify we can get nested fields
    for (Map<String, Object> result : bikeResults) {
      // For JSON storage, indexed fields with paths come back as nested objects
      assertThat(result).containsKey("$.metadata");
      assertThat(result).containsKey("$.name");

      // Check nested metadata structure
      @SuppressWarnings("unchecked")
      Map<String, Object> metadata = (Map<String, Object>) result.get("$.metadata");
      if (metadata != null) {
        assertThat(metadata).containsKey("brand");
        assertThat(metadata).containsKey("type");
      }
    }
  }
}
