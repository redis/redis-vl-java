package com.redis.vl.index;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SchemaValidationTest extends BaseIntegrationTest {

  private static final String TEST_PREFIX =
      "validation_" + UUID.randomUUID().toString().substring(0, 8);
  private SearchIndex index;

  @BeforeEach
  void setUp() {
    // Create a simple schema for testing
    Map<String, Object> schemaMap = new HashMap<>();

    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "validation_test_" + TEST_PREFIX);
    indexConfig.put("prefix", "validation_test_docs_" + TEST_PREFIX);
    schemaMap.put("index", indexConfig);

    // Add vector field for validation testing
    Map<String, Object> vectorField = new HashMap<>();
    vectorField.put("name", "test_vector");
    vectorField.put("type", "vector");

    Map<String, Object> vectorAttrs = new HashMap<>();
    vectorAttrs.put("dims", 3);
    vectorAttrs.put("distance_metric", "cosine");
    vectorAttrs.put("algorithm", "flat");
    vectorAttrs.put("datatype", "float32");
    vectorField.put("attrs", vectorAttrs);

    // Add text field for validation testing
    Map<String, Object> textField = new HashMap<>();
    textField.put("name", "test_text");
    textField.put("type", "text");

    // Add numeric field for validation testing
    Map<String, Object> numericField = new HashMap<>();
    numericField.put("name", "test_number");
    numericField.put("type", "numeric");

    schemaMap.put("fields", java.util.List.of(vectorField, textField, numericField));

    // Create index using the TestContainers unifiedJedis
    index = SearchIndex.fromDict(schemaMap, unifiedJedis);

    try {
      index.create(true);
    } catch (Exception e) {
      // Index might already exist, ignore
    }
  }

  @Test
  void testValidDocumentPasses() {
    // Valid document should not throw exception
    Map<String, Object> validDoc = new HashMap<>();
    validDoc.put("test_vector", new float[] {0.1f, 0.2f, 0.3f});
    validDoc.put("test_text", "valid text");
    validDoc.put("test_number", 42);

    assertDoesNotThrow(
        () -> {
          index.addDocument("valid_doc", validDoc);
        });
  }

  @Test
  void testInvalidVectorTypeFails() {
    Map<String, Object> invalidDoc = new HashMap<>();
    invalidDoc.put("test_vector", true); // Boolean instead of vector

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> index.addDocument("invalid_doc", invalidDoc));

    assertTrue(exception.getMessage().contains("Schema validation failed for field 'test_vector'"));
    assertTrue(
        exception.getMessage().contains("Field expects bytes (vector data), but got Boolean"));
  }

  @Test
  void testInvalidVectorDimensionsFails() {
    Map<String, Object> invalidDoc = new HashMap<>();
    invalidDoc.put("test_vector", new float[] {0.1f, 0.2f}); // 2 dims instead of 3

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> index.addDocument("invalid_doc", invalidDoc));

    assertTrue(
        exception
            .getMessage()
            .contains("Vector field 'test_vector' expects 3 dimensions, but got 2"));
  }

  @Test
  void testInvalidTextTypeFails() {
    Map<String, Object> invalidDoc = new HashMap<>();
    invalidDoc.put("test_text", 123); // Number instead of string

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> index.addDocument("invalid_doc", invalidDoc));

    assertTrue(exception.getMessage().contains("Schema validation failed for field 'test_text'"));
    assertTrue(exception.getMessage().contains("Field expects a string, but got Integer"));
  }

  @Test
  void testInvalidNumericTypeFails() {
    Map<String, Object> invalidDoc = new HashMap<>();
    invalidDoc.put("test_number", "not a number"); // String instead of number

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> index.addDocument("invalid_doc", invalidDoc));

    assertTrue(exception.getMessage().contains("Schema validation failed for field 'test_number'"));
    assertTrue(exception.getMessage().contains("Field expects a number, but got String"));
  }
}
