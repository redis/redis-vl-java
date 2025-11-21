package com.redis.vl.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.redis.vl.exceptions.RedisVLException;
import com.redis.vl.query.*;
import com.redis.vl.redis.RedisConnectionManager;
import com.redis.vl.schema.BaseField;
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.VectorField;
import com.redis.vl.storage.BaseStorage;
import com.redis.vl.storage.HashStorage;
import com.redis.vl.storage.JsonStorage;
import com.redis.vl.utils.ArrayUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.SchemaField;

/**
 * Manages Redis search index operations.
 *
 * <p>This class is final to prevent finalizer attacks, as it throws exceptions in constructors for
 * input validation (SEI CERT OBJ11-J).
 */
@Slf4j
public final class SearchIndex {

  private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
  private static final ObjectMapper jsonMapper = new ObjectMapper();
  @Getter private final RedisConnectionManager connectionManager;

  @Getter
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Schema needs to be mutable for Python-like API compatibility")
  private final IndexSchema schema;

  private final BaseStorage storage;
  private Jedis client;
  private UnifiedJedis unifiedClient;
  @Getter private boolean validateOnLoad = false;

  /**
   * Create a SearchIndex with connection manager and schema
   *
   * @param connectionManager Redis connection manager
   * @param schema Index schema definition
   */
  public SearchIndex(RedisConnectionManager connectionManager, IndexSchema schema) {
    if (connectionManager == null) {
      throw new IllegalArgumentException("Connection manager cannot be null");
    }
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }
    this.connectionManager = connectionManager;
    this.schema = schema;
    this.storage = initializeStorage(schema);
  }

  /**
   * Create a SearchIndex with schema only (no connection)
   *
   * @param schema Index schema definition
   */
  public SearchIndex(IndexSchema schema) {
    this(schema, false);
  }

  /**
   * Create a SearchIndex with schema and validateOnLoad option
   *
   * @param schema Index schema definition
   * @param validateOnLoad Whether to validate documents on load
   */
  public SearchIndex(IndexSchema schema, boolean validateOnLoad) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }
    this.connectionManager = null;
    this.schema = schema;
    this.client = null;
    this.unifiedClient = null;
    this.validateOnLoad = validateOnLoad;
    this.storage = initializeStorage(schema);
  }

  /**
   * Create a SearchIndex with schema and Jedis client
   *
   * @param schema Index schema definition
   * @param client Jedis client for Redis operations
   */
  public SearchIndex(IndexSchema schema, Jedis client) {
    this(schema, client, false);
  }

  /**
   * Create a SearchIndex with schema, Jedis client, and validateOnLoad option
   *
   * @param schema Index schema definition
   * @param client Jedis client for Redis operations
   * @param validateOnLoad Whether to validate documents on load
   */
  public SearchIndex(IndexSchema schema, Jedis client, boolean validateOnLoad) {
    if (schema == null) {
      throw new IllegalArgumentException("Must provide a valid IndexSchema object");
    }
    if (client == null) {
      throw new IllegalArgumentException("Jedis client cannot be null");
    }
    this.connectionManager = null;
    this.schema = schema;
    // Store the client - this is the expected usage pattern for this library
    this.client = client;
    this.unifiedClient = null;
    this.validateOnLoad = validateOnLoad;
    this.storage = initializeStorage(schema);
  }

  /**
   * Create a SearchIndex with schema and Redis URL
   *
   * @param schema Index schema definition
   * @param redisUrl Redis connection URL
   */
  public SearchIndex(IndexSchema schema, String redisUrl) {
    this(schema, redisUrl, false);
  }

  /**
   * Create a SearchIndex with schema, Redis URL, and validateOnLoad option
   *
   * @param schema Index schema definition
   * @param redisUrl Redis connection URL
   * @param validateOnLoad Whether to validate documents on load
   */
  public SearchIndex(IndexSchema schema, String redisUrl, boolean validateOnLoad) {
    if (schema == null) {
      throw new IllegalArgumentException("Schema cannot be null");
    }
    if (redisUrl == null || redisUrl.trim().isEmpty()) {
      throw new IllegalArgumentException("Redis URL cannot be null or empty");
    }
    this.connectionManager = null;
    this.schema = schema;
    this.client = null;
    this.validateOnLoad = validateOnLoad;
    // Create UnifiedJedis from URL
    this.unifiedClient = new UnifiedJedis(redisUrl);
    this.storage = initializeStorage(schema);
  }

  /**
   * Create a SearchIndex with schema and UnifiedJedis client (preferred for RediSearch)
   *
   * @param schema Index schema definition
   * @param unifiedClient UnifiedJedis client for Redis operations
   */
  public SearchIndex(IndexSchema schema, UnifiedJedis unifiedClient) {
    this(schema, unifiedClient, false);
  }

  /**
   * Create a SearchIndex with schema, UnifiedJedis client, and validateOnLoad option
   *
   * @param schema Index schema definition
   * @param unifiedClient UnifiedJedis client for Redis operations
   * @param validateOnLoad Whether to validate documents on load
   */
  public SearchIndex(IndexSchema schema, UnifiedJedis unifiedClient, boolean validateOnLoad) {
    if (schema == null) {
      throw new IllegalArgumentException("Must provide a valid IndexSchema object");
    }
    if (unifiedClient == null) {
      throw new IllegalArgumentException("UnifiedJedis client cannot be null");
    }
    this.connectionManager = null;
    this.schema = schema;
    this.client = null;
    this.validateOnLoad = validateOnLoad;
    // Store the client - this is the expected usage pattern for this library
    this.unifiedClient = unifiedClient;
    this.storage = initializeStorage(schema);
  }

  /**
   * Create a SearchIndex from a YAML file
   *
   * @param yamlPath Path to the YAML file containing schema definition
   * @return SearchIndex instance
   */
  @SuppressWarnings("unchecked")
  public static SearchIndex fromYaml(String yamlPath) {
    try {
      Map<String, Object> data = yamlMapper.readValue(new File(yamlPath), Map.class);
      IndexSchema schema = IndexSchema.fromDict(data);
      return new SearchIndex(schema);
    } catch (Exception e) {
      throw new RedisVLException("Failed to load schema from YAML: " + yamlPath, e);
    }
  }

  /**
   * Create a SearchIndex from a dictionary/map
   *
   * @param dict Schema definition as a map
   * @return SearchIndex instance
   */
  public static SearchIndex fromDict(Map<String, Object> dict) {
    return fromDict(dict, (UnifiedJedis) null, false);
  }

  /**
   * Create a SearchIndex from a dictionary/map with a UnifiedJedis client
   *
   * @param dict Schema definition as a map
   * @param client UnifiedJedis client for Redis operations
   * @return SearchIndex instance
   */
  public static SearchIndex fromDict(Map<String, Object> dict, UnifiedJedis client) {
    if (dict == null) {
      throw new IllegalArgumentException("Schema dictionary cannot be null");
    }
    if (client == null) {
      throw new IllegalArgumentException("Redis client cannot be null");
    }
    IndexSchema schema = IndexSchema.fromDict(dict);
    return new SearchIndex(schema, client, false);
  }

  /**
   * Create a SearchIndex from a dictionary/map with a Redis URL
   *
   * @param dict Schema definition as a map
   * @param redisUrl Redis connection URL
   * @return SearchIndex instance
   */
  public static SearchIndex fromDict(Map<String, Object> dict, String redisUrl) {
    return fromDict(dict, redisUrl, false);
  }

  private static IndexSchema validateAndParseDict(Map<String, Object> dict) {
    if (dict == null) {
      throw new IllegalArgumentException("Schema dictionary cannot be null");
    }
    return IndexSchema.fromDict(dict);
  }

  private static void addParamsToSearchParams(
      redis.clients.jedis.search.FTSearchParams searchParams, Map<String, Object> params) {
    for (Map.Entry<String, Object> entry : params.entrySet()) {
      if ("vec".equals(entry.getKey()) && entry.getValue() instanceof byte[]) {
        searchParams.addParam(entry.getKey(), entry.getValue());
      } else if ("K".equals(entry.getKey()) && entry.getValue() instanceof Integer) {
        searchParams.addParam(entry.getKey(), entry.getValue());
      } else {
        searchParams.addParam(entry.getKey(), entry.getValue().toString());
      }
    }
  }

  /**
   * Create a SearchIndex from a dictionary/map with a UnifiedJedis client and validateOnLoad option
   *
   * @param dict Schema definition as a map
   * @param client UnifiedJedis client for Redis operations
   * @param validateOnLoad Whether to validate documents on load
   * @return SearchIndex instance
   */
  public static SearchIndex fromDict(
      Map<String, Object> dict, UnifiedJedis client, boolean validateOnLoad) {
    IndexSchema schema = validateAndParseDict(dict);
    if (client == null) {
      return new SearchIndex(schema, validateOnLoad);
    }
    return new SearchIndex(schema, client, validateOnLoad);
  }

  /**
   * Create a SearchIndex from a dictionary/map with a Redis URL and validateOnLoad option
   *
   * @param dict Schema definition as a map
   * @param redisUrl Redis connection URL
   * @param validateOnLoad Whether to validate documents on load
   * @return SearchIndex instance
   */
  public static SearchIndex fromDict(
      Map<String, Object> dict, String redisUrl, boolean validateOnLoad) {
    IndexSchema schema = validateAndParseDict(dict);
    if (redisUrl == null) {
      return new SearchIndex(schema, validateOnLoad);
    }
    return new SearchIndex(schema, redisUrl, validateOnLoad);
  }

  private static BaseField createFieldFromType(
      String fieldName, String fieldType, List<Object> fieldDef) {
    switch (fieldType.toUpperCase()) {
      case "TAG":
        return new com.redis.vl.schema.TagField(fieldName);
      case "TEXT":
        return new com.redis.vl.schema.TextField(fieldName);
      case "NUMERIC":
        return new com.redis.vl.schema.NumericField(fieldName);
      case "GEO":
        return new com.redis.vl.schema.GeoField(fieldName);
      case "VECTOR":
        // Extract vector parameters
        int dims = 0;
        String distanceMetric = "COSINE";
        String algorithm = "FLAT";

        for (int i = 0; i < fieldDef.size(); i++) {
          String item = fieldDef.get(i).toString();

          if ("dim".equals(item) && i + 1 < fieldDef.size()) {
            dims = Integer.parseInt(fieldDef.get(i + 1).toString());
          } else if ("distance_metric".equals(item) && i + 1 < fieldDef.size()) {
            distanceMetric = fieldDef.get(i + 1).toString();
          } else if ("algorithm".equals(item) && i + 1 < fieldDef.size()) {
            algorithm = fieldDef.get(i + 1).toString();
          }
        }

        return VectorField.builder()
            .name(fieldName)
            .dimensions(dims)
            .distanceMetric(VectorField.DistanceMetric.valueOf(distanceMetric))
            .algorithm(
                "HNSW".equals(algorithm)
                    ? redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.HNSW
                    : redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.FLAT)
            .build();
      default:
        log.warn("Unknown field type: {} for field: {}", fieldType, fieldName);
        return null;
    }
  }

  /**
   * Create a SearchIndex from an existing index in Redis
   *
   * @param indexName Name of the existing index
   * @param client UnifiedJedis client for Redis operations
   * @return SearchIndex instance
   */
  public static SearchIndex fromExisting(String indexName, UnifiedJedis client) {
    // Load index info from Redis
    Map<String, Object> info = client.ftInfo(indexName);

    // Extract index definition
    @SuppressWarnings("unchecked")
    List<Object> indexDef = (List<Object>) info.get("index_definition");

    // Build schema from the info
    var builder = IndexSchema.builder().name(indexName);

    // Extract prefix if available
    if (indexDef != null) {
      for (int i = 0; i < indexDef.size(); i += 2) {
        String key = indexDef.get(i).toString();
        if ("prefixes".equals(key) && i + 1 < indexDef.size()) {
          @SuppressWarnings("unchecked")
          List<String> prefixes = (List<String>) indexDef.get(i + 1);
          if (!prefixes.isEmpty()) {
            // Python port: preserve all prefixes (issue #258/#392)
            // Normalize single-element lists to string for backward compatibility
            if (prefixes.size() == 1) {
              builder.prefix(prefixes.get(0));
            } else {
              builder.prefix(prefixes);
            }
          }
        } else if ("key_type".equals(key) && i + 1 < indexDef.size()) {
          String keyType = indexDef.get(i + 1).toString();
          builder.storageType(
              "JSON".equals(keyType) ? IndexSchema.StorageType.JSON : IndexSchema.StorageType.HASH);
        }
      }
    }

    // Extract fields from attributes
    @SuppressWarnings("unchecked")
    List<Object> attributes = (List<Object>) info.get("attributes");

    if (attributes != null) {
      // Process attributes list - it's a list of field definitions
      for (Object attrObj : attributes) {
        if (attrObj instanceof List) {
          @SuppressWarnings("unchecked")
          List<Object> attr = (List<Object>) attrObj;
          if (!attr.isEmpty()) {
            // The attr list itself is the field definition
            List<Object> fieldDef = attr;

            String fieldName = null;
            String fieldType = null;
            String jsonPath = null;

            // Parse field definition
            for (int i = 0; i < fieldDef.size(); i++) {
              String item = fieldDef.get(i).toString();

              if ("identifier".equals(item) && i + 1 < fieldDef.size()) {
                fieldName = fieldDef.get(i + 1).toString();
              } else if ("attribute".equals(item) && i + 1 < fieldDef.size()) {
                jsonPath = fieldDef.get(i + 1).toString();
              } else if ("type".equals(item) && i + 1 < fieldDef.size()) {
                fieldType = fieldDef.get(i + 1).toString();
              }
            }

            // Create appropriate field based on type
            if (fieldName != null && fieldType != null) {
              BaseField field = createFieldFromType(fieldName, fieldType, fieldDef);
              if (field != null) {
                builder.field(field);
              }
            }
          }
        }
      }
    }

    IndexSchema schema = builder.build();
    return new SearchIndex(schema, client);
  }

  /** Get Jedis connection from either connectionManager or direct client */
  private Jedis getJedis() {
    if (client != null) {
      return client;
    } else if (connectionManager != null) {
      return connectionManager.getJedis();
    } else {
      throw new IllegalStateException("No Redis connection available for document operations");
    }
  }

  /** Get UnifiedJedis for RediSearch operations */
  private UnifiedJedis getUnifiedJedis() {
    if (unifiedClient != null) {
      return unifiedClient;
    } else {
      throw new IllegalStateException(
          "RediSearch operations require UnifiedJedis client. Please use SearchIndex(schema, unifiedJedis) constructor.");
    }
  }

  /** Initialize storage based on schema storage type */
  private BaseStorage initializeStorage(IndexSchema schema) {
    if (schema.getIndex().getStorageType() == IndexSchema.StorageType.JSON) {
      return new JsonStorage(schema);
    } else {
      return new HashStorage(schema);
    }
  }

  /**
   * Create the index in Redis using FT.CREATE
   *
   * @throws RuntimeException if index creation fails
   */
  public void create() {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      // Validate schema has fields
      if (schema == null || schema.getFields() == null || schema.getFields().isEmpty()) {
        throw new RedisVLException("Schema must have at least one field defined");
      }

      // Build the schema fields
      List<SchemaField> schemaFields = new ArrayList<>();
      for (BaseField field : schema.getFields()) {
        schemaFields.add(field.toJedisSchemaField());
      }

      // Build create params with index definition
      FTCreateParams createParams = FTCreateParams.createParams();

      // Set index type
      IndexDataType indexType =
          schema.getStorageType() == IndexSchema.StorageType.JSON
              ? IndexDataType.JSON
              : IndexDataType.HASH;

      // Set prefix if provided
      if (schema.getPrefix() != null && !schema.getPrefix().isEmpty()) {
        createParams.on(indexType).prefix(schema.getPrefix());
      } else {
        createParams.on(indexType);
      }

      // Set stopwords if configured (PR #436)
      List<String> stopwords = schema.getIndex().getStopwords();
      if (stopwords != null) {
        if (stopwords.isEmpty()) {
          // Empty list = disable stopwords (STOPWORDS 0)
          createParams.stopwords();
        } else {
          // Custom stopwords list
          createParams.stopwords(stopwords.toArray(new String[0]));
        }
      }
      // If stopwords is null, use Redis default stopwords (don't set anything)

      // Create the index
      String result = jedis.ftCreate(schema.getName(), createParams, schemaFields);

      log.info(
          "Created index: {} with {} fields, result: {}",
          schema.getName(),
          schema.getFields().size(),
          result);
    } catch (Exception e) {
      if (e instanceof IllegalStateException) {
        throw e;
      }
      throw new RuntimeException("Failed to create index: " + e.getMessage(), e);
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Drop the index using FT.DROPINDEX
   *
   * @return true if index was dropped, false if it didn't exist
   */
  public boolean drop() {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      // FT.DROPINDEX without DD option (don't delete documents)
      String indexName = getName();
      if (indexName == null) {
        return false;
      }
      String result = jedis.ftDropIndex(indexName);
      log.info("Dropped index: {}, result: {}", indexName, result);
      return "OK".equals(result);
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("Unknown index")) {
        return false;
      }
      throw new RuntimeException("Failed to drop index: " + e.getMessage(), e);
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Drop the index and delete all documents using FT.DROPINDEX DD
   *
   * @return true if index was dropped, false if it didn't exist
   */
  public boolean dropWithData() {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      // FT.DROPINDEX with DD option (delete documents)
      String indexName = getName();
      if (indexName == null) {
        return false;
      }
      String result = jedis.ftDropIndexDD(indexName);
      log.info("Dropped index with data: {}, result: {}", indexName, result);
      return "OK".equals(result);
    } catch (Exception e) {
      if (e.getMessage() != null && e.getMessage().contains("Unknown index")) {
        return false;
      }
      throw new RuntimeException("Failed to drop index with data: " + e.getMessage(), e);
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  // Additional methods for integration test compatibility

  /**
   * Check if index exists using FT.INFO
   *
   * @return true if index exists, false otherwise
   */
  public boolean exists() {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      // Try to get index info - will throw exception if it doesn't exist
      String indexName = getName();
      if (indexName == null) {
        return false;
      }
      Map<String, Object> info = jedis.ftInfo(indexName);
      return info != null && !info.isEmpty();
    } catch (Exception e) {
      // Index doesn't exist or error occurred
      return false;
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Recreate the index (drop if exists and create new)
   *
   * @throws RuntimeException if recreation fails
   */
  public void recreate() {
    drop();
    create();
  }

  /**
   * Preprocess document to convert Lists to arrays for vector fields
   *
   * @param document Document to preprocess
   * @return Preprocessed document with Lists converted to arrays
   */
  private Map<String, Object> preprocessDocument(Map<String, Object> document) {
    Map<String, Object> processed = new HashMap<>(document);

    if (schema == null || schema.getFields() == null) {
      return processed;
    }

    for (BaseField field : schema.getFields()) {
      String fieldName = field.getName();
      Object value = document.get(fieldName);

      if (field instanceof VectorField && value instanceof List) {
        @SuppressWarnings("unchecked")
        List<Number> list = (List<Number>) value;
        float[] floatArray = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
          floatArray[i] = list.get(i).floatValue();
        }
        processed.put(fieldName, floatArray);
      }
    }

    return processed;
  }

  /**
   * Validate a document against the schema
   *
   * @param document Document to validate
   * @throws IllegalArgumentException if validation fails
   */
  private void validateDocument(Map<String, Object> document) {
    if (schema == null || schema.getFields() == null) {
      return;
    }

    for (BaseField field : schema.getFields()) {
      String fieldName = field.getName();
      Object value = document.get(fieldName);

      // Check if field is missing - only required when validateOnLoad is true
      if (value == null) {
        if (validateOnLoad) {
          throw new IllegalArgumentException(
              "Missing required field: " + fieldName + " of type " + field.getFieldType());
        } else {
          // Skip validation for missing fields when validateOnLoad is false
          continue;
        }
      }

      // Value is guaranteed to be non-null here
      switch (field.getFieldType()) {
        case VECTOR:
          VectorField vectorField = (VectorField) field;
          if (!(value instanceof byte[]
              || value instanceof float[]
              || value instanceof double[]
              || value instanceof List)) {
            throw new IllegalArgumentException(
                String.format(
                    "Schema validation failed for field '%s'. Field expects bytes (vector data), but got %s value '%s'. If this should be a vector field, provide a list of numbers or bytes.",
                    fieldName, value.getClass().getSimpleName(), value));
          }
          // Check dimensions for arrays and lists
          int actualDimensions = 0;
          if (value instanceof float[]) {
            actualDimensions = ((float[]) value).length;
          } else if (value instanceof double[]) {
            actualDimensions = ((double[]) value).length;
          } else if (value instanceof List) {
            actualDimensions = ((List<?>) value).size();
          }

          if (actualDimensions > 0 && actualDimensions != vectorField.getDimensions()) {
            throw new IllegalArgumentException(
                String.format(
                    "Vector field '%s' expects %d dimensions, but got %d",
                    fieldName, vectorField.getDimensions(), actualDimensions));
          }
          break;
        case NUMERIC:
          if (!(value instanceof Number)) {
            throw new IllegalArgumentException(
                String.format(
                    "Schema validation failed for field '%s'. Field expects a number, but got %s value '%s'",
                    fieldName, value.getClass().getSimpleName(), value));
          }
          break;
        case TEXT:
        case TAG:
          if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                String.format(
                    "Schema validation failed for field '%s'. Field expects a string, but got %s value '%s'",
                    fieldName, value.getClass().getSimpleName(), value));
          }
          break;
        case GEO:
          if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                String.format(
                    "Schema validation failed for field '%s'. Field expects a string (lat,lon), but got %s value '%s'",
                    fieldName, value.getClass().getSimpleName(), value));
          }
          break;
      }
    }
  }

  /**
   * Add a document to the index
   *
   * @param docId Document ID
   * @param document Document fields
   * @return Document ID
   */
  public String addDocument(String docId, Map<String, Object> document) {
    // Preprocess document to convert Lists to arrays for vector fields
    Map<String, Object> processedDocument = preprocessDocument(document);
    // Always validate document against schema when adding directly
    validateDocument(processedDocument);
    // Use UnifiedJedis if available for consistency
    if (unifiedClient != null) {
      return addDocumentWithUnified(docId, processedDocument);
    }

    Jedis jedis = getJedis();
    try {
      if (getStorageType() == IndexSchema.StorageType.JSON) {
        // For JSON storage, use RedisJSON commands

        // Use JSON.SET command - Jedis doesn't have jsonSet, need UnifiedJedis
        throw new IllegalStateException(
            "JSON storage requires UnifiedJedis client. Use SearchIndex(schema, unifiedJedis) constructor.");
      } else {
        // For HASH storage - handle vectors specially
        for (Map.Entry<String, Object> entry : processedDocument.entrySet()) {
          String key = entry.getKey();
          Object value = entry.getValue();

          BaseField field = (schema != null) ? schema.getField(key) : null;
          if (field instanceof VectorField && value != null) {
            // Store vectors as binary data
            byte[] vectorBytes = null;
            if (value instanceof byte[]) {
              // Already in byte array format
              vectorBytes = (byte[]) value;
            } else if (value instanceof float[]) {
              vectorBytes = ArrayUtils.floatArrayToBytes((float[]) value);
            } else if (value instanceof double[]) {
              float[] floats = ArrayUtils.doubleArrayToFloats((double[]) value);
              vectorBytes = ArrayUtils.floatArrayToBytes(floats);
            }
            if (vectorBytes != null) {
              jedis.hset(
                  docId.getBytes(StandardCharsets.UTF_8),
                  key.getBytes(StandardCharsets.UTF_8),
                  vectorBytes);
            }
          } else if (value != null) {
            // Store other fields as strings
            jedis.hset(docId, key, value.toString());
          }
        }
      }

      return docId;
    } catch (Exception e) {
      throw new RuntimeException("Failed to add document: " + e.getMessage(), e);
    } finally {
      // Close connection if we don't have a persistent client
      if (client == null && connectionManager != null) {
        jedis.close();
      }
    }
  }

  private String addDocumentWithUnified(String docId, Map<String, Object> document) {
    // Document is already preprocessed and validated
    try {
      if (getStorageType() == IndexSchema.StorageType.JSON) {
        // For JSON storage, vectors are stored as JSON arrays
        // Convert float[] to List<Float> for proper JSON serialization
        Map<String, Object> jsonDocument = new HashMap<>(document);
        for (Map.Entry<String, Object> entry : document.entrySet()) {
          if (entry.getValue() instanceof float[] floatArray) {
            List<Float> floatList = new ArrayList<>();
            for (float f : floatArray) {
              floatList.add(f);
            }
            jsonDocument.put(entry.getKey(), floatList);
          }
        }

        // Use JSON.SET command to store the document as an object
        // Path2.ROOT_PATH is the root JSON path "$"
        String result =
            unifiedClient.jsonSetWithEscape(
                docId, redis.clients.jedis.json.Path2.ROOT_PATH, jsonDocument);
        log.debug("Stored JSON document {}: {}", docId, result);
      } else {
        // For HASH storage - handle vectors specially
        Map<byte[], byte[]> binaryFields = new HashMap<>();
        Map<String, String> stringFields = new HashMap<>();

        for (Map.Entry<String, Object> entry : document.entrySet()) {
          String key = entry.getKey();
          Object value = entry.getValue();

          BaseField field = (schema != null) ? schema.getField(key) : null;
          if (field instanceof VectorField && value != null) {
            // Store vectors as binary data
            byte[] vectorBytes = null;
            if (value instanceof byte[]) {
              // Already in byte array format
              vectorBytes = (byte[]) value;
            } else if (value instanceof float[]) {
              vectorBytes = ArrayUtils.floatArrayToBytes((float[]) value);
            } else if (value instanceof double[]) {
              float[] floats = ArrayUtils.doubleArrayToFloats((double[]) value);
              vectorBytes = ArrayUtils.floatArrayToBytes(floats);
            }
            if (vectorBytes != null) {
              binaryFields.put(key.getBytes(StandardCharsets.UTF_8), vectorBytes);
            }
          } else if (value != null) {
            // Store other fields as strings
            stringFields.put(key, value.toString());
          }
        }

        // Store binary fields
        if (!binaryFields.isEmpty()) {
          unifiedClient.hset(docId.getBytes(StandardCharsets.UTF_8), binaryFields);
        }
        // Store string fields
        if (!stringFields.isEmpty()) {
          unifiedClient.hset(docId, stringFields);
        }
      }

      return docId;
    } catch (Exception e) {
      throw new RuntimeException("Failed to add document: " + e.getMessage(), e);
    }
  }

  /**
   * Update an existing document
   *
   * @param docId Document ID
   * @param document Updated fields
   */
  public void updateDocument(String docId, Map<String, Object> document) {
    // For now, update is the same as add (Redis will overwrite)
    addDocument(docId, document);
  }

  /**
   * Delete a document from the index
   *
   * @param docId Document ID
   * @return true if document was deleted, false if it didn't exist
   */
  public boolean deleteDocument(String docId) {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      return jedis.del(docId) > 0;
    } catch (Exception e) {
      throw new RuntimeException("Failed to delete document: " + e.getMessage(), e);
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Get document count in the index
   *
   * @return Number of documents
   */
  public long getDocumentCount() {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      // Count documents with the prefix
      String prefix = getPrefix();
      if (prefix != null) {
        var keys = jedis.keys(prefix + "*");
        return keys.size();
      }
      return 0;
    } catch (Exception e) {
      throw new RuntimeException("Failed to get document count: " + e.getMessage(), e);
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Get index information using FT.INFO
   *
   * @return Map of index information
   */
  public Map<String, Object> getInfo() {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      String indexName = getName();
      if (indexName == null) {
        return new HashMap<>();
      }
      return jedis.ftInfo(indexName);
    } catch (Exception e) {
      throw new RuntimeException("Failed to get index info: " + e.getMessage(), e);
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Get the index name
   *
   * @return Index name
   */
  public String getName() {
    if (schema == null || schema.getIndex() == null) {
      return null;
    }
    return schema.getIndex().getName();
  }

  /**
   * Get the key prefix for documents
   *
   * @return Key prefix
   */
  public String getPrefix() {
    if (schema == null || schema.getIndex() == null) {
      return null;
    }
    return schema.getIndex().getPrefix();
  }

  /**
   * Get the key separator
   *
   * @return Key separator (default is ":")
   */
  public String getKeySeparator() {
    if (schema == null || schema.getIndex() == null) {
      return ":";
    }
    return schema.getIndex().getKeySeparator();
  }

  /**
   * Get the storage type
   *
   * @return Storage type (HASH or JSON)
   */
  public IndexSchema.StorageType getStorageType() {
    if (schema == null || schema.getIndex() == null) {
      return IndexSchema.StorageType.HASH;
    }
    return schema.getIndex().getStorageType();
  }

  /**
   * Generate a full key from an ID using the index prefix and separator
   *
   * @param id Document ID
   * @return Full key with prefix
   */
  public String key(String id) {
    if (getPrefix() == null || getPrefix().isEmpty()) {
      return id;
    }
    return getPrefix() + getKeySeparator() + id;
  }

  /**
   * Create the index with overwrite option
   *
   * @param overwrite Whether to overwrite an existing index
   */
  public void create(boolean overwrite) {
    create(overwrite, false);
  }

  /**
   * Create the index with overwrite and drop options
   *
   * @param overwrite Whether to overwrite an existing index
   * @param drop Whether to drop existing data when overwriting
   */
  public void create(boolean overwrite, boolean drop) {
    if (overwrite && exists()) {
      delete(drop);
    }

    // Use existing create() method
    create();
  }

  /**
   * Delete the index
   *
   * @param drop Whether to also drop the data
   */
  public void delete(boolean drop) {
    if (!exists()) {
      throw new RedisVLException("Index " + getName() + " does not exist");
    }

    if (drop) {
      dropWithData();
    } else {
      drop();
    }
  }

  /**
   * Clear all documents from the index without dropping the index itself
   *
   * @return Number of keys deleted
   */
  public int clear() {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      if (getPrefix() != null) {
        var keys = jedis.keys(getPrefix() + "*");
        if (!keys.isEmpty()) {
          jedis.del(keys.toArray(new String[0]));
          return keys.size();
        }
      }
      return 0;
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Load data with automatic ULID key generation.
   *
   * @param data List of documents to load
   * @return List of generated keys
   */
  public List<String> load(List<Map<String, Object>> data) {
    return load(data, null, null);
  }

  /**
   * Load data with specified id field.
   *
   * @param data List of documents to load
   * @param idField Field to use as document ID (null for auto-generated ULIDs)
   * @return List of keys
   */
  public List<String> load(List<Map<String, Object>> data, String idField) {
    return load(data, idField, null);
  }

  /**
   * Load data with preprocessing.
   *
   * @param data List of documents to load
   * @param idField Field to use as document ID (null for auto-generated ULIDs)
   * @param preprocess Optional preprocessing function
   * @return List of keys
   */
  public List<String> load(
      List<Map<String, Object>> data,
      String idField,
      Function<Map<String, Object>, Map<String, Object>> preprocess) {
    // Create a combined preprocess function that includes validation if needed
    Function<Map<String, Object>, Map<String, Object>> combinedPreprocess =
        obj -> {
          // Apply user preprocessing first
          Map<String, Object> processed = preprocess != null ? preprocess.apply(obj) : obj;

          if (processed == null) {
            return null; // Will be handled by storage
          }

          // Apply preprocessing for Lists to arrays for vector fields
          processed = preprocessDocument(processed);

          // Apply validation if validateOnLoad is true
          if (validateOnLoad) {
            validateDocument(processed);
          }

          return processed;
        };

    // Use the storage class for batch loading (with validation disabled since we handle it above)
    UnifiedJedis jedis = getUnifiedJedis();
    return storage.write(jedis, data, idField, null, null, null, combinedPreprocess, false);
  }

  /**
   * Fetch a document by ID or key
   *
   * @param idOrKey Document ID or full key
   * @return Document fields as a map, or null if not found
   */
  public Map<String, Object> fetch(String idOrKey) {
    // If input already contains the prefix, use it as-is, otherwise construct the key
    String key;
    if (getPrefix() != null && !getPrefix().isEmpty()) {
      // Normalize prefix to avoid double separator issues (issue #368)
      String normalizedPrefix = getPrefix();
      String separator = getKeySeparator();
      if (separator != null && !separator.isEmpty() && normalizedPrefix.endsWith(separator)) {
        normalizedPrefix =
            normalizedPrefix.substring(0, normalizedPrefix.length() - separator.length());
      }
      String prefixWithSeparator = normalizedPrefix + separator;

      if (idOrKey.startsWith(prefixWithSeparator)) {
        key = idOrKey; // Already a full key
      } else {
        key = key(idOrKey); // Just an ID, construct the key
      }
    } else {
      key = key(idOrKey); // Just an ID, construct the key
    }
    UnifiedJedis jedis = getUnifiedJedis();

    try {
      if (!jedis.exists(key)) {
        return null;
      }

      if (getStorageType() == IndexSchema.StorageType.HASH) {
        // Get all string fields
        Map<String, String> stringData = jedis.hgetAll(key);
        Map<String, Object> result = new HashMap<>(stringData);

        // Now retrieve vector fields as binary data
        if (schema != null) {
          for (BaseField field : schema.getFields()) {
            if (field instanceof VectorField) {
              String fieldName = field.getName();
              // Get the vector field as binary
              byte[] vectorBytes =
                  jedis.hget(
                      key.getBytes(StandardCharsets.UTF_8),
                      fieldName.getBytes(StandardCharsets.UTF_8));
              if (vectorBytes != null) {
                // Store as byte array - caller can convert as needed
                result.put(fieldName, vectorBytes);
              }
            }
          }
        }

        return result;
      } else {
        // JSON storage - use JSON.GET command
        try {
          Object jsonObj = jedis.jsonGet(key);
          if (jsonObj != null) {
            // The jsonGet returns the parsed object directly when using default Path
            if (jsonObj instanceof Map) {
              @SuppressWarnings("unchecked")
              Map<String, Object> result = (Map<String, Object>) jsonObj;
              return result;
            } else if (jsonObj instanceof String) {
              // If it's a string, parse it
              @SuppressWarnings("unchecked")
              Map<String, Object> result = jsonMapper.readValue((String) jsonObj, Map.class);
              return result;
            }
          }
          return Collections.emptyMap();
        } catch (Exception e) {
          log.error("Failed to fetch JSON document: {}", key, e);
          return Collections.emptyMap();
        }
      }
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Drop a single key
   *
   * @param key Key to delete
   * @return Number of keys deleted (0 or 1)
   */
  public int dropKeys(String key) {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      return (int) jedis.del(key);
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Drop multiple keys
   *
   * @param keys List of keys to delete
   * @return Number of keys deleted
   */
  public int dropKeys(List<String> keys) {
    if (keys.isEmpty()) {
      return 0;
    }
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      return (int) jedis.del(keys.toArray(new String[0]));
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Get index information
   *
   * @return Index information as a map
   */
  public Map<String, Object> info() {
    if (!exists()) {
      throw new RedisVLException("Index " + getName() + " does not exist");
    }
    return getInfo();
  }

  /**
   * Search the index using a VectorQuery
   *
   * @param query Vector query to execute
   * @return Search results
   */
  public SearchResult search(VectorQuery query) {
    if (!exists()) {
      throw new RedisVLException("Index " + getName() + " does not exist");
    }

    // Convert VectorQuery to search string
    String queryString = query.toQueryString();
    Map<String, Object> params = query.toParams();

    // Handle sorting or inOrder if specified
    if ((query.getSortBy() != null && !query.getSortBy().isEmpty()) || query.isInOrder()) {
      return searchWithSort(
          queryString, params, query.getSortBy(), query.isSortDescending(), query.isInOrder());
    }

    return search(queryString, params);
  }

  /**
   * Search the index using a query string
   *
   * @param query Query string
   * @return Search results
   */
  public SearchResult search(String query) {
    return search(query, new HashMap<>());
  }

  /**
   * Search the index using a query string with parameters
   *
   * @param query Query string
   * @param params Query parameters
   * @return Search results
   */
  public SearchResult search(String query, Map<String, Object> params) {
    if (!exists()) {
      throw new RedisVLException("Index " + getName() + " does not exist");
    }

    UnifiedJedis jedis = getUnifiedJedis();
    try {
      if (params != null && !params.isEmpty()) {
        // Convert params to FTSearchParams
        redis.clients.jedis.search.FTSearchParams searchParams =
            new redis.clients.jedis.search.FTSearchParams();

        // Set dialect to 2 for KNN queries
        searchParams.dialect(2);

        // Add vector parameters
        addParamsToSearchParams(searchParams, params);

        return jedis.ftSearch(schema.getName(), query, searchParams);
      } else {
        return jedis.ftSearch(schema.getName(), query);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to search index: " + e.getMessage(), e);
    }
  }

  /**
   * Search with sorting and/or inOrder support.
   *
   * @param query The query string
   * @param params Query parameters
   * @param sortBy Field to sort by (can be null)
   * @param descending Whether to sort in descending order
   * @param inOrder Whether to require terms in field to have same order as in query
   * @return Search results
   */
  private SearchResult searchWithSort(
      String query,
      Map<String, Object> params,
      String sortBy,
      boolean descending,
      boolean inOrder) {
    if (!exists()) {
      throw new RedisVLException("Index " + getName() + " does not exist");
    }

    UnifiedJedis jedis = getUnifiedJedis();
    try {
      // Convert params to FTSearchParams
      redis.clients.jedis.search.FTSearchParams searchParams =
          new redis.clients.jedis.search.FTSearchParams();

      // Set dialect to 2 for KNN queries
      searchParams.dialect(2);

      // Add vector parameters if present
      if (params != null && !params.isEmpty()) {
        addParamsToSearchParams(searchParams, params);
      }

      // Add sorting if specified
      if (sortBy != null && !sortBy.isEmpty()) {
        redis.clients.jedis.args.SortingOrder order =
            descending
                ? redis.clients.jedis.args.SortingOrder.DESC
                : redis.clients.jedis.args.SortingOrder.ASC;
        searchParams.sortBy(sortBy, order);
      }

      // Add inOrder if specified
      if (inOrder) {
        searchParams.inOrder();
      }

      return jedis.ftSearch(schema.getName(), query, searchParams);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to search index with sorting/inOrder: " + e.getMessage(), e);
    }
  }

  /**
   * List all search indexes in Redis
   *
   * @return Set of index names
   */
  public Set<String> listIndexes() {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      return jedis.ftList();
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Count documents matching a query
   *
   * @param query The count query
   * @return The number of matching documents
   */
  public long count(CountQuery query) {
    String queryString = query.getFilterString();
    // Use FT.SEARCH with LIMIT 0 0 to just get the count
    FTSearchParams searchParams = FTSearchParams.searchParams().limit(0, 0).noContent();

    UnifiedJedis jedis = getUnifiedJedis();
    SearchResult result = jedis.ftSearch(schema.getName(), queryString, searchParams);
    return result.getTotalResults();
  }

  /**
   * Query the index and return results as a list of maps
   *
   * @param queryString Query string
   * @return List of document maps
   */
  public List<Map<String, Object>> query(String queryString) {
    SearchResult result = search(queryString);
    return processSearchResult(result);
  }

  /**
   * Query the index using various query types and return results as a list of maps
   *
   * @param query Query object (VectorQuery, FilterQuery, TextQuery, etc.)
   * @return List of document maps
   */
  public List<Map<String, Object>> query(Object query) {
    if (query instanceof CountQuery cq) {
      // For CountQuery, return an empty list but log the count
      // This maintains API compatibility while the count() method provides the actual functionality
      long count = count(cq);
      log.debug("CountQuery returned {} results", count);
      return new ArrayList<>();
    } else if (query instanceof VectorQuery vq) {
      // Validate query parameters based on vector field algorithm
      String fieldName = vq.getField();

      // Try to find the field - handle both plain names and JSONPath names
      BaseField field = schema.getField(fieldName);
      String resolvedFieldName = fieldName;

      if (field == null && !fieldName.startsWith("$.")) {
        // Try with JSONPath prefix if plain name didn't work
        field = schema.getField("$." + fieldName);
        if (field != null) {
          resolvedFieldName = "$." + fieldName; // Use the JSONPath version
        }
      } else if (field == null && fieldName.startsWith("$.")) {
        // Try without JSONPath prefix if JSONPath didn't work
        field = schema.getField(fieldName.substring(2));
        if (field != null) {
          resolvedFieldName = fieldName.substring(2); // Use the plain version
        }
      }

      if (field instanceof VectorField vf
          && vf.getAlgorithm() == VectorField.Algorithm.FLAT
          && vq.getEfRuntime() != null) {
        throw new RedisVLException("EF_RUNTIME is only valid for HNSW algorithm, not FLAT");
      }

      // Create a new VectorQuery with the resolved field name if it changed
      VectorQuery finalQuery = vq;
      if (!resolvedFieldName.equals(fieldName)) {
        finalQuery =
            VectorQuery.builder()
                .field(resolvedFieldName)
                .vector(vq.getVector())
                .numResults(vq.getNumResults())
                .distanceMetric(vq.getDistanceMetric())
                .returnDistance(vq.isReturnDistance())
                .returnScore(vq.isReturnScore())
                .preFilter(vq.getPreFilter())
                .hybridField(vq.getHybridField())
                .hybridQuery(vq.getHybridQuery())
                .efRuntime(vq.getEfRuntime())
                .returnFields(vq.getReturnFields())
                .sortBy(vq.getSortBy())
                .sortDescending(vq.isSortDescending())
                .inOrder(vq.isInOrder())
                .build();
      }

      // Execute the search
      SearchResult result = search(finalQuery);
      return processSearchResult(result);
    } else if (query instanceof VectorRangeQuery vrq) {
      // For now, convert to VectorQuery with distance threshold
      VectorQuery vq =
          VectorQuery.builder()
              .vector(vrq.getVector())
              .field(vrq.getField())
              .numResults(vrq.getNumResults())
              .returnDistance(true)
              .sortBy(vrq.getSortBy())
              .sortDescending(vrq.isSortDescending())
              .inOrder(vrq.isInOrder())
              .build();
      SearchResult result = search(vq);
      // Filter results by distance threshold
      List<Map<String, Object>> allResults = processSearchResult(result);
      List<Map<String, Object>> filtered = new ArrayList<>();
      for (Map<String, Object> doc : allResults) {
        // Check both "distance" and "vector_distance" for compatibility
        Object distanceObj = doc.getOrDefault("distance", doc.get("vector_distance"));
        if (distanceObj != null) {
          double distance = Double.parseDouble(distanceObj.toString());
          if (distance <= vrq.getDistanceThreshold()) {
            filtered.add(doc);
          }
        }
      }
      return filtered;
    } else if (query instanceof Filter fq) {
      SearchResult result = search(fq.build());
      return processSearchResult(result);
    } else if (query instanceof TextQuery tq) {
      SearchResult result = search(tq.toString());
      return processSearchResult(result);
    } else if (query instanceof FilterQuery fq) {
      // FilterQuery: metadata-only query without vector search
      // Python: FilterQuery (redisvl/query/query.py:314)
      redis.clients.jedis.search.Query redisQuery = fq.buildRedisQuery();
      UnifiedJedis jedis = getUnifiedJedis();
      SearchResult result = jedis.ftSearch(schema.getName(), redisQuery);
      return processSearchResult(result);
    } else if (query instanceof AggregationQuery aq) {
      // AggregationQuery: HybridQuery and other aggregation-based queries
      // Python: HybridQuery (redisvl/query/aggregate.py:23)
      redis.clients.jedis.search.aggr.AggregationBuilder aggregation = aq.buildRedisAggregation();
      UnifiedJedis jedis = getUnifiedJedis();

      // Add parameters if present (e.g., vector parameter for HybridQuery)
      Map<String, Object> params = aq.getParams();
      if (params != null && !params.isEmpty()) {
        aggregation.params(params);
      }

      redis.clients.jedis.search.aggr.AggregationResult result =
          jedis.ftAggregate(schema.getName(), aggregation);
      return processAggregationResult(result);
    }

    // Default: try to convert to string and search
    SearchResult result = search(query.toString());
    return processSearchResult(result);
  }

  private List<Map<String, Object>> processSearchResult(SearchResult result) {
    List<Map<String, Object>> processed = new ArrayList<>();
    if (result != null && result.getDocuments() != null) {
      for (redis.clients.jedis.search.Document doc : result.getDocuments()) {
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("id", doc.getId());

        // For JSON storage, parse the JSON document if it's returned as a single "$" field
        if (getStorageType() == IndexSchema.StorageType.JSON) {
          Object jsonField = doc.get("$");
          if (jsonField instanceof String) {
            try {
              // Parse the JSON and add fields with JSONPath notation
              @SuppressWarnings("unchecked")
              Map<String, Object> parsedDoc = jsonMapper.readValue((String) jsonField, Map.class);
              for (Map.Entry<String, Object> entry : parsedDoc.entrySet()) {
                docMap.put("$." + entry.getKey(), entry.getValue());
              }
            } catch (Exception e) {
              log.warn("Failed to parse JSON document in search result", e);
              // Fall back to adding raw properties
              for (Map.Entry<String, Object> entry : doc.getProperties()) {
                docMap.put(entry.getKey(), entry.getValue());
              }
            }
          } else {
            // Add all properties as-is
            for (Map.Entry<String, Object> entry : doc.getProperties()) {
              docMap.put(entry.getKey(), entry.getValue());
            }
          }
        } else {
          // For HASH storage, add all properties as-is
          for (Map.Entry<String, Object> entry : doc.getProperties()) {
            docMap.put(entry.getKey(), entry.getValue());
          }
        }

        // Add score if available
        if (doc.getScore() != null) {
          docMap.put("score", doc.getScore());
        }

        // Check for vector_distance field (might be in properties)
        Object vectorDistance = doc.get("vector_distance");
        if (vectorDistance != null) {
          docMap.put("vector_distance", vectorDistance);
        }

        processed.add(docMap);
      }
    }
    return processed;
  }

  /**
   * Process AggregationResult into List of Maps.
   *
   * <p>Converts Redis aggregation results into a list of maps, where each map represents a row from
   * the aggregation result.
   *
   * @param result the AggregationResult from Redis
   * @return list of maps containing aggregation results
   */
  private List<Map<String, Object>> processAggregationResult(
      redis.clients.jedis.search.aggr.AggregationResult result) {
    List<Map<String, Object>> processed = new ArrayList<>();
    if (result != null && result.getResults() != null) {
      for (Map<String, Object> row : result.getResults()) {
        // Each row is already a Map<String, Object>
        // Just add it to the processed list
        processed.add(new HashMap<>(row));
      }
    }
    return processed;
  }

  /**
   * Execute multiple search queries in batch
   *
   * @param queries List of query strings
   * @return List of search results
   */
  public List<SearchResult> batchSearch(List<String> queries) {
    return batchSearch(queries, Integer.MAX_VALUE);
  }

  /**
   * Execute multiple search queries in batch with specified batch size
   *
   * @param queries List of query strings
   * @param batchSize Number of queries to process per batch
   * @return List of search results
   */
  public List<SearchResult> batchSearch(List<String> queries, int batchSize) {
    List<SearchResult> results = new ArrayList<>();

    // Process queries in batches
    for (int i = 0; i < queries.size(); i += batchSize) {
      int endIndex = Math.min(i + batchSize, queries.size());
      List<String> batch = queries.subList(i, endIndex);

      // Execute each query in the batch
      for (String query : batch) {
        results.add(search(query));
      }
    }

    return results;
  }

  /**
   * Execute multiple filter queries in batch
   *
   * @param queries List of filter queries
   * @return List of query results
   */
  public List<List<Map<String, Object>>> batchQuery(List<Filter> queries) {
    return batchQuery(queries, Integer.MAX_VALUE);
  }

  /**
   * Execute multiple filter queries in batch with specified batch size
   *
   * @param queries List of filter queries
   * @param batchSize Number of queries to process per batch
   * @return List of query results
   */
  public List<List<Map<String, Object>>> batchQuery(List<Filter> queries, int batchSize) {
    List<List<Map<String, Object>>> results = new ArrayList<>();

    // Process queries in batches
    for (int i = 0; i < queries.size(); i += batchSize) {
      int endIndex = Math.min(i + batchSize, queries.size());
      List<Filter> batch = queries.subList(i, endIndex);

      // Execute each query in the batch
      for (Filter query : batch) {
        results.add(query(query));
      }
    }

    return results;
  }

  /**
   * Set expiration time for a key
   *
   * @param key Key to expire
   * @param seconds Time to live in seconds
   */
  public void expireKeys(String key, int seconds) {
    UnifiedJedis jedis = getUnifiedJedis();
    try {
      jedis.expire(key, seconds);
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }
  }

  /**
   * Set expiration time for multiple keys
   *
   * @param keys List of keys to expire
   * @param seconds Time to live in seconds
   * @return List of results (1 if successful, 0 if key doesn't exist)
   */
  public List<Long> expireKeys(List<String> keys, int seconds) {
    List<Long> results = new ArrayList<>();
    UnifiedJedis jedis = getUnifiedJedis();

    try {
      for (String key : keys) {
        results.add(jedis.expire(key, seconds));
      }
    } finally {
      // Close connection if we created a new UnifiedJedis
      if (unifiedClient == null) {
        jedis.close();
      }
    }

    return results;
  }

  /**
   * Execute a query and return results in paginated batches.
   *
   * @param query The query to execute (VectorQuery, FilterQuery, or TextQuery)
   * @param batchSize Number of results per batch
   * @return Iterable of result batches
   */
  public Iterable<List<Map<String, Object>>> paginate(Object query, int batchSize) {
    return new Iterable<>() {
      @Override
      @Nonnull
      public Iterator<List<Map<String, Object>>> iterator() {
        return new Iterator<>() {
          private int offset = 0;
          private boolean hasMore = true;
          private SearchResult lastResult = null;

          @Override
          public boolean hasNext() {
            if (!hasMore) {
              return false;
            }

            // Peek ahead to see if there are more results
            if (lastResult == null) {
              try {
                String queryString = buildQueryString(query);
                FTSearchParams searchParams = buildSearchParams(query, offset, batchSize);
                lastResult = executeSearch(queryString, searchParams);
                hasMore = lastResult != null && !lastResult.getDocuments().isEmpty();
              } catch (Exception e) {
                hasMore = false;
              }
            }
            return hasMore;
          }

          @Override
          public List<Map<String, Object>> next() {
            if (!hasNext()) {
              throw new NoSuchElementException();
            }

            SearchResult result = lastResult;
            lastResult = null;
            offset += batchSize;

            List<Map<String, Object>> results = processSearchResult(result);

            // For VectorRangeQuery, filter by distance threshold
            if (query instanceof VectorRangeQuery vrq) {
              List<Map<String, Object>> filtered = new ArrayList<>();
              for (Map<String, Object> doc : results) {
                if (doc.containsKey("vector_distance")) {
                  String distanceStr = doc.get("vector_distance").toString();
                  // Handle NaN values - skip them
                  if ("nan".equalsIgnoreCase(distanceStr)) {
                    continue;
                  }
                  try {
                    double distance = Double.parseDouble(distanceStr);
                    if (distance <= vrq.getDistanceThreshold()) {
                      filtered.add(doc);
                    }
                  } catch (NumberFormatException e) {
                    // Skip invalid distance values
                  }
                }
                // If no distance, don't include it for range queries
                // Range queries are specifically about distance filtering
              }
              return filtered;
            }

            return results;
          }

          private String buildQueryString(Object query) {
            if (query instanceof VectorQuery) {
              return ((VectorQuery) query).toQueryString();
            } else if (query instanceof VectorRangeQuery vrq) {
              // Convert range query to vector query for execution
              VectorQuery vq =
                  VectorQuery.builder()
                      .vector(vrq.getVector())
                      .field(vrq.getField())
                      .numResults(vrq.getNumResults())
                      .returnDistance(true)
                      .build();
              return vq.toQueryString();
            } else if (query instanceof Filter) {
              return ((Filter) query).build();
            } else if (query instanceof FilterQuery fq) {
              // FilterQuery: extract filter expression or use "*"
              return (fq.getFilterExpression() != null) ? fq.getFilterExpression().build() : "*";
            } else if (query instanceof TextQuery) {
              return query.toString();
            } else {
              return query.toString();
            }
          }

          private FTSearchParams buildSearchParams(Object query, int offset, int limit) {
            FTSearchParams params = new FTSearchParams();
            params.limit(offset, limit);
            params.dialect(2);

            if (query instanceof VectorQuery vq) {
              Map<String, Object> queryParams = vq.toParams();
              addParamsToSearchParams(params, queryParams);

              // Add return fields if specified
              if (vq.getReturnFields() != null && !vq.getReturnFields().isEmpty()) {
                params.returnFields(vq.getReturnFields().toArray(new String[0]));
              }
            } else if (query instanceof VectorRangeQuery vrq) {
              // Convert range query to vector query for params
              VectorQuery vq =
                  VectorQuery.builder()
                      .vector(vrq.getVector())
                      .field(vrq.getField())
                      .numResults(vrq.getNumResults())
                      .returnDistance(true)
                      .build();

              Map<String, Object> queryParams = vq.toParams();
              addParamsToSearchParams(params, queryParams);

              // Always return distance for range queries
              params.returnFields("vector_distance");
            }

            return params;
          }

          private SearchResult executeSearch(String queryString, FTSearchParams params) {
            UnifiedJedis jedis = getUnifiedJedis();
            try {
              return jedis.ftSearch(schema.getName(), queryString, params);
            } finally {
              if (unifiedClient == null) {
                jedis.close();
              }
            }
          }
        };
      }
    };
  }
}
