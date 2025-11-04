package com.redis.vl.langchain4j;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;

/**
 * Test for RedisVLDocumentStore - stores raw binary content (images, PDFs) for multimodal RAG.
 *
 * <p>Tests storing and retrieving documents with metadata for use with vision LLMs.
 */
@Tag("integration")
class RedisVLDocumentStoreTest {

  private JedisPooled jedis;
  private RedisVLDocumentStore documentStore;
  private static final String KEY_PREFIX = "test_docs:";

  @BeforeEach
  void setUp() {
    jedis = new JedisPooled("localhost", 6379);
    documentStore = new RedisVLDocumentStore(jedis, KEY_PREFIX);
  }

  @AfterEach
  void tearDown() {
    // Clean up test data
    if (documentStore != null && jedis != null) {
      // Delete all test keys
      jedis.keys(KEY_PREFIX + "*").forEach(jedis::del);
    }
    if (jedis != null) {
      jedis.close();
    }
  }

  @Test
  void testStoreAndRetrieveDocument() {
    // Given
    String docId = "doc1";
    byte[] content = "Test document content".getBytes(StandardCharsets.UTF_8);
    Map<String, String> metadata = Map.of("type", "text", "source", "test.txt", "page", "1");

    // When
    documentStore.store(docId, content, metadata);
    Optional<RedisVLDocumentStore.Document> retrieved = documentStore.retrieve(docId);

    // Then
    assertTrue(retrieved.isPresent());
    assertArrayEquals(content, retrieved.get().content());
    assertEquals("text", retrieved.get().metadata().get("type"));
    assertEquals("test.txt", retrieved.get().metadata().get("source"));
    assertEquals("1", retrieved.get().metadata().get("page"));
  }

  @Test
  void testStoreAndRetrieveBinaryContent() {
    // Given - Simulate image bytes
    String docId = "image1";
    byte[] imageBytes =
        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}; // JPEG header
    Map<String, String> metadata = Map.of("type", "image", "format", "jpeg");

    // When
    documentStore.store(docId, imageBytes, metadata);
    Optional<RedisVLDocumentStore.Document> retrieved = documentStore.retrieve(docId);

    // Then
    assertTrue(retrieved.isPresent());
    assertArrayEquals(imageBytes, retrieved.get().content());
    assertEquals("image", retrieved.get().metadata().get("type"));
  }

  @Test
  void testRetrieveNonExistentDocument() {
    // When
    Optional<RedisVLDocumentStore.Document> retrieved = documentStore.retrieve("nonexistent");

    // Then
    assertFalse(retrieved.isPresent());
  }

  @Test
  void testDeleteDocument() {
    // Given
    String docId = "doc_to_delete";
    byte[] content = "Delete me".getBytes(StandardCharsets.UTF_8);
    documentStore.store(docId, content, Map.of());

    // When
    boolean deleted = documentStore.delete(docId);
    Optional<RedisVLDocumentStore.Document> retrieved = documentStore.retrieve(docId);

    // Then
    assertTrue(deleted);
    assertFalse(retrieved.isPresent());
  }

  @Test
  void testDeleteNonExistentDocument() {
    // When
    boolean deleted = documentStore.delete("nonexistent");

    // Then
    assertFalse(deleted);
  }

  @Test
  void testStoreMultipleDocuments() {
    // Given
    Map<String, byte[]> docs =
        Map.of(
            "doc1", "Content 1".getBytes(StandardCharsets.UTF_8),
            "doc2", "Content 2".getBytes(StandardCharsets.UTF_8),
            "doc3", "Content 3".getBytes(StandardCharsets.UTF_8));

    // When
    docs.forEach((id, content) -> documentStore.store(id, content, Map.of("id", id)));

    // Then
    docs.forEach(
        (id, expectedContent) -> {
          Optional<RedisVLDocumentStore.Document> doc = documentStore.retrieve(id);
          assertTrue(doc.isPresent());
          assertArrayEquals(expectedContent, doc.get().content());
        });
  }

  @Test
  void testStoreWithEmptyMetadata() {
    // Given
    String docId = "doc_no_metadata";
    byte[] content = "No metadata".getBytes(StandardCharsets.UTF_8);

    // When
    documentStore.store(docId, content, Map.of());
    Optional<RedisVLDocumentStore.Document> retrieved = documentStore.retrieve(docId);

    // Then
    assertTrue(retrieved.isPresent());
    assertArrayEquals(content, retrieved.get().content());
    assertTrue(retrieved.get().metadata().isEmpty());
  }

  @Test
  void testStoreWithNullMetadata() {
    // Given
    String docId = "doc_null_metadata";
    byte[] content = "Null metadata".getBytes(StandardCharsets.UTF_8);

    // When
    documentStore.store(docId, content, null);
    Optional<RedisVLDocumentStore.Document> retrieved = documentStore.retrieve(docId);

    // Then
    assertTrue(retrieved.isPresent());
    assertArrayEquals(content, retrieved.get().content());
    assertTrue(retrieved.get().metadata().isEmpty());
  }

  @Test
  void testUpdateDocument() {
    // Given - Store initial document
    String docId = "doc_update";
    byte[] initialContent = "Initial".getBytes(StandardCharsets.UTF_8);
    documentStore.store(docId, initialContent, Map.of("version", "1"));

    // When - Update with new content
    byte[] updatedContent = "Updated".getBytes(StandardCharsets.UTF_8);
    documentStore.store(docId, updatedContent, Map.of("version", "2"));
    Optional<RedisVLDocumentStore.Document> retrieved = documentStore.retrieve(docId);

    // Then
    assertTrue(retrieved.isPresent());
    assertArrayEquals(updatedContent, retrieved.get().content());
    assertEquals("2", retrieved.get().metadata().get("version"));
  }

  @Test
  void testStoreNullContent() {
    // When/Then
    assertThrows(IllegalArgumentException.class, () -> documentStore.store("id", null, Map.of()));
  }

  @Test
  void testStoreNullId() {
    // When/Then
    assertThrows(
        IllegalArgumentException.class,
        () -> documentStore.store(null, "content".getBytes(StandardCharsets.UTF_8), Map.of()));
  }

  @Test
  void testBuilderPattern() {
    // Given
    RedisVLDocumentStore built =
        RedisVLDocumentStore.builder().jedis(jedis).keyPrefix("custom_prefix:").build();

    // When
    built.store("test", "content".getBytes(StandardCharsets.UTF_8), Map.of());
    Optional<RedisVLDocumentStore.Document> retrieved = built.retrieve("test");

    // Then
    assertNotNull(built);
    assertTrue(retrieved.isPresent());

    // Cleanup
    built.delete("test");
  }

  @Test
  void testListDocumentIds() {
    // Given
    documentStore.store("doc1", "Content 1".getBytes(StandardCharsets.UTF_8), Map.of());
    documentStore.store("doc2", "Content 2".getBytes(StandardCharsets.UTF_8), Map.of());
    documentStore.store("doc3", "Content 3".getBytes(StandardCharsets.UTF_8), Map.of());

    // When
    List<String> ids = documentStore.listDocumentIds();

    // Then
    assertEquals(3, ids.size());
    assertTrue(ids.containsAll(List.of("doc1", "doc2", "doc3")));
  }
}
