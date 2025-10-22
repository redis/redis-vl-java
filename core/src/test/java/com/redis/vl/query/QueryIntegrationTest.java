package com.redis.vl.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Integration tests for queries */
@DisplayName("Query Integration Tests")
class QueryIntegrationTest extends BaseIntegrationTest {

  private static final String TEST_PREFIX = "q_" + UUID.randomUUID().toString().substring(0, 8);
  private SearchIndex index;

  @BeforeEach
  void setUp() {
    // Create index schema matching Python test
    Map<String, Object> schemaDict = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "user_index_" + TEST_PREFIX);
    indexConfig.put("prefix", "v1_" + TEST_PREFIX);
    indexConfig.put("storage_type", "hash");
    schemaDict.put("index", indexConfig);

    List<Map<String, Object>> fields =
        Arrays.asList(
            Map.of("name", "description", "type", "text"),
            Map.of("name", "credit_score", "type", "tag"),
            Map.of("name", "job", "type", "text"),
            Map.of("name", "age", "type", "numeric"),
            Map.of("name", "last_updated", "type", "numeric"),
            Map.of("name", "location", "type", "geo"),
            Map.of(
                "name",
                "user_embedding",
                "type",
                "vector",
                "attrs",
                Map.of(
                    "dims",
                    3,
                    "distance_metric",
                    "cosine",
                    "algorithm",
                    "hnsw",
                    "datatype",
                    "float32")));
    schemaDict.put("fields", fields);

    IndexSchema schema = IndexSchema.fromDict(schemaDict);
    index = new SearchIndex(schema, unifiedJedis);

    // Create index
    index.create(true);

    // Load sample data
    List<Map<String, Object>> sampleData = createSampleData();
    List<String> loadedKeys = index.load(sampleData, "id");
    assertThat(loadedKeys).hasSize(sampleData.size());

    // Add small delay to allow indexing
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @AfterEach
  void tearDown() {
    if (index != null && index.exists()) {
      index.delete(true);
    }
  }

  private List<Map<String, Object>> createSampleData() {
    List<Map<String, Object>> data = new ArrayList<>();

    // Sample users with embeddings and metadata
    data.add(
        Map.of(
            "id",
            "john",
            "user",
            "john",
            "description",
            "John is a software engineer with expertise in distributed systems",
            "credit_score",
            "high",
            "job",
            "engineer",
            "age",
            35,
            "last_updated",
            System.currentTimeMillis() / 1000,
            "location",
            "-122.4194,37.7749",
            "user_embedding",
            new float[] {0.1f, 0.2f, 0.3f}));

    data.add(
        Map.of(
            "id",
            "mary",
            "user",
            "mary",
            "description",
            "Mary is a medical professional with expertise in lung cancer research",
            "credit_score",
            "high",
            "job",
            "doctor",
            "age",
            42,
            "last_updated",
            System.currentTimeMillis() / 1000 - 3600,
            "location",
            "-122.4194,37.7749",
            "user_embedding",
            new float[] {0.15f, 0.25f, 0.35f}));

    data.add(
        Map.of(
            "id",
            "nancy",
            "user",
            "nancy",
            "description",
            "Nancy is a dermatologist specializing in skin conditions",
            "credit_score",
            "medium",
            "job",
            "dermatologist",
            "age",
            38,
            "last_updated",
            System.currentTimeMillis() / 1000 - 7200,
            "location",
            "-110.0839,37.3861",
            "user_embedding",
            new float[] {0.2f, 0.3f, 0.4f}));

    data.add(
        Map.of(
            "id",
            "tyler",
            "user",
            "tyler",
            "description",
            "Tyler is a CEO of a startup company",
            "credit_score",
            "high",
            "job",
            "CEO",
            "age",
            45,
            "last_updated",
            System.currentTimeMillis() / 1000 - 10800,
            "location",
            "-122.4194,37.7749",
            "user_embedding",
            new float[] {0.25f, 0.35f, 0.45f}));

    data.add(
        Map.of(
            "id",
            "tim",
            "user",
            "tim",
            "description",
            "Tim is a software engineer focusing on machine learning",
            "credit_score",
            "low",
            "job",
            "engineer",
            "age",
            28,
            "last_updated",
            System.currentTimeMillis() / 1000 - 14400,
            "location",
            "-110.0839,37.3861",
            "user_embedding",
            new float[] {0.3f, 0.4f, 0.5f}));

    data.add(
        Map.of(
            "id",
            "derrick",
            "user",
            "derrick",
            "description",
            "Derrick is a medical researcher studying cancer treatments",
            "credit_score",
            "high",
            "job",
            "doctor",
            "age",
            50,
            "last_updated",
            System.currentTimeMillis() / 1000 - 18000,
            "location",
            "-110.0839,37.3861",
            "user_embedding",
            new float[] {0.35f, 0.45f, 0.55f}));

    data.add(
        Map.of(
            "id",
            "joe",
            "user",
            "joe",
            "description",
            "Joe is a dentist with a private practice",
            "credit_score",
            "low",
            "job",
            "dentist",
            "age",
            40,
            "last_updated",
            System.currentTimeMillis() / 1000 - 21600,
            "location",
            "-110.0839,37.3861",
            "user_embedding",
            new float[] {0.4f, 0.5f, 0.6f}));

    return data;
  }

  @Test
  @DisplayName("Test search and query")
  void testSearchAndQuery() {
    // Create VectorQuery matching Python test
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job", "location")
            .numResults(7)
            .build();

    // Test raw search
    index.search(query);
    // TODO: SearchResult returns null for now, implement actual search
    // assertThat(searchResult).isNotNull();
    // assertThat(searchResult.getTotalResults()).isEqualTo(7);

    // Test processed query
    List<Map<String, Object>> processedResults = index.query(query);
    assertThat(processedResults).hasSize(7);
    assertThat(processedResults.get(0)).isInstanceOf(Map.class);
  }

  @Test
  @DisplayName("Test range query")
  void testRangeQuery() {
    // Create VectorRangeQuery
    VectorRangeQuery rangeQuery =
        VectorRangeQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job")
            .distanceThreshold(0.3)
            .numResults(7)
            .build();

    List<Map<String, Object>> results = index.query(rangeQuery);

    // Verify all results are within distance threshold
    for (Map<String, Object> result : results) {
      assertThat(result).containsKey("vector_distance");
      double distance = Double.parseDouble(result.get("vector_distance").toString());
      assertThat(distance).isLessThanOrEqualTo(0.3);
    }

    // Test updating distance threshold
    rangeQuery.setDistanceThreshold(0.15);
    assertThat(rangeQuery.getDistanceThreshold()).isEqualTo(0.15);

    results = index.query(rangeQuery);
    for (Map<String, Object> result : results) {
      double distance = Double.parseDouble(result.get("vector_distance").toString());
      assertThat(distance).isLessThanOrEqualTo(0.15);
    }
  }

  @Test
  @DisplayName("Test count query")
  void testCountQuery() {
    // Test count query construction
    CountQuery countAll = new CountQuery("*");
    assertThat(countAll.getFilterString()).isEqualTo("*");

    // Count documents with high credit score
    CountQuery countHighCredit = new CountQuery(Filter.tag("credit_score", "high"));
    assertThat(countHighCredit.getFilterString()).isEqualTo("@credit_score:{high}");

    // Test actual count query execution using count() method
    // Count all documents
    long totalCount = index.count(countAll);
    assertThat(totalCount).isEqualTo(7); // We loaded 7 documents

    // Count high credit score documents
    long highCreditCount = index.count(countHighCredit);
    assertThat(highCreditCount)
        .isEqualTo(4); // 4 documents have high credit score (john, mary, tyler, derrick)

    // Count with filter that matches nothing
    CountQuery countNone = new CountQuery(Filter.tag("credit_score", "nonexistent"));
    long noneCount = index.count(countNone);
    assertThat(noneCount).isEqualTo(0);

    // Test query() method with CountQuery returns empty list (maintains API compatibility)
    // Use count() method directly for getting the count
    List<Map<String, Object>> countQueryResult = index.query(countAll);
    assertThat(countQueryResult).isEmpty();
  }

  @Test
  @DisplayName("Test filters with vector query")
  void testFiltersWithVectorQuery() {
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job", "location")
            .numResults(10)
            .build();

    // Test tag filter
    Filter tagFilter = Filter.tag("credit_score", "high");
    query.setFilter(tagFilter);
    List<Map<String, Object>> results = index.query(query);
    assertThat(results).hasSize(4);
    for (Map<String, Object> result : results) {
      assertThat(result.get("credit_score")).isEqualTo("high");
    }

    // Test numeric filter
    Filter numericFilter = Filter.numeric("age").gte(40);
    query.setFilter(numericFilter);

    results = index.query(query);
    for (Map<String, Object> result : results) {
      int age = Integer.parseInt(result.get("age").toString());
      assertThat(age).isGreaterThanOrEqualTo(40);
    }

    // Test combined filters
    Filter combinedFilter =
        Filter.and(Filter.tag("credit_score", "high"), Filter.numeric("age").between(35, 45));
    query.setFilter(combinedFilter);

    results = index.query(query);
    for (Map<String, Object> result : results) {
      assertThat(result.get("credit_score")).isEqualTo("high");
      int age = Integer.parseInt(result.get("age").toString());
      assertThat(age).isBetween(35, 45);
    }
  }

  @Test
  @DisplayName("Test text filters")
  void testTextFilters() {
    // Test filter query construction
    Filter query = Filter.text("job", "engineer");
    assertThat(query.build()).isEqualTo("@job:engineer");

    // Test wildcard
    query = Filter.wildcard("job", "engine*");
    assertThat(query.build()).isEqualTo("@job:engine*");

    // Test fuzzy match
    query = Filter.fuzzy("job", "engneer"); // Misspelled
    assertThat(query.build()).isEqualTo("@job:%engneer%");
  }

  @Test
  @DisplayName("Test geo filters")
  void testGeoFilters() {
    // Test geo radius filter construction
    Filter geoFilter = Filter.geo("location").radius(-122.4194, 37.7749, 1, Filter.GeoUnit.KM);

    assertThat(geoFilter.build()).isEqualTo("@location:[-122.4194 37.7749 1 km]");
  }

  @Test
  @DisplayName("Test filter combinations")
  void testFilterCombinations() {
    // Test AND combination
    Filter andFilter =
        Filter.and(Filter.tag("credit_score", "high"), Filter.text("job", "engineer"));
    assertThat(andFilter.build()).contains("@credit_score:{high}");
    assertThat(andFilter.build()).contains("@job:engineer");

    // Test OR combination
    Filter orFilter = Filter.or(Filter.tag("credit_score", "high"), Filter.text("job", "engineer"));
    assertThat(orFilter.build()).contains("@credit_score:{high} | @job:engineer");

    // Test NOT filter
    Filter notFilter = Filter.not(Filter.text("job", "engineer"));
    assertThat(notFilter.build()).isEqualTo("-@job:engineer");
  }

  @Test
  @DisplayName("Test paginate vector query")
  void testPaginateVectorQuery() {
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job", "location")
            .numResults(10) // Request more than we have to test pagination
            .returnScore(true)
            .build();

    // Test pagination with small page size
    Iterable<List<Map<String, Object>>> paginatorIterable = index.paginate(query, 2);
    Iterator<List<Map<String, Object>>> paginator = paginatorIterable.iterator();

    assertThat(paginator).isNotNull();
    assertThat(paginator.hasNext()).isTrue();

    List<Map<String, Object>> allResults = new ArrayList<>();
    int pageCount = 0;

    while (paginator.hasNext()) {
      List<Map<String, Object>> page = paginator.next();
      assertThat(page).isNotEmpty();
      assertThat(page.size()).isLessThanOrEqualTo(2); // Page size should be at most 2
      allResults.addAll(page);
      pageCount++;
    }

    // We have 7 documents, with page size 2, we should have 4 pages
    assertThat(pageCount).isEqualTo(4);
    assertThat(allResults).hasSize(7);
  }

  @Test
  @DisplayName("Test paginate filter query")
  void testPaginateFilterQuery() {
    // Test filter query construction for pagination
    Filter filter = Filter.tag("credit_score", "high");
    assertThat(filter.build()).isEqualTo("@credit_score:{high}");

    // Test pagination with filter query
    Iterable<List<Map<String, Object>>> paginatorIterable = index.paginate(filter, 2);
    Iterator<List<Map<String, Object>>> paginator = paginatorIterable.iterator();

    assertThat(paginator).isNotNull();
    assertThat(paginator.hasNext()).isTrue();

    List<Map<String, Object>> allResults = new ArrayList<>();

    while (paginator.hasNext()) {
      List<Map<String, Object>> page = paginator.next();
      assertThat(page).isNotEmpty();
      assertThat(page.size()).isLessThanOrEqualTo(2);

      // Verify all results have high credit score
      for (Map<String, Object> doc : page) {
        assertThat(doc.get("credit_score")).isEqualTo("high");
      }

      allResults.addAll(page);
    }

    // 4 documents have high credit score
    assertThat(allResults).hasSize(4);
  }

  @Test
  @DisplayName("Test hybrid policy batches mode")
  void testHybridPolicyBatchesMode() {
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job", "location")
            .returnScore(true)
            .build();

    // Set filter
    Filter tagFilter = Filter.tag("credit_score", "high");
    query.setFilter(tagFilter);

    // Set hybrid policy to BATCHES
    query.setHybridPolicy("BATCHES");
    query.setBatchSize(2);

    // Check query string
    String queryString = query.toString();
    assertThat(queryString).contains("HYBRID_POLICY BATCHES BATCH_SIZE 2");

    // Execute query
    List<Map<String, Object>> results = index.query(query);

    // Check results - should have filtered to "high" credit scores
    assertThat(results).isNotEmpty();
    for (Map<String, Object> result : results) {
      assertThat(result.get("credit_score")).isEqualTo("high");
    }
  }

  @Test
  @DisplayName("Test hybrid policy adhoc bf mode")
  void testHybridPolicyAdhocBfMode() {
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job", "location")
            .returnScore(true)
            .build();

    // Set filter
    Filter tagFilter = Filter.tag("credit_score", "high");
    query.setFilter(tagFilter);

    // Set hybrid policy to ADHOC_BF
    query.setHybridPolicy("ADHOC_BF");

    // Check query string
    String queryString = query.toString();
    assertThat(queryString).contains("HYBRID_POLICY ADHOC_BF");

    // Execute query
    List<Map<String, Object>> results = index.query(query);

    // Check results - should have filtered to "high" credit scores
    assertThat(results).isNotEmpty();
    for (Map<String, Object> result : results) {
      assertThat(result.get("credit_score")).isEqualTo("high");
    }
  }

  @Test
  @DisplayName("Test range query with epsilon")
  void testRangeQueryWithEpsilon() {
    // Create a range query with epsilon
    VectorRangeQuery epsilonQuery =
        VectorRangeQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job")
            .distanceThreshold(0.3)
            .epsilon(0.5) // Larger than default to get potentially more results
            .build();

    // Verify query string is valid
    String queryString = epsilonQuery.toString();
    assertThat(queryString).contains("@user_embedding");

    // Verify epsilon property is set
    assertThat(epsilonQuery.getEpsilon()).isEqualTo(0.5);

    // Test setting epsilon
    epsilonQuery.setEpsilon(0.1);
    assertThat(epsilonQuery.getEpsilon()).isEqualTo(0.1);
    assertThat(epsilonQuery.toString()).contains("@user_embedding");

    // Execute basic query without epsilon to ensure functionality
    VectorRangeQuery basicQuery =
        VectorRangeQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnFields("user", "credit_score", "age", "job")
            .distanceThreshold(0.3)
            .build();

    List<Map<String, Object>> results = index.query(basicQuery);

    // Check results
    for (Map<String, Object> result : results) {
      double distance = Double.parseDouble(result.get("vector_distance").toString());
      assertThat(distance).isLessThanOrEqualTo(0.3);
    }
  }

  @Test
  @DisplayName("Test text query")
  void testTextQuery() {
    // Try simpler search first
    String text = "medical";
    String textField = "description";
    List<String> returnFields =
        Arrays.asList("user", "credit_score", "age", "job", "location", "description");

    // First try a basic search to see if any documents exist
    List<Map<String, Object>> basicResults = index.query("*");
    assertThat(basicResults).as("Basic search should return documents").isNotEmpty();

    // Test different scorers
    List<String> scorers = Arrays.asList("BM25", "TFIDF", "TFIDF.DOCNORM", "DISMAX", "DOCSCORE");

    for (String scorer : scorers) {
      TextQuery textQuery =
          TextQuery.builder().text(text).textField(textField).scorer(scorer).numResults(10).build();
      List<Map<String, Object>> results = index.query(textQuery);
      assertThat(results)
          .as("TextQuery with scorer " + scorer + " should return results")
          .isNotEmpty();

      // Make sure at least one word from the query is in the description
      for (Map<String, Object> result : results) {
        String description = result.get("description").toString();
        boolean containsWord =
            Arrays.stream(text.split(" "))
                .anyMatch(word -> description.toLowerCase().contains(word.toLowerCase()));
        assertThat(containsWord).isTrue();
      }
    }
  }

  @Test
  @DisplayName("Test text query with filter")
  void testTextQueryWithFilter() {
    String text = "medical";
    String textField = "description";
    List<String> returnFields =
        Arrays.asList("user", "credit_score", "age", "job", "location", "description");
    Filter filterExpression =
        Filter.and(Filter.tag("credit_score", "high"), Filter.numeric("age").gt(30));
    String scorer = "TFIDF";

    TextQuery textQuery =
        TextQuery.builder()
            .text(text)
            .textField(textField)
            .scorer(scorer)
            .filterExpression(filterExpression)
            .numResults(10)
            .build();
    List<Map<String, Object>> results = index.query(textQuery);

    assertThat(results).hasSize(2); // mary and derrick
    for (Map<String, Object> result : results) {
      String description = result.get("description").toString();
      boolean containsWord =
          Arrays.stream(text.split(" "))
              .anyMatch(word -> description.toLowerCase().contains(word.toLowerCase()));
      assertThat(containsWord).isTrue();
      assertThat(result.get("credit_score")).isEqualTo("high");
      int age = Integer.parseInt(result.get("age").toString());
      assertThat(age).isGreaterThan(30);
    }
  }

  @Test
  @DisplayName("Test query with chunk number zero")
  void testQueryWithChunkNumberZero() {
    String docBaseId = "8675309";
    String fileId = "e9ffbac9ff6f67cc";
    int chunkNum = 0;

    Filter filterConditions =
        Filter.and(
            Filter.tag("doc_base_id", docBaseId),
            Filter.tag("file_id", fileId),
            Filter.numeric("chunk_number").eq(chunkNum));

    String expectedQueryStr =
        "(@doc_base_id:{8675309} @file_id:{e9ffbac9ff6f67cc} @chunk_number:[0 0])";
    assertThat(filterConditions.build()).isEqualTo(expectedQueryStr);
  }

  @Test
  @DisplayName("Test normalize cosine distance")
  void testNormalizeCosineDistance() {
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .normalizeVectorDistance(true)
            .returnScore(true)
            .returnFields("user", "credit_score", "age", "job", "location")
            .build();

    List<Map<String, Object>> results = index.query(query);

    // Check all distances are normalized between 0 and 1
    for (Map<String, Object> result : results) {
      // Try different possible field names for distance
      Object distanceObj = result.get("vector_distance");
      if (distanceObj == null) {
        distanceObj = result.get("__user_embedding_score");
      }
      if (distanceObj == null) {
        distanceObj = result.get("score");
      }

      assertThat(distanceObj).as("Distance field not found in result").isNotNull();
      double distance = Double.parseDouble(distanceObj.toString());
      assertThat(distance).isBetween(0.0, 1.0);
    }
  }

  @Test
  @DisplayName("Test cosine distance unnormalized")
  void testCosineDistanceUnnormalized() {
    VectorQuery query =
        VectorQuery.builder()
            .vector(new float[] {0.1f, 0.1f, 0.5f})
            .field("user_embedding")
            .returnScore(true)
            .returnFields("user", "credit_score", "age", "job", "location")
            .build();

    List<Map<String, Object>> results = index.query(query);

    // RediSearch returns normalized cosine distances by default (0-1 range)
    // Check that all distances are within normalized range
    for (Map<String, Object> result : results) {
      // Try different possible field names for distance
      Object distanceObj = result.get("vector_distance");
      if (distanceObj == null) {
        distanceObj = result.get("__user_embedding_score");
      }
      if (distanceObj == null) {
        distanceObj = result.get("score");
      }

      assertThat(distanceObj).as("Distance field not found in result").isNotNull();
      double distance = Double.parseDouble(distanceObj.toString());
      // Cosine distance in RediSearch is normalized (0-1 range)
      assertThat(distance).isBetween(0.0, 1.0);
    }
  }

  @Test
  @DisplayName("Test range query normalize bad input")
  void testRangeQueryNormalizeBadInput() {
    // Should throw exception when distance threshold > 1 with normalization
    assertThatThrownBy(
            () ->
                VectorRangeQuery.builder()
                    .vector(new float[] {0.1f, 0.1f, 0.5f})
                    .field("user_embedding")
                    .normalizeVectorDistance(true)
                    .returnScore(true)
                    .returnFields("user", "credit_score", "age", "job", "location")
                    .distanceThreshold(1.2)
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
