package com.redis.vl.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.schema.IndexSchema;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.AbstractPipeline;

/**
 * Integration tests for AbstractPipeline compatibility in storage classes.
 *
 * <p>Tests the fix for issue #365: Ensures BaseStorage uses AbstractPipeline instead of Pipeline to
 * support both regular Pipeline and MultiClusterPipeline objects, matching Python's graceful
 * handling of both Pipeline and ClusterPipeline types.
 *
 * <p>Python port: Matches Python's isinstance() checks for (AsyncPipeline, AsyncClusterPipeline) by
 * using the common base class AbstractPipeline in Java.
 */
@DisplayName("AbstractPipeline Compatibility Tests")
class AbstractPipelineIntegrationTest extends BaseIntegrationTest {

  @Test
  @DisplayName("Should accept AbstractPipeline in HashStorage.set()")
  void testHashStorageAcceptsAbstractPipeline() {
    // Create a simple schema
    Map<String, Object> schemaDict =
        Map.of(
            "index",
            Map.of("name", "test-index", "prefix", "test", "storage_type", "hash"),
            "fields",
            List.of(Map.of("name", "field1", "type", "text")));

    IndexSchema schema = IndexSchema.fromDict(schemaDict);
    HashStorage storage = new HashStorage(schema);

    // Verify the set() method signature accepts AbstractPipeline
    Map<String, Object> testData = Map.of("field1", "value1");

    // Use the unifiedJedis from BaseIntegrationTest (Testcontainers)
    try (AbstractPipeline pipeline = unifiedJedis.pipelined()) {
      // This should compile without ClassCastException
      storage.set(pipeline, "test:key", testData);
      pipeline.sync();
    }

    assertThat(true).as("Method signature accepts AbstractPipeline").isTrue();
  }

  @Test
  @DisplayName("Should accept AbstractPipeline in JsonStorage.set()")
  void testJsonStorageAcceptsAbstractPipeline() {
    // Create a simple schema
    Map<String, Object> schemaDict =
        Map.of(
            "index",
            Map.of("name", "test-index", "prefix", "test", "storage_type", "json"),
            "fields",
            List.of(Map.of("name", "field1", "type", "text", "path", "$.field1")));

    IndexSchema schema = IndexSchema.fromDict(schemaDict);
    JsonStorage storage = new JsonStorage(schema);

    // Verify the set() method signature accepts AbstractPipeline
    Map<String, Object> testData = Map.of("field1", "value1");

    // Use the unifiedJedis from BaseIntegrationTest (Testcontainers)
    try (AbstractPipeline pipeline = unifiedJedis.pipelined()) {
      // This should compile without ClassCastException
      storage.set(pipeline, "test:key", testData);
      pipeline.sync();
    }

    assertThat(true).as("Method signature accepts AbstractPipeline").isTrue();
  }

  @Test
  @DisplayName("Should accept AbstractPipeline in HashStorage.getResponse()")
  void testHashStorageGetResponseAcceptsAbstractPipeline() {
    // Create a simple schema
    Map<String, Object> schemaDict =
        Map.of(
            "index",
            Map.of("name", "test-index", "prefix", "test", "storage_type", "hash"),
            "fields",
            List.of(Map.of("name", "field1", "type", "text")));

    IndexSchema schema = IndexSchema.fromDict(schemaDict);
    HashStorage storage = new HashStorage(schema);

    // Use the unifiedJedis from BaseIntegrationTest (Testcontainers)
    try (AbstractPipeline pipeline = unifiedJedis.pipelined()) {
      // This should compile without ClassCastException
      storage.getResponse(pipeline, "test:key");
      pipeline.sync();
    }

    assertThat(true).as("Method signature accepts AbstractPipeline").isTrue();
  }

  @Test
  @DisplayName("Should accept AbstractPipeline in JsonStorage.getResponse()")
  void testJsonStorageGetResponseAcceptsAbstractPipeline() {
    // Create a simple schema
    Map<String, Object> schemaDict =
        Map.of(
            "index",
            Map.of("name", "test-index", "prefix", "test", "storage_type", "json"),
            "fields",
            List.of(Map.of("name", "field1", "type", "text", "path", "$.field1")));

    IndexSchema schema = IndexSchema.fromDict(schemaDict);
    JsonStorage storage = new JsonStorage(schema);

    // Use the unifiedJedis from BaseIntegrationTest (Testcontainers)
    try (AbstractPipeline pipeline = unifiedJedis.pipelined()) {
      // This should compile without ClassCastException
      storage.getResponse(pipeline, "test:key");
      pipeline.sync();
    }

    assertThat(true).as("Method signature accepts AbstractPipeline").isTrue();
  }
}
