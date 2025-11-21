package com.redis.vl.extensions.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for LangCacheSemanticCache.
 *
 * <p>Port of tests from redis-vl-python/tests/unit/test_langcache_wrapper.py
 */
class LangCacheSemanticCacheTest {

  private OkHttpClient mockHttpClient;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mockHttpClient = mock(OkHttpClient.class);
    objectMapper = new ObjectMapper();
  }

  @Test
  void testInitRequiresCacheId() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new LangCacheSemanticCache.Builder()
                    .name("test")
                    .serverUrl("https://api.example.com")
                    .cacheId("")
                    .apiKey("test-key")
                    .build());

    assertTrue(exception.getMessage().contains("cache_id"));
  }

  @Test
  void testInitRequiresApiKey() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new LangCacheSemanticCache.Builder()
                    .name("test")
                    .serverUrl("https://api.example.com")
                    .cacheId("test-cache")
                    .apiKey("")
                    .build());

    assertTrue(exception.getMessage().contains("api_key"));
  }

  @Test
  void testInitRequiresAtLeastOneSearchStrategy() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new LangCacheSemanticCache.Builder()
                    .name("test")
                    .serverUrl("https://api.example.com")
                    .cacheId("test-cache")
                    .apiKey("test-key")
                    .useExactSearch(false)
                    .useSemanticSearch(false)
                    .build());

    assertTrue(exception.getMessage().toLowerCase().contains("at least one"));
  }

  @Test
  void testInitSuccess() throws Exception {
    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test_cache")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache-id")
            .apiKey("test-api-key")
            .ttl(3600)
            .httpClient(mockHttpClient)
            .build();

    assertEquals("test_cache", cache.getName());
    assertEquals(3600, cache.getTtl());
  }

  @Test
  void testStore() throws Exception {
    // Mock HTTP response for store operation
    ObjectNode responseJson = objectMapper.createObjectNode();
    responseJson.put("entry_id", "entry-123");

    ResponseBody responseBody =
        ResponseBody.create(responseJson.toString(), MediaType.get("application/json"));

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("https://api.example.com/set").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .httpClient(mockHttpClient)
            .build();

    String entryId =
        cache.store(
            "What is Python?", "Python is a programming language.", Map.of("topic", "programming"));

    assertEquals("entry-123", entryId);

    // Verify the request was made with correct parameters
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockHttpClient).newCall(requestCaptor.capture());

    Request capturedRequest = requestCaptor.getValue();
    assertNotNull(capturedRequest.body());
  }

  @Test
  void testCheck() throws Exception {
    // Mock HTTP response for check operation
    ObjectNode entry = objectMapper.createObjectNode();
    entry.put("id", "entry-123");
    entry.put("prompt", "What is Python?");
    entry.put("response", "Python is a programming language.");
    entry.put("similarity", 0.95);
    entry.put("created_at", 1234567890.0);
    entry.put("updated_at", 1234567890.0);
    entry.set("attributes", objectMapper.createObjectNode().put("topic", "programming"));

    ObjectNode responseJson = objectMapper.createObjectNode();
    ArrayNode dataArray = objectMapper.createArrayNode();
    dataArray.add(entry);
    responseJson.set("data", dataArray);

    ResponseBody responseBody =
        ResponseBody.create(responseJson.toString(), MediaType.get("application/json"));

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("https://api.example.com/search").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .httpClient(mockHttpClient)
            .build();

    List<Map<String, Object>> results = cache.check("What is Python?", null, 1, null, null, null);

    assertEquals(1, results.size());
    Map<String, Object> result = results.get(0);
    assertEquals("entry-123", result.get("entry_id"));
    assertEquals("What is Python?", result.get("prompt"));
    assertEquals("Python is a programming language.", result.get("response"));

    // Verify distance conversion: similarity 0.95 -> distance 0.05 (1.0 - 0.95)
    float distance = ((Number) result.get("vector_distance")).floatValue();
    assertEquals(0.05f, distance, 0.001f);
  }

  @Test
  void testCheckWithDistanceThreshold() throws Exception {
    // Mock HTTP response
    ObjectNode entry = objectMapper.createObjectNode();
    entry.put("id", "entry-123");
    entry.put("prompt", "What is Python?");
    entry.put("response", "Python is a programming language.");
    entry.put("similarity", 0.85);
    entry.put("created_at", 1234567890.0);
    entry.put("updated_at", 1234567890.0);
    entry.set("attributes", objectMapper.createObjectNode());

    ObjectNode responseJson = objectMapper.createObjectNode();
    ArrayNode dataArray = objectMapper.createArrayNode();
    dataArray.add(entry);
    responseJson.set("data", dataArray);

    ResponseBody responseBody =
        ResponseBody.create(responseJson.toString(), MediaType.get("application/json"));

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("https://api.example.com/search").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .httpClient(mockHttpClient)
            .build();

    // distance_threshold=0.2 should be converted to similarity_threshold=0.8
    cache.check("What is Python?", null, 1, null, null, 0.2f);

    // Verify the request was made with correct similarity_threshold
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockHttpClient).newCall(requestCaptor.capture());

    Request capturedRequest = requestCaptor.getValue();
    assertNotNull(capturedRequest.body());
    // Would need to parse request body to verify similarity_threshold=0.8
  }

  @Test
  void testCheckWithAttributes() throws Exception {
    // Mock HTTP response
    ObjectNode entry = objectMapper.createObjectNode();
    entry.put("id", "entry-123");
    entry.put("prompt", "What is Python?");
    entry.put("response", "Python is a programming language.");
    entry.put("similarity", 0.95);
    entry.put("created_at", 1234567890.0);
    entry.put("updated_at", 1234567890.0);

    ObjectNode attrs = objectMapper.createObjectNode();
    attrs.put("language", "python");
    attrs.put("topic", "programming");
    entry.set("attributes", attrs);

    ObjectNode responseJson = objectMapper.createObjectNode();
    ArrayNode dataArray = objectMapper.createArrayNode();
    dataArray.add(entry);
    responseJson.set("data", dataArray);

    ResponseBody responseBody =
        ResponseBody.create(responseJson.toString(), MediaType.get("application/json"));

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("https://api.example.com/search").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .httpClient(mockHttpClient)
            .build();

    Map<String, Object> attributes = Map.of("language", "python", "topic", "programming");
    List<Map<String, Object>> results =
        cache.check("What is Python?", attributes, 1, null, null, null);

    assertEquals(1, results.size());
    assertEquals("entry-123", results.get(0).get("entry_id"));
  }

  @Test
  void testDelete() throws Exception {
    // Mock HTTP response for flush operation
    ResponseBody responseBody =
        ResponseBody.create("{\"flushed\": true}", MediaType.get("application/json"));

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("https://api.example.com/flush").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .httpClient(mockHttpClient)
            .build();

    cache.delete();

    // Verify flush endpoint was called
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockHttpClient).newCall(requestCaptor.capture());

    Request capturedRequest = requestCaptor.getValue();
    assertTrue(capturedRequest.url().toString().contains("/flush"));
  }

  @Test
  void testFlush() throws Exception {
    // Mock HTTP response for flush operation
    ResponseBody responseBody =
        ResponseBody.create("{\"flushed\": true}", MediaType.get("application/json"));

    Response mockResponse =
        new Response.Builder()
            .request(
                new Request.Builder()
                    .url("https://api.example.com/v1/caches/test-cache/flush")
                    .build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .httpClient(mockHttpClient)
            .build();

    cache.flush();

    // Verify the request was made to the flush endpoint
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockHttpClient).newCall(requestCaptor.capture());

    Request capturedRequest = requestCaptor.getValue();
    assertEquals(
        "https://api.example.com/v1/caches/test-cache/flush", capturedRequest.url().toString());
    assertEquals("POST", capturedRequest.method());
  }

  @Test
  void testClear() throws Exception {
    // Mock HTTP response for flush operation (clear calls delete which calls flush)
    ResponseBody responseBody =
        ResponseBody.create("{\"flushed\": true}", MediaType.get("application/json"));

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("https://api.example.com/flush").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .httpClient(mockHttpClient)
            .build();

    cache.clear();

    // Verify flush endpoint was called
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockHttpClient).newCall(requestCaptor.capture());

    Request capturedRequest = requestCaptor.getValue();
    assertTrue(capturedRequest.url().toString().contains("/flush"));
  }

  @Test
  void testDeleteByAttributesWithEmptyAttributes() throws Exception {
    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .httpClient(mockHttpClient)
            .build();

    // Test with null attributes
    Map<String, Object> result = cache.deleteByAttributes(null);
    assertEquals(0, result.get("deleted_entries_count"));

    // Verify no HTTP call was made
    verify(mockHttpClient, never()).newCall(any(Request.class));

    // Test with empty map
    result = cache.deleteByAttributes(Collections.emptyMap());
    assertEquals(0, result.get("deleted_entries_count"));

    // Verify still no HTTP call was made
    verify(mockHttpClient, never()).newCall(any(Request.class));
  }

  @Test
  void testDeleteById() throws Exception {
    // Mock HTTP response for delete by ID operation
    ResponseBody responseBody =
        ResponseBody.create("{\"deleted\": true}", MediaType.get("application/json"));

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("https://api.example.com/delete/entry-123").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .httpClient(mockHttpClient)
            .build();

    cache.deleteById("entry-123");

    verify(mockHttpClient).newCall(any(Request.class));
  }

  @Test
  void testUpdateNotSupported() {
    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .httpClient(mockHttpClient)
            .build();

    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class,
            () -> cache.update("key", "response", "new response"));

    assertTrue(exception.getMessage().contains("does not support updating"));
  }

  @Test
  void testDistanceScaleRedis() throws Exception {
    // Test with distance_scale="redis"
    LangCacheSemanticCache cache =
        new LangCacheSemanticCache.Builder()
            .name("test")
            .serverUrl("https://api.example.com")
            .cacheId("test-cache")
            .apiKey("test-key")
            .distanceScale("redis")
            .httpClient(mockHttpClient)
            .build();

    // Mock response with similarity
    ObjectNode entry = objectMapper.createObjectNode();
    entry.put("id", "entry-123");
    entry.put("prompt", "What is Python?");
    entry.put("response", "Python is a programming language.");
    entry.put("similarity", 0.75); // similarity 0.75
    entry.put("created_at", 1234567890.0);
    entry.put("updated_at", 1234567890.0);
    entry.set("attributes", objectMapper.createObjectNode());

    ObjectNode responseJson = objectMapper.createObjectNode();
    ArrayNode dataArray = objectMapper.createArrayNode();
    dataArray.add(entry);
    responseJson.set("data", dataArray);

    ResponseBody responseBody =
        ResponseBody.create(responseJson.toString(), MediaType.get("application/json"));

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("https://api.example.com/search").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    List<Map<String, Object>> results = cache.check("What is Python?", null, 1, null, null, null);

    assertEquals(1, results.size());

    // Verify distance conversion for "redis" scale:
    // similarity 0.75 -> redis distance = 2 - 2*0.75 = 0.5
    float distance = ((Number) results.get(0).get("vector_distance")).floatValue();
    assertEquals(0.5f, distance, 0.001f);
  }

  @Test
  void testInvalidDistanceScale() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new LangCacheSemanticCache.Builder()
                    .name("test")
                    .serverUrl("https://api.example.com")
                    .cacheId("test-cache")
                    .apiKey("test-key")
                    .distanceScale("invalid")
                    .build());

    assertTrue(exception.getMessage().contains("distance_scale"));
  }

  /**
   * Test attribute encoding/decoding round-trip.
   *
   * <p>Port of test_check_with_attributes from redis-vl-python (PR #437 & #438)
   *
   * <p>Verifies that problematic characters (comma, slash, backslash, question mark) are:
   * <ul>
   *   <li>Encoded when sending attributes to LangCache API</li>
   *   <li>Decoded when receiving attributes back from LangCache API</li>
   *   <li>Round-trip correctly so callers see original values</li>
   * </ul>
   */
  @Test
  void testAttributeEncodingDecoding() throws Exception {
    // Mock HTTP response with encoded attributes
    ObjectNode entry = objectMapper.createObjectNode();
    entry.put("id", "entry-123");
    entry.put("prompt", "What is Python?");
    entry.put("response", "Python is a programming language.");
    entry.put("similarity", 0.95);
    entry.put("created_at", 1234567890.0);
    entry.put("updated_at", 1234567890.0);

    // LangCache returns attributes with encoded special characters
    ObjectNode attrs = objectMapper.createObjectNode();
    attrs.put("language", "python");
    attrs.put("topic", "programming，with∕encoding＼and？");  // Encoded characters
    entry.set("attributes", attrs);

    ArrayNode dataArray = objectMapper.createArrayNode();
    dataArray.add(entry);

    ObjectNode responseBody = objectMapper.createObjectNode();
    responseBody.set("data", dataArray);

    ResponseBody mockResponseBody = ResponseBody.create(responseBody.toString(), MediaType.get("application/json"));
    Response mockResponse = new Response.Builder()
        .request(new Request.Builder().url("http://test.com").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(mockResponseBody)
        .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache = new LangCacheSemanticCache.Builder()
        .name("test")
        .serverUrl("https://api.example.com")
        .cacheId("test-cache")
        .apiKey("test-key")
        .httpClient(mockHttpClient)
        .build();

    // Search with attributes containing special characters (unencoded)
    Map<String, Object> searchAttributes = Map.of(
        "language", "python",
        "topic", "programming,with/encoding\\and?"  // Original unencoded values
    );

    List<Map<String, Object>> results = cache.check("What is Python?", searchAttributes, 1, null, null, null);

    assertEquals(1, results.size());
    assertEquals("entry-123", results.get(0).get("entry_id"));

    // Verify attributes were encoded when sent to LangCache
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockHttpClient).newCall(requestCaptor.capture());

    Request capturedRequest = requestCaptor.getValue();
    okio.Buffer buffer = new okio.Buffer();
    capturedRequest.body().writeTo(buffer);
    String requestBodyStr = buffer.readUtf8();

    ObjectNode requestJson = (ObjectNode) objectMapper.readTree(requestBodyStr);
    ObjectNode requestAttrs = (ObjectNode) requestJson.get("attributes");

    // The comma, slash, backslash, and question mark should be encoded for LangCache
    assertEquals("python", requestAttrs.get("language").asText());
    assertEquals("programming，with∕encoding＼and？", requestAttrs.get("topic").asText());

    // Verify attributes were decoded when returned to caller
    @SuppressWarnings("unchecked")
    Map<String, Object> metadata = (Map<String, Object>) results.get(0).get("metadata");
    assertNotNull(metadata);
    assertEquals("python", metadata.get("language"));
    assertEquals("programming,with/encoding\\and?", metadata.get("topic"));  // Decoded back to original
  }

  /**
   * Test that store() encodes metadata attributes.
   *
   * <p>Port of store test with special characters from redis-vl-python (PR #437 & #438)
   */
  @Test
  void testStoreEncodesMetadata() throws Exception {
    // Mock HTTP response
    ObjectNode responseBody = objectMapper.createObjectNode();
    responseBody.put("entry_id", "entry-456");

    ResponseBody mockResponseBody = ResponseBody.create(responseBody.toString(), MediaType.get("application/json"));
    Response mockResponse = new Response.Builder()
        .request(new Request.Builder().url("http://test.com").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(mockResponseBody)
        .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache = new LangCacheSemanticCache.Builder()
        .name("test")
        .serverUrl("https://api.example.com")
        .cacheId("test-cache")
        .apiKey("test-key")
        .httpClient(mockHttpClient)
        .build();

    // Store with metadata containing special characters
    Map<String, Object> metadata = Map.of(
        "category", "Q&A",
        "path", "docs/api/v1",
        "regex", "\\d+",
        "separator", "a,b,c"
    );

    String entryId = cache.store("Test prompt", "Test response", metadata);
    assertEquals("entry-456", entryId);

    // Verify metadata was encoded when sent to LangCache
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockHttpClient).newCall(requestCaptor.capture());

    Request capturedRequest = requestCaptor.getValue();
    okio.Buffer buffer = new okio.Buffer();
    capturedRequest.body().writeTo(buffer);
    String requestBodyStr = buffer.readUtf8();

    ObjectNode requestJson = (ObjectNode) objectMapper.readTree(requestBodyStr);
    ObjectNode requestAttrs = (ObjectNode) requestJson.get("attributes");

    // Special characters should be encoded (& is not encoded, only ,/\? are)
    assertEquals("Q&A", requestAttrs.get("category").asText());  // & is not in ENCODE_TRANS, stays as is
    assertEquals("docs∕api∕v1", requestAttrs.get("path").asText());  // / encoded
    assertEquals("＼d+", requestAttrs.get("regex").asText());  // \ encoded
    assertEquals("a，b，c", requestAttrs.get("separator").asText());  // , encoded
  }

  /**
   * Test that deleteByAttributes() encodes attribute filters.
   *
   * <p>Port of delete_by_attributes test from redis-vl-python (PR #437 & #438)
   */
  @Test
  void testDeleteByAttributesEncodesAttributes() throws Exception {
    // Mock HTTP response
    ObjectNode responseBody = objectMapper.createObjectNode();
    responseBody.put("deleted_entries_count", 2);

    ResponseBody mockResponseBody = ResponseBody.create(responseBody.toString(), MediaType.get("application/json"));
    Response mockResponse = new Response.Builder()
        .request(new Request.Builder().url("http://test.com").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(mockResponseBody)
        .build();

    Call mockCall = mock(Call.class);
    when(mockCall.execute()).thenReturn(mockResponse);
    when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);

    LangCacheSemanticCache cache = new LangCacheSemanticCache.Builder()
        .name("test")
        .serverUrl("https://api.example.com")
        .cacheId("test-cache")
        .apiKey("test-key")
        .httpClient(mockHttpClient)
        .build();

    // Delete by attributes containing special characters
    Map<String, Object> attributes = Map.of(
        "topic", "programming,with/encoding\\and?"
    );

    Map<String, Object> result = cache.deleteByAttributes(attributes);
    assertEquals(2, result.get("deleted_entries_count"));

    // Verify attributes were encoded when sent to LangCache
    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(mockHttpClient).newCall(requestCaptor.capture());

    Request capturedRequest = requestCaptor.getValue();
    okio.Buffer buffer = new okio.Buffer();
    capturedRequest.body().writeTo(buffer);
    String requestBodyStr = buffer.readUtf8();

    ObjectNode requestJson = (ObjectNode) objectMapper.readTree(requestBodyStr);
    ObjectNode requestAttrs = (ObjectNode) requestJson.get("attributes");

    // Special characters should be encoded to match what was stored
    assertEquals("programming，with∕encoding＼and？", requestAttrs.get("topic").asText());
  }
}
