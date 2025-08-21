package com.redis.vl.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redis.vl.schema.IndexSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for SearchIndex URL constructor and fromDict with URL */
@DisplayName("SearchIndex URL Constructor Tests")
class SearchIndexUrlConstructorTest {

  @Test
  @DisplayName("Should create SearchIndex with Redis URL")
  void shouldCreateSearchIndexWithRedisUrl() {
    // Given
    IndexSchema schema = IndexSchema.builder().name("test_index").prefix("test:").build();
    String redisUrl = "redis://localhost:6379";

    // When
    SearchIndex index = new SearchIndex(schema, redisUrl);

    // Then
    assertThat(index.getSchema()).isEqualTo(schema);
    assertThat(index.isValidateOnLoad()).isFalse();
  }

  @Test
  @DisplayName("Should create SearchIndex with Redis URL and validateOnLoad")
  void shouldCreateSearchIndexWithRedisUrlAndValidateOnLoad() {
    // Given
    IndexSchema schema = IndexSchema.builder().name("test_index").prefix("test:").build();
    String redisUrl = "redis://localhost:6379";

    // When
    SearchIndex index = new SearchIndex(schema, redisUrl, true);

    // Then
    assertThat(index.getSchema()).isEqualTo(schema);
    assertThat(index.isValidateOnLoad()).isTrue();
  }

  @Test
  @DisplayName("Should throw exception for null Redis URL")
  @SuppressWarnings("DataFlowIssue") // Intentionally testing failure case
  void shouldThrowExceptionForNullRedisUrl() {
    // Given
    IndexSchema schema = IndexSchema.builder().name("test_index").prefix("test:").build();

    // When/Then
    assertThatThrownBy(() -> new SearchIndex(schema, (String) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Redis URL cannot be null or empty");
  }

  @Test
  @DisplayName("Should throw exception for empty Redis URL")
  void shouldThrowExceptionForEmptyRedisUrl() {
    // Given
    IndexSchema schema = IndexSchema.builder().name("test_index").prefix("test:").build();

    // When/Then
    assertThatThrownBy(() -> new SearchIndex(schema, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Redis URL cannot be null or empty");

    assertThatThrownBy(() -> new SearchIndex(schema, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Redis URL cannot be null or empty");
  }

  @Test
  @DisplayName("Should create SearchIndex from dict with Redis URL")
  void shouldCreateSearchIndexFromDictWithRedisUrl() {
    // Given
    Map<String, Object> dict = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_index");
    indexConfig.put("prefix", "test:");
    dict.put("index", indexConfig);

    List<Map<String, Object>> fields =
        List.of(
            Map.of("name", "text_field", "type", "text"),
            Map.of("name", "numeric_field", "type", "numeric"));
    dict.put("fields", fields);

    String redisUrl = "redis://localhost:6379";

    // When
    SearchIndex index = SearchIndex.fromDict(dict, redisUrl);

    // Then
    assertThat(index.getSchema().getName()).isEqualTo("test_index");
    assertThat(index.getSchema().getPrefix()).isEqualTo("test:");
    assertThat(index.isValidateOnLoad()).isFalse();
  }

  @Test
  @DisplayName("Should create SearchIndex from dict with Redis URL and validateOnLoad")
  void shouldCreateSearchIndexFromDictWithRedisUrlAndValidateOnLoad() {
    // Given
    Map<String, Object> dict = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_index");
    indexConfig.put("prefix", "test:");
    dict.put("index", indexConfig);

    List<Map<String, Object>> fields =
        List.of(
            Map.of("name", "text_field", "type", "text"),
            Map.of(
                "name",
                "vector_field",
                "type",
                "vector",
                "attrs",
                Map.of("dims", 3, "distance_metric", "cosine", "algorithm", "flat")));
    dict.put("fields", fields);

    String redisUrl = "redis://localhost:6379";

    // When
    SearchIndex index = SearchIndex.fromDict(dict, redisUrl, true);

    // Then
    assertThat(index.getSchema().getName()).isEqualTo("test_index");
    assertThat(index.getSchema().getPrefix()).isEqualTo("test:");
    assertThat(index.isValidateOnLoad()).isTrue();
  }

  @Test
  @DisplayName("Should handle null Redis URL in fromDict")
  void shouldHandleNullRedisUrlInFromDict() {
    // Given
    Map<String, Object> dict = new HashMap<>();
    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_index");
    indexConfig.put("prefix", "test:");
    dict.put("index", indexConfig);

    List<Map<String, Object>> fields = List.of(Map.of("name", "text_field", "type", "text"));
    dict.put("fields", fields);

    // When - null URL should create index without connection
    SearchIndex index = SearchIndex.fromDict(dict, (String) null, false);

    // Then
    assertThat(index.getSchema().getName()).isEqualTo("test_index");
    assertThat(index.isValidateOnLoad()).isFalse();
  }

  @Test
  @DisplayName("Should support Redis URL with password")
  void shouldSupportRedisUrlWithPassword() {
    // Given
    IndexSchema schema = IndexSchema.builder().name("test_index").prefix("test:").build();
    String redisUrl = "redis://user:password@localhost:6379/0";

    // When
    SearchIndex index = new SearchIndex(schema, redisUrl);

    // Then
    assertThat(index.getSchema()).isEqualTo(schema);
  }

  @Test
  @DisplayName("Should support Redis SSL URL")
  void shouldSupportRedisSslUrl() {
    // Given
    IndexSchema schema = IndexSchema.builder().name("test_index").prefix("test:").build();
    String redisUrl = "rediss://localhost:6379";

    // When
    SearchIndex index = new SearchIndex(schema, redisUrl);

    // Then
    assertThat(index.getSchema()).isEqualTo(schema);
  }
}
