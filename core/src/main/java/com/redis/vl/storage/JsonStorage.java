package com.redis.vl.storage;

import com.redis.vl.schema.BaseField;
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.VectorField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.json.Path2;

/**
 * Internal subclass of BaseStorage for the Redis JSON data type.
 *
 * <p>Implements json-specific logic for validation and read/write operations in Redis.
 */
public class JsonStorage extends BaseStorage {

  public JsonStorage(IndexSchema indexSchema) {
    super(indexSchema);
  }

  @Override
  protected void set(Pipeline pipeline, String key, Map<String, Object> obj) {
    // For JSON storage, vectors are stored as JSON arrays
    Map<String, Object> jsonDocument = new HashMap<>();

    for (Map.Entry<String, Object> entry : obj.entrySet()) {
      String fieldName = entry.getKey();
      Object value = entry.getValue();

      if (value == null) {
        continue;
      }

      // Check if this is a vector field
      BaseField field = findField(fieldName);
      if (field instanceof VectorField) {
        // Convert to List<Float> for proper JSON serialization
        List<Float> floatList = null;
        if (value instanceof float[] floatArray) {
          floatList = new ArrayList<>();
          for (float f : floatArray) {
            floatList.add(f);
          }
        } else if (value instanceof byte[] bytes) {
          // Convert byte array to float array first
          floatList = bytesToFloatList(bytes);
        } else if (value instanceof double[] doubleArray) {
          floatList = new ArrayList<>();
          for (double d : doubleArray) {
            floatList.add((float) d);
          }
        } else if (value instanceof List) {
          // Already a list, just ensure it's Float
          @SuppressWarnings("unchecked")
          List<Number> numberList = (List<Number>) value;
          floatList = new ArrayList<>();
          for (Number n : numberList) {
            floatList.add(n.floatValue());
          }
        }

        if (floatList != null) {
          jsonDocument.put(fieldName, floatList);
        }
      } else {
        // Store other fields as-is
        jsonDocument.put(fieldName, value);
      }
    }

    // Use JSON.SET command to store the document
    pipeline.jsonSetWithEscape(key, Path2.ROOT_PATH, jsonDocument);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected Response<Map<String, Object>> getResponse(Pipeline pipeline, String key) {
    // For JSON, we get the entire document
    Response<Object> response = pipeline.jsonGet(key);
    // We need to return Response<Map<String, Object>> so cast it
    return (Response<Map<String, Object>>) (Response<?>) response;
  }

  @Override
  protected Map<String, Object> convertBytes(Map<String, Object> map) {
    // Handle Response objects from pipeline
    if (map instanceof Response) {
      @SuppressWarnings("unchecked")
      Response<Object> response = (Response<Object>) map;
      Object jsonObj = response.get();

      if (jsonObj instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) jsonObj;
        return result;
      }
    }

    // Return as-is for regular maps
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

  private List<Float> bytesToFloatList(byte[] bytes) {
    List<Float> floats = new ArrayList<>();
    // Assuming 4 bytes per float, little-endian
    for (int i = 0; i < bytes.length; i += 4) {
      int intBits =
          (bytes[i] & 0xFF)
              | ((bytes[i + 1] & 0xFF) << 8)
              | ((bytes[i + 2] & 0xFF) << 16)
              | ((bytes[i + 3] & 0xFF) << 24);
      floats.add(Float.intBitsToFloat(intBits));
    }
    return floats;
  }
}
