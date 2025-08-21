package com.redis.vl.index;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.schema.IndexSchema;
import java.util.*;
import org.junit.jupiter.api.Test;

public class ListConversionTest {

  @Test
  void testListToFloatArrayConversion() throws Exception {
    // Create schema
    Map<String, Object> schemaMap = new HashMap<>();

    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_list_conversion");
    indexConfig.put("prefix", "test_list_docs");
    schemaMap.put("index", indexConfig);

    List<Map<String, Object>> fields = new ArrayList<>();
    fields.add(Map.of("name", "user", "type", "tag"));

    Map<String, Object> vectorAttrs = new HashMap<>();
    vectorAttrs.put("dims", 3);
    vectorAttrs.put("distance_metric", "cosine");
    vectorAttrs.put("algorithm", "flat");
    vectorAttrs.put("datatype", "float32");

    Map<String, Object> vectorField = new HashMap<>();
    vectorField.put("name", "user_embedding");
    vectorField.put("type", "vector");
    vectorField.put("attrs", vectorAttrs);
    fields.add(vectorField);

    schemaMap.put("fields", fields);

    IndexSchema schema = IndexSchema.fromDict(schemaMap);
    SearchIndex index = new SearchIndex(schema);

    // Test document with List for vector field
    Map<String, Object> testDoc = new HashMap<>();
    testDoc.put("user", "test");
    testDoc.put("user_embedding", Arrays.asList(0.1f, 0.2f, 0.3f));

    // Use reflection to test preprocessDocument
    java.lang.reflect.Method preprocessMethod =
        SearchIndex.class.getDeclaredMethod("preprocessDocument", Map.class);
    preprocessMethod.setAccessible(true);

    @SuppressWarnings("unchecked")
    Map<String, Object> processed = (Map<String, Object>) preprocessMethod.invoke(index, testDoc);

    // Verify conversion
    Object embedding = processed.get("user_embedding");
    assertNotNull(embedding);
    assertInstanceOf(float[].class, embedding, "List should be converted to float[]");

    float[] arr = (float[]) embedding;
    assertEquals(3, arr.length, "Array should have 3 elements");
    assertEquals(0.1f, arr[0], 0.001f);
    assertEquals(0.2f, arr[1], 0.001f);
    assertEquals(0.3f, arr[2], 0.001f);

    // Verify validation also works
    java.lang.reflect.Method validateMethod =
        SearchIndex.class.getDeclaredMethod("validateDocument", Map.class);
    validateMethod.setAccessible(true);

    // Should not throw exception
    assertDoesNotThrow(
        () -> {
          validateMethod.invoke(index, processed);
        });
  }

  @Test
  void testListValidationDirectly() throws Exception {
    // Create schema
    Map<String, Object> schemaMap = new HashMap<>();

    Map<String, Object> indexConfig = new HashMap<>();
    indexConfig.put("name", "test_list_validation");
    indexConfig.put("prefix", "test_list_validation_docs");
    schemaMap.put("index", indexConfig);

    List<Map<String, Object>> fields = new ArrayList<>();

    Map<String, Object> vectorAttrs = new HashMap<>();
    vectorAttrs.put("dims", 3);
    vectorAttrs.put("distance_metric", "cosine");
    vectorAttrs.put("algorithm", "flat");
    vectorAttrs.put("datatype", "float32");

    Map<String, Object> vectorField = new HashMap<>();
    vectorField.put("name", "user_embedding");
    vectorField.put("type", "vector");
    vectorField.put("attrs", vectorAttrs);
    fields.add(vectorField);

    schemaMap.put("fields", fields);

    IndexSchema schema = IndexSchema.fromDict(schemaMap);
    SearchIndex index = new SearchIndex(schema);

    // Test document with List for vector field
    Map<String, Object> testDoc = new HashMap<>();
    testDoc.put("user_embedding", Arrays.asList(0.1f, 0.2f, 0.3f));

    // Use reflection to test validateDocument directly
    java.lang.reflect.Method validateMethod =
        SearchIndex.class.getDeclaredMethod("validateDocument", Map.class);
    validateMethod.setAccessible(true);

    // Should not throw exception - Lists should be accepted now
    assertDoesNotThrow(
        () -> {
          validateMethod.invoke(index, testDoc);
        });

    // Test invalid dimensions with List
    Map<String, Object> invalidDoc = new HashMap<>();
    invalidDoc.put("user_embedding", Arrays.asList(0.1f, 0.2f)); // Only 2 dimensions instead of 3

    java.lang.reflect.InvocationTargetException exception =
        assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> validateMethod.invoke(index, invalidDoc));

    // Check the wrapped exception
    Throwable cause = exception.getCause();
    assertInstanceOf(IllegalArgumentException.class, cause);
    assertTrue(cause.getMessage().contains("expects 3 dimensions, but got 2"));
  }
}
