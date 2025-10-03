package com.redis.vl.storage;

import com.redis.vl.schema.BaseField;
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.VectorField;
import com.redis.vl.utils.ArrayUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

/**
 * Internal subclass of BaseStorage for the Redis hash data type.
 *
 * <p>Implements hash-specific logic for validation and read/write operations in Redis.
 */
public class HashStorage extends BaseStorage {

  /**
   * Creates a new HashStorage instance for managing Redis hash data structures.
   *
   * @param indexSchema The index schema defining the structure of data to be stored
   */
  public HashStorage(IndexSchema indexSchema) {
    super(indexSchema);
  }

  @Override
  protected void set(Pipeline pipeline, String key, Map<String, Object> obj) {
    Map<byte[], byte[]> binaryFields = new HashMap<>();
    Map<String, String> stringFields = new HashMap<>();

    for (Map.Entry<String, Object> entry : obj.entrySet()) {
      String fieldName = entry.getKey();
      Object value = entry.getValue();

      if (value == null) {
        continue;
      }

      // Check if this is a vector field
      BaseField field = findField(fieldName);
      if (field instanceof VectorField) {
        // Store vectors as binary data
        byte[] vectorBytes = null;
        if (value instanceof byte[]) {
          vectorBytes = (byte[]) value;
        } else if (value instanceof float[]) {
          vectorBytes = ArrayUtils.floatArrayToBytes((float[]) value);
        } else if (value instanceof double[]) {
          float[] floats = ArrayUtils.doubleArrayToFloats((double[]) value);
          vectorBytes = ArrayUtils.floatArrayToBytes(floats);
        }
        if (vectorBytes != null) {
          binaryFields.put(fieldName.getBytes(StandardCharsets.UTF_8), vectorBytes);
        }
      } else {
        // Store other fields as strings
        stringFields.put(fieldName, value.toString());
      }
    }

    // Set binary fields
    if (!binaryFields.isEmpty()) {
      pipeline.hset(key.getBytes(StandardCharsets.UTF_8), binaryFields);
    }
    // Set string fields
    if (!stringFields.isEmpty()) {
      pipeline.hset(key, stringFields);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  protected Response<Map<String, Object>> getResponse(Pipeline pipeline, String key) {
    // For hash, we use hgetAll to get all fields
    Response<Map<String, String>> response = pipeline.hgetAll(key);
    // We need to return Response<Map<String, Object>> so cast it
    return (Response<Map<String, Object>>) (Response<?>) response;
  }

  // Override the get method to handle binary vector fields
  @Override
  public List<Map<String, Object>> get(
      redis.clients.jedis.UnifiedJedis redisClient,
      java.util.Collection<String> keys,
      Integer batchSize) {
    List<Map<String, Object>> results = new ArrayList<>();

    if (keys == null || keys.isEmpty()) {
      return results;
    }

    // TODO: Implement batching for large key sets
    // Currently not using batchSize parameter

    // Use a pipeline to batch the retrieval
    List<Response<Map<String, String>>> stringResponses = new ArrayList<>();
    Map<String, List<Response<byte[]>>> vectorResponses = new HashMap<>();

    try (Pipeline pipeline = (Pipeline) redisClient.pipelined()) {
      // Get all string fields and identify vector fields
      for (String key : keys) {
        Response<Map<String, String>> response = pipeline.hgetAll(key);
        stringResponses.add(response);

        // For each vector field, get the binary data
        if (indexSchema != null && indexSchema.getFields() != null) {
          List<Response<byte[]>> keyVectorResponses = new ArrayList<>();
          for (BaseField field : indexSchema.getFields()) {
            if (field instanceof VectorField) {
              Response<byte[]> vectorResponse =
                  pipeline.hget(
                      key.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                      field.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
              keyVectorResponses.add(vectorResponse);
            }
          }
          if (!keyVectorResponses.isEmpty()) {
            vectorResponses.put(key, keyVectorResponses);
          }
        }
      }

      // Execute all commands
      pipeline.sync();
    }

    // Process results
    int keyIndex = 0;
    for (String key : keys) {
      Map<String, String> stringMap = stringResponses.get(keyIndex).get();
      if (stringMap != null && !stringMap.isEmpty()) {
        Map<String, Object> result = new HashMap<>();

        // Add all non-vector fields
        for (Map.Entry<String, String> entry : stringMap.entrySet()) {
          String fieldName = entry.getKey();
          BaseField field = findField(fieldName);
          if (!(field instanceof VectorField)) {
            result.put(fieldName, entry.getValue());
          }
        }

        // Add vector fields from binary responses
        if (vectorResponses.containsKey(key)) {
          int vectorFieldIndex = 0;
          for (BaseField field : indexSchema.getFields()) {
            if (field instanceof VectorField) {
              byte[] vectorBytes = vectorResponses.get(key).get(vectorFieldIndex).get();
              if (vectorBytes != null) {
                result.put(field.getName(), vectorBytes);
              }
              vectorFieldIndex++;
            }
          }
        }

        results.add(result);
      }
      keyIndex++;
    }

    return results;
  }

  @Override
  protected Map<String, Object> convertBytes(Map<String, Object> map) {
    // This method is not used anymore since we override the get method
    // But keep it for compatibility
    return map;
  }

  private BaseField findField(String fieldName) {
    if (indexSchema == null || indexSchema.getFields() == null) {
      return null;
    }
    return indexSchema.getFields().stream()
        .filter(f -> f.getName().equals(fieldName))
        .findFirst()
        .orElse(null);
  }
}
