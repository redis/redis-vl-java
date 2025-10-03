package com.redis.vl.storage;

import com.github.f4b6a3.ulid.UlidCreator;
import com.redis.vl.schema.IndexSchema;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.*;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Getter;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;

/**
 * Base class for internal storage handling in Redis.
 *
 * <p>Provides foundational methods for key management, data preprocessing, validation, and basic
 * read/write operations.
 */
public abstract class BaseStorage {

  /** The index schema for this storage instance. */
  @Getter(AccessLevel.NONE)
  protected final IndexSchema indexSchema;

  /** Default batch size for bulk operations. */
  protected int defaultBatchSize = 200;

  /**
   * Creates a new BaseStorage instance.
   *
   * @param indexSchema The index schema for this storage
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "IndexSchema needs to be mutable for Redis operations")
  public BaseStorage(IndexSchema indexSchema) {
    // Store a defensive copy if needed, or just store as-is if immutable
    this.indexSchema = indexSchema;
  }

  /**
   * Create a Redis key using a combination of a prefix, separator, and the identifier.
   *
   * @param id The unique identifier for the Redis entry
   * @param prefix A prefix to append before the key value
   * @param keySeparator A separator to insert between prefix and key value
   * @return The fully formed Redis key
   */
  protected static String createKey(String id, String prefix, String keySeparator) {
    if (prefix == null || prefix.isEmpty()) {
      return id;
    } else {
      return prefix + keySeparator + id;
    }
  }

  /**
   * Apply a preprocessing function to the object if provided.
   *
   * @param obj Object to preprocess
   * @param preprocess Function to process the object
   * @return Processed object as a map
   */
  protected static Map<String, Object> preprocessObject(
      Map<String, Object> obj, Function<Map<String, Object>, Map<String, Object>> preprocess) {
    if (preprocess != null) {
      return preprocess.apply(obj);
    }
    return obj;
  }

  /**
   * Get the index schema.
   *
   * @return The index schema for this storage
   */
  protected IndexSchema getIndexSchema() {
    return indexSchema;
  }

  /**
   * Construct a Redis key for a given object, optionally using a specified field from the object as
   * the key.
   *
   * @param obj The object from which to construct the key
   * @param idField The field to use as the key, if provided
   * @return The constructed Redis key for the object
   * @throws IllegalArgumentException If the id_field is not found in the object
   */
  protected String createKeyForObject(Map<String, Object> obj, String idField) {
    String keyValue;
    if (idField == null) {
      keyValue = UlidCreator.getUlid().toString();
    } else {
      if (!obj.containsKey(idField)) {
        throw new com.redis.vl.exceptions.RedisVLException("Missing id field: " + idField);
      }
      keyValue = obj.get(idField).toString();
    }

    return createKey(
        keyValue, indexSchema.getIndex().getPrefix(), indexSchema.getIndex().getKeySeparator());
  }

  /**
   * Generate Redis keys for a list of objects.
   *
   * @param objects List of objects
   * @param keys Optional iterable of keys
   * @param idField Field to use as the key
   * @return List of generated keys
   */
  protected List<String> getKeys(
      List<Map<String, Object>> objects, List<String> keys, String idField) {
    List<String> generatedKeys = new ArrayList<>();

    if (keys != null && keys.size() != objects.size()) {
      throw new IllegalArgumentException(
          "Length of provided keys does not match the length of objects.");
    }

    for (int i = 0; i < objects.size(); i++) {
      String key;
      if (keys != null) {
        key = keys.get(i);
      } else {
        key = createKeyForObject(objects.get(i), idField);
      }
      generatedKeys.add(key);
    }
    return generatedKeys;
  }

  /**
   * Validate an object against the schema.
   *
   * @param obj The object to validate
   * @return Validated object
   */
  protected Map<String, Object> validate(Map<String, Object> obj) {
    if (indexSchema == null || indexSchema.getFields() == null) {
      return obj;
    }

    // Only validate the fields that are present in the object
    for (Map.Entry<String, Object> entry : obj.entrySet()) {
      String fieldName = entry.getKey();
      Object value = entry.getValue();

      // Find the field definition in the schema
      com.redis.vl.schema.BaseField field =
          indexSchema.getFields().stream()
              .filter(f -> f.getName().equals(fieldName))
              .findFirst()
              .orElse(null);

      if (field != null && value != null) {
        com.redis.vl.schema.FieldType fieldType = field.getFieldType();

        // Validate based on field type
        switch (fieldType) {
          case NUMERIC:
            if (!(value instanceof Number)) {
              throw new IllegalArgumentException(
                  "Field '"
                      + fieldName
                      + "' should be numeric but got: "
                      + value.getClass().getSimpleName()
                      + " (validation error)");
            }
            break;
          case VECTOR:
            if (!(value instanceof float[])
                && !(value instanceof double[])
                && !(value instanceof byte[])
                && !(value instanceof List)) {
              throw new IllegalArgumentException(
                  "Field '"
                      + fieldName
                      + "' should be a vector but got: "
                      + value.getClass().getSimpleName()
                      + " (validation error)");
            }
            // TODO: Check vector dimensions
            break;
          case TEXT:
          case TAG:
            if (!(value instanceof String)) {
              throw new IllegalArgumentException(
                  "Field '"
                      + fieldName
                      + "' should be string but got: "
                      + value.getClass().getSimpleName()
                      + " (validation error)");
            }
            break;
          case GEO:
            // TODO: Implement geo validation
            break;
        }
      }
    }

    return obj;
  }

  /**
   * Preprocess and validate a list of objects.
   *
   * @param objects List of objects to preprocess and validate
   * @param idField Field to use as the key
   * @param keys Optional list of keys
   * @param preprocess Optional preprocessing function
   * @param doValidate Whether to validate against schema
   * @return List of tuples (key, processed_obj) for valid objects
   */
  protected List<KeyValuePair> preprocessAndValidateObjects(
      List<Map<String, Object>> objects,
      String idField,
      List<String> keys,
      Function<Map<String, Object>, Map<String, Object>> preprocess,
      boolean doValidate) {

    List<KeyValuePair> preparedObjects = new ArrayList<>();

    for (int i = 0; i < objects.size(); i++) {
      try {
        Map<String, Object> obj = objects.get(i);

        // Generate key
        String key;
        if (keys != null && i < keys.size()) {
          key = keys.get(i);
        } else {
          key = createKeyForObject(obj, idField);
        }

        // Preprocess
        Map<String, Object> processedObj = preprocessObject(obj, preprocess);

        // Check for null from preprocessing
        if (preprocess != null && processedObj == null) {
          throw new com.redis.vl.exceptions.RedisVLException("Preprocess function returned null");
        }

        // Validate if enabled
        if (doValidate) {
          processedObj = validate(processedObj);
        }

        // Store valid object with its key
        preparedObjects.add(new KeyValuePair(key, processedObj));

      } catch (IllegalArgumentException e) {
        // Re-throw validation errors as-is
        throw e;
      } catch (Exception e) {
        throw new com.redis.vl.exceptions.RedisVLException(
            "Error processing object at index " + i + ": " + e.getMessage(), e);
      }
    }

    return preparedObjects;
  }

  /**
   * Write a batch of objects to Redis. This method returns a list of Redis keys written to the
   * database.
   *
   * @param redisClient A Redis client used for writing data
   * @param objects An iterable of objects to store
   * @param idField Field used as the key for each object
   * @param keys Optional list of keys, must match the length of objects if provided
   * @param ttl Time-to-live in seconds for each key
   * @param batchSize Number of objects to write in a single Redis pipeline execution
   * @param preprocess A function to preprocess objects before storage
   * @param doValidate Whether to validate objects against schema
   * @return List of keys written to Redis
   */
  public List<String> write(
      UnifiedJedis redisClient,
      List<Map<String, Object>> objects,
      String idField,
      List<String> keys,
      Integer ttl,
      Integer batchSize,
      Function<Map<String, Object>, Map<String, Object>> preprocess,
      boolean doValidate) {

    if (keys != null && keys.size() != objects.size()) {
      throw new IllegalArgumentException("Length of keys does not match the length of objects");
    }

    if (batchSize == null) {
      batchSize = defaultBatchSize;
    }

    if (objects.isEmpty()) {
      return Collections.emptyList();
    }

    // Pass 1: Preprocess and validate all objects
    List<KeyValuePair> preparedObjects =
        preprocessAndValidateObjects(objects, idField, keys, preprocess, doValidate);

    // Pass 2: Write all valid objects in batches
    List<String> addedKeys = new ArrayList<>();

    try (Pipeline pipeline = (Pipeline) redisClient.pipelined()) {
      for (int i = 0; i < preparedObjects.size(); i++) {
        KeyValuePair kvp = preparedObjects.get(i);
        set(pipeline, kvp.key, kvp.value);

        // Set TTL if provided
        if (ttl != null) {
          pipeline.expire(kvp.key, ttl);
        }

        addedKeys.add(kvp.key);

        // Execute in batches
        if ((i + 1) % batchSize == 0) {
          pipeline.sync();
        }
      }

      // Execute any remaining commands
      if (preparedObjects.size() % batchSize != 0) {
        pipeline.sync();
      }
    }

    return addedKeys;
  }

  /**
   * Write a batch of objects to Redis with default parameters.
   *
   * @param redisClient Redis client
   * @param objects Objects to write
   * @return List of keys written
   */
  public List<String> write(UnifiedJedis redisClient, List<Map<String, Object>> objects) {
    return write(redisClient, objects, null, null, null, null, null, false);
  }

  /**
   * Write with id field.
   *
   * @param redisClient Redis client
   * @param objects Objects to write
   * @param idField Field to use as key
   * @param keys Custom keys
   * @return List of keys written
   */
  public List<String> write(
      UnifiedJedis redisClient,
      List<Map<String, Object>> objects,
      String idField,
      List<String> keys) {
    return write(redisClient, objects, idField, keys, null, null, null, false);
  }

  /**
   * Write with TTL.
   *
   * @param redisClient Redis client
   * @param objects Objects to write
   * @param idField Field to use as key
   * @param keys Custom keys
   * @param ttl Time to live in seconds
   * @return List of keys written
   */
  public List<String> write(
      UnifiedJedis redisClient,
      List<Map<String, Object>> objects,
      String idField,
      List<String> keys,
      Integer ttl) {
    return write(redisClient, objects, idField, keys, ttl, null, null, false);
  }

  /**
   * Write with batch size.
   *
   * @param redisClient Redis client
   * @param objects Objects to write
   * @param idField Field to use as key
   * @param keys Custom keys
   * @param ttl Time to live
   * @param batchSize Batch size for pipeline
   * @return List of keys written
   */
  public List<String> write(
      UnifiedJedis redisClient,
      List<Map<String, Object>> objects,
      String idField,
      List<String> keys,
      Integer ttl,
      Integer batchSize) {
    return write(redisClient, objects, idField, keys, ttl, batchSize, null, false);
  }

  /**
   * Write with preprocessing.
   *
   * @param redisClient Redis client
   * @param objects Objects to write
   * @param idField Field to use as key
   * @param keys Custom keys
   * @param ttl Time to live
   * @param batchSize Batch size
   * @param preprocess Preprocessing function
   * @return List of keys written
   */
  public List<String> write(
      UnifiedJedis redisClient,
      List<Map<String, Object>> objects,
      String idField,
      List<String> keys,
      Integer ttl,
      Integer batchSize,
      Function<Map<String, Object>, Map<String, Object>> preprocess) {
    return write(redisClient, objects, idField, keys, ttl, batchSize, preprocess, false);
  }

  /**
   * Retrieve objects from Redis by keys.
   *
   * @param redisClient Synchronous Redis client
   * @param keys Keys to retrieve from Redis
   * @param batchSize Number of objects to retrieve in a single Redis pipeline execution
   * @return List of objects pulled from redis
   */
  public List<Map<String, Object>> get(
      UnifiedJedis redisClient, Collection<String> keys, Integer batchSize) {
    List<Map<String, Object>> results = new ArrayList<>();

    if (keys == null || keys.isEmpty()) {
      return results;
    }

    // TODO: Implement batching for large key sets
    // Currently not using batchSize parameter

    // Use a pipeline to batch the retrieval
    List<Response<Map<String, Object>>> responses = new ArrayList<>();

    try (Pipeline pipeline = (Pipeline) redisClient.pipelined()) {
      for (String key : keys) {
        Response<Map<String, Object>> response = getResponse(pipeline, key);
        responses.add(response);
      }

      // Execute all commands
      pipeline.sync();
    }

    // Process results
    for (Response<Map<String, Object>> response : responses) {
      Map<String, Object> map = response.get();
      if (map != null) {
        results.add(convertBytes(map));
      }
    }

    return results;
  }

  /**
   * Retrieve objects from Redis by keys with default batch size.
   *
   * @param redisClient Redis client
   * @param keys Keys to retrieve
   * @return List of objects
   */
  public List<Map<String, Object>> get(UnifiedJedis redisClient, Collection<String> keys) {
    return get(redisClient, keys, null);
  }

  /**
   * Convert byte arrays in the map to appropriate types.
   *
   * @param map Map with potential byte arrays
   * @return Map with converted values
   */
  protected Map<String, Object> convertBytes(Map<String, Object> map) {
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object value = entry.getValue();
      // Simply pass through all values as-is
      result.put(entry.getKey(), value);
    }
    return result;
  }

  // Abstract methods to be implemented by subclasses
  /**
   * Set a key-value pair in Redis using a pipeline.
   *
   * @param pipeline The Redis pipeline to use
   * @param key The Redis key
   * @param obj The object to store
   */
  protected abstract void set(Pipeline pipeline, String key, Map<String, Object> obj);

  /**
   * Get a response for retrieving a value from Redis using a pipeline.
   *
   * @param pipeline The Redis pipeline to use
   * @param key The Redis key
   * @return Response containing the retrieved object
   */
  protected abstract Response<Map<String, Object>> getResponse(Pipeline pipeline, String key);

  /** Helper class for key-value pairs used during preprocessing and validation. */
  protected static class KeyValuePair {
    final String key;
    final Map<String, Object> value;

    KeyValuePair(String key, Map<String, Object> value) {
      this.key = key;
      this.value = value;
    }
  }
}
