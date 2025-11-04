package com.redis.vl.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.*;
import java.util.function.Consumer;
import lombok.Getter;

/**
 * Represents the schema definition for a Redis search index.
 *
 * <p>This class is final to prevent finalizer attacks, as it throws exceptions in constructors for
 * input validation (SEI CERT OBJ11-J).
 */
public final class IndexSchema {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  /** Index configuration */
  private final Index index;

  /** List of fields in the schema */
  @JsonIgnore private final List<BaseField> fields;

  /** Map of field names to fields for quick lookup */
  @JsonIgnore private final Map<String, BaseField> fieldMap;

  /** Private constructor for builder */
  private IndexSchema(String name, String prefix, StorageType storageType, List<BaseField> fields) {
    if (name == null || name.trim().isEmpty()) {
      // Create minimal valid state before throwing exception
      this.index = new Index();
      this.fields = new ArrayList<>();
      this.fieldMap = new HashMap<>();
      throw new IllegalArgumentException("Index name is required");
    }
    this.index = new Index(name.trim());
    if (prefix != null) {
      this.index.setPrefix(prefix);
    }
    if (storageType != null) {
      this.index.setStorageType(storageType);
    }
    this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
    this.fieldMap = new HashMap<>();
    for (BaseField field : this.fields) {
      this.fieldMap.put(field.getName(), field);
    }
  }

  /**
   * Create IndexSchema from YAML string
   *
   * @param yaml the YAML string representation of the schema
   * @return an IndexSchema instance parsed from the YAML
   */
  @SuppressWarnings("unchecked")
  public static IndexSchema fromYaml(String yaml) {
    try {
      Map<String, Object> data = YAML_MAPPER.readValue(yaml, Map.class);
      return fromMap(data);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to parse YAML schema", e);
    }
  }

  /**
   * Create IndexSchema from YAML file
   *
   * @param filepath the path to the YAML file containing the schema
   * @return an IndexSchema instance parsed from the YAML file
   */
  @SuppressWarnings("unchecked")
  public static IndexSchema fromYamlFile(String filepath) {
    try {
      java.io.File file = new java.io.File(filepath);
      Map<String, Object> data = YAML_MAPPER.readValue(file, Map.class);
      return fromMap(data);
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("Failed to read YAML schema from file: " + filepath, e);
    }
  }

  /**
   * Create IndexSchema from JSON string
   *
   * @param json the JSON string representation of the schema
   * @return an IndexSchema instance parsed from the JSON
   */
  @SuppressWarnings("unchecked")
  public static IndexSchema fromJson(String json) {
    try {
      Map<String, Object> data = JSON_MAPPER.readValue(json, Map.class);
      return fromMap(data);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to parse JSON schema", e);
    }
  }

  /**
   * Create IndexSchema from dictionary (Map)
   *
   * @param data the map containing schema data
   * @return an IndexSchema instance created from the map
   */
  public static IndexSchema fromDict(Map<String, Object> data) {
    if (data == null) {
      throw new IllegalArgumentException("Schema data cannot be null");
    }
    return fromMap(data);
  }

  /** Create IndexSchema from a map (internal helper) */
  @SuppressWarnings("unchecked")
  private static IndexSchema fromMap(Map<String, Object> data) {
    if (data == null) {
      throw new IllegalArgumentException("Schema data cannot be null");
    }
    Map<String, Object> indexData = (Map<String, Object>) data.get("index");
    if (indexData == null) {
      throw new IllegalArgumentException("Missing 'index' section in schema");
    }

    String name = (String) indexData.get("name");
    String prefix = (String) indexData.get("prefix");
    String storageTypeStr = (String) indexData.get("storage_type");
    StorageType storageType =
        storageTypeStr != null ? StorageType.fromValue(storageTypeStr) : StorageType.HASH;

    List<BaseField> fields = new ArrayList<>();
    List<Map<String, Object>> fieldsData = (List<Map<String, Object>>) data.get("fields");
    if (fieldsData != null) {
      for (Map<String, Object> fieldData : fieldsData) {
        fields.add(parseField(fieldData, storageType));
      }
    }

    return new IndexSchema(name, prefix, storageType, fields);
  }

  /** Parse a field from map data */
  @SuppressWarnings("unchecked")
  private static BaseField parseField(Map<String, Object> fieldData, StorageType storageType) {
    String name = (String) fieldData.get("name");
    String type = (String) fieldData.get("type");
    String alias = (String) fieldData.get("alias");
    Boolean sortable = (Boolean) fieldData.get("sortable");
    Boolean indexed = (Boolean) fieldData.get("indexed");

    // Read attrs map for additional field attributes
    Map<String, Object> attrs = (Map<String, Object>) fieldData.get("attrs");

    // Check for "as" attribute in attrs map (common in JSON schema definitions)
    if (alias == null && attrs != null) {
      alias = (String) attrs.get("as");
    }

    // For JSON storage, handle JSON paths properly
    String path = (String) fieldData.get("path");
    if (storageType == StorageType.JSON) {
      // If explicit path is provided, use it as the field name
      if (path != null && !path.isEmpty()) {
        name = path;
      } else if (!name.startsWith("$.")) {
        // For JSON storage without explicit path, add $. prefix if not already present
        name = "$." + name;
      }
    }

    FieldType fieldType = FieldType.fromRedisType(type.toLowerCase());

    switch (fieldType) {
      case TEXT:
        TextField.TextFieldBuilder textBuilder = TextField.builder().name(name);
        if (alias != null) textBuilder.alias(alias);
        if (sortable != null) textBuilder.sortable(sortable);
        if (indexed != null) textBuilder.indexed(indexed);

        Double weight = getDouble(fieldData.get("weight"));
        if (weight != null) textBuilder.weight(weight);

        Boolean noStem = (Boolean) fieldData.get("noStem");
        if (noStem != null) textBuilder.noStem(noStem);

        String phonetic = (String) fieldData.get("phonetic");
        if (phonetic != null) textBuilder.phonetic(phonetic);

        return textBuilder.build();

      case TAG:
        TagField.TagFieldBuilder tagBuilder = TagField.builder().name(name);
        if (alias != null) tagBuilder.alias(alias);
        if (sortable != null) tagBuilder.sortable(sortable);
        if (indexed != null) tagBuilder.indexed(indexed);

        // Try to get separator from top-level first, then attrs
        String separator = (String) fieldData.get("separator");
        if (separator == null && attrs != null) {
          separator = (String) attrs.get("separator");
        }
        if (separator != null) tagBuilder.separator(separator);

        // Try to get caseSensitive from top-level first, then attrs
        Boolean caseSensitive = (Boolean) fieldData.get("caseSensitive");
        if (caseSensitive == null && attrs != null) {
          caseSensitive = (Boolean) attrs.get("caseSensitive");
        }
        if (caseSensitive != null) tagBuilder.caseSensitive(caseSensitive);

        return tagBuilder.build();

      case NUMERIC:
        NumericField.NumericFieldBuilder numericBuilder = NumericField.builder().name(name);
        if (alias != null) numericBuilder.alias(alias);
        if (sortable != null) numericBuilder.sortable(sortable);
        if (indexed != null) numericBuilder.indexed(indexed);
        return numericBuilder.build();

      case GEO:
        GeoField.GeoFieldBuilder geoBuilder = GeoField.builder().name(name);
        if (alias != null) geoBuilder.alias(alias);
        if (sortable != null) geoBuilder.sortable(sortable);
        if (indexed != null) geoBuilder.indexed(indexed);
        return geoBuilder.build();

      case VECTOR:
        if (attrs == null) {
          throw new IllegalArgumentException("Vector field requires 'attrs' section");
        }

        Integer dims = getInteger(attrs.get("dims"));
        if (dims == null) {
          throw new IllegalArgumentException("Vector field requires 'dims' attribute");
        }

        VectorField.VectorFieldBuilder vectorBuilder =
            VectorField.builder().name(name).dimensions(dims);
        if (alias != null) vectorBuilder.alias(alias);
        if (sortable != null) vectorBuilder.sortable(sortable);
        if (indexed != null) vectorBuilder.indexed(indexed);

        String algorithm = (String) attrs.get("algorithm");
        if (algorithm != null) {
          vectorBuilder.algorithm(
              "hnsw".equalsIgnoreCase(algorithm)
                  ? redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.HNSW
                  : redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.FLAT);
        }

        String distanceMetric = (String) attrs.get("distance_metric");
        if (distanceMetric != null) {
          vectorBuilder.distanceMetric(
              switch (distanceMetric.toLowerCase()) {
                case "l2" -> VectorField.DistanceMetric.L2;
                case "ip" -> VectorField.DistanceMetric.IP;
                default -> VectorField.DistanceMetric.COSINE;
              });
        }

        return vectorBuilder.build();

      default:
        throw new IllegalArgumentException("Unknown field type: " + type);
    }
  }

  private static Integer getInteger(Object value) {
    if (value == null) return null;
    if (value instanceof Integer) return (Integer) value;
    if (value instanceof Number) return ((Number) value).intValue();
    if (value instanceof String) return Integer.parseInt((String) value);
    return null;
  }

  private static Double getDouble(Object value) {
    if (value == null) return null;
    if (value instanceof Double) return (Double) value;
    if (value instanceof Number) return ((Number) value).doubleValue();
    if (value instanceof String) return Double.parseDouble((String) value);
    return null;
  }

  private static Map<String, Object> getStringObjectMap(BaseField field) {
    Map<String, Object> fieldData = new HashMap<>();
    fieldData.put("name", field.getName());
    fieldData.put("type", field.getFieldType().getRedisType());
    if (field.getAlias() != null) {
      fieldData.put("alias", field.getAlias());
    }
    if (field.isSortable()) {
      fieldData.put("sortable", true);
    }
    if (!field.isIndexed()) {
      fieldData.put("indexed", false);
    }

    // Add type-specific attributes
    if (field instanceof VectorField vectorField) {
      Map<String, Object> attrs = new HashMap<>();
      attrs.put("dims", vectorField.getDimensions());
      attrs.put("algorithm", vectorField.getAlgorithm().name().toLowerCase());
      attrs.put("distance_metric", vectorField.getDistanceMetric().getValue().toLowerCase());
      fieldData.put("attrs", attrs);
    }
    return fieldData;
  }

  /**
   * Create a builder
   *
   * @return a new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Create with fluent API
   *
   * @param name the name of the index
   * @return a new Builder instance with the name set
   */
  public static Builder of(String name) {
    return new Builder().name(name);
  }

  /**
   * Get the index name
   *
   * @return the index name
   */
  public String getName() {
    return index.getName();
  }

  /**
   * Get the prefix
   *
   * @return the key prefix for documents in this index
   */
  public String getPrefix() {
    return index.getPrefix();
  }

  /**
   * Get the storage type
   *
   * @return the storage type (HASH or JSON)
   */
  public StorageType getStorageType() {
    return index.getStorageType();
  }

  /**
   * Get a copy of the fields list
   *
   * @return a copy of the list of fields in this schema
   */
  public List<BaseField> getFields() {
    return new ArrayList<>(fields);
  }

  /**
   * Get a field by name
   *
   * @param name the name of the field to retrieve
   * @return the field with the given name, or null if not found
   */
  public BaseField getField(String name) {
    return fieldMap.get(name);
  }

  /**
   * Check if a field exists
   *
   * @param name the name of the field to check
   * @return true if a field with the given name exists, false otherwise
   */
  public boolean hasField(String name) {
    return fieldMap.containsKey(name);
  }

  /**
   * Get fields of a specific type
   *
   * @param <T> the type of field to retrieve
   * @param fieldType the class of the field type
   * @return a list of fields matching the specified type
   */
  @SuppressWarnings("unchecked")
  public <T extends BaseField> List<T> getFieldsByType(Class<T> fieldType) {
    return fields.stream().filter(fieldType::isInstance).map(field -> (T) field).toList();
  }

  /**
   * Remove a field by name
   *
   * @param name the name of the field to remove
   */
  public void removeField(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Field name is required");
    }
    BaseField removed = fieldMap.remove(name);
    if (removed != null) {
      fields.removeIf(field -> field.getName().equals(name));
    }
  }

  /**
   * Add a single field
   *
   * @param field the field to add
   */
  public void addField(BaseField field) {
    if (field == null) {
      throw new IllegalArgumentException("Field cannot be null");
    }
    if (fieldMap.containsKey(field.getName())) {
      throw new IllegalArgumentException("Field '" + field.getName() + "' already exists");
    }
    fields.add(field);
    fieldMap.put(field.getName(), field);
  }

  /**
   * Add multiple fields from a list of maps
   *
   * @param fieldsData list of maps containing field configuration data
   */
  @SuppressWarnings("unchecked")
  public void addFields(List<Map<String, Object>> fieldsData) {
    if (fieldsData == null) {
      throw new IllegalArgumentException("Fields data cannot be null");
    }
    List<BaseField> newFields = new ArrayList<>();
    // First parse all fields to ensure they're valid
    for (Map<String, Object> fieldData : fieldsData) {
      BaseField field = parseField(fieldData, this.index.getStorageType());
      if (fieldMap.containsKey(field.getName())) {
        throw new IllegalArgumentException("Field '" + field.getName() + "' already exists");
      }
      newFields.add(field);
    }
    // If all fields are valid, add them
    for (BaseField field : newFields) {
      fields.add(field);
      fieldMap.put(field.getName(), field);
    }
  }

  /**
   * Serialize to YAML
   *
   * @return YAML string representation of this schema
   */
  public String toYaml() {
    try {
      Map<String, Object> data = toMap();
      return YAML_MAPPER.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize schema to YAML", e);
    }
  }

  /**
   * Serialize to JSON
   *
   * @return JSON string representation of this schema
   */
  public String toJson() {
    try {
      Map<String, Object> data = toMap();
      return JSON_MAPPER.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize schema to JSON", e);
    }
  }

  /** Convert to map for serialization */
  private Map<String, Object> toMap() {
    Map<String, Object> data = new HashMap<>();
    data.put("version", "0.1.0");

    Map<String, Object> indexData = new HashMap<>();
    indexData.put("name", index.getName());
    if (index.getPrefixRaw() != null) {
      // Serialize raw prefix (String or List<String>)
      indexData.put("prefix", index.getPrefixRaw());
    }
    indexData.put("storage_type", index.getStorageType().getValue());
    data.put("index", indexData);

    List<Map<String, Object>> fieldsData = new ArrayList<>();
    for (BaseField field : fields) {
      Map<String, Object> fieldData = getStringObjectMap(field);

      fieldsData.add(fieldData);
    }
    data.put("fields", fieldsData);

    return data;
  }

  /**
   * Get the index configuration (defensive copy)
   *
   * @return a copy of the Index configuration
   */
  public Index getIndex() {
    // Return a new Index with the same values to prevent external modification
    Index copy = new Index();
    copy.setName(index.getName());
    // Use setPrefixRaw to preserve List<String> prefixes
    copy.setPrefixRaw(index.getPrefixRaw());
    copy.setKeySeparator(index.getKeySeparator());
    copy.setStorageType(index.getStorageType());
    return copy;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IndexSchema that = (IndexSchema) o;
    return Objects.equals(index, that.index) && Objects.equals(fields, that.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(index, fields);
  }

  /** Storage type for documents in Redis */
  public enum StorageType {
    /** Hash storage type */
    HASH("hash"),
    /** JSON storage type */
    JSON("json");

    private final String value;

    /**
     * Creates a StorageType with the given value
     *
     * @param value the string value of the storage type
     */
    StorageType(String value) {
      this.value = value;
    }

    /**
     * Get StorageType from string value
     *
     * @param value the string value to convert
     * @return the corresponding StorageType
     */
    public static StorageType fromValue(String value) {
      for (StorageType type : values()) {
        if (type.value.equalsIgnoreCase(value)) {
          return type;
        }
      }
      throw new IllegalArgumentException("Unknown storage type: " + value);
    }

    /**
     * Get the string value of this storage type
     *
     * @return the string value
     */
    public String getValue() {
      return value;
    }
  }

  /** Inner class to hold index configuration */
  @Getter
  public static class Index {
    private String name;

    /**
     * The prefix(es) used for Redis keys. Can be either a String (single prefix) or List of String
     * (multiple prefixes). Python port: supports Union[str, List[str]] for compatibility.
     */
    private Object prefix;

    private String keySeparator = ":";
    private StorageType storageType = StorageType.HASH;

    /** Creates a new Index with default values */
    public Index() {}

    /**
     * Creates a new Index with the given name
     *
     * @param name the name of the index
     */
    public Index(String name) {
      this.name = name;
    }

    /**
     * Get the index name
     *
     * @return the index name
     */
    public String getName() {
      return name;
    }

    /**
     * Set the index name
     *
     * @param name the index name to set
     */
    public void setName(String name) {
      this.name = name;
    }

    /**
     * Get the key prefix (normalized).
     *
     * <p>If multiple prefixes are configured, returns the first one for Redis key construction.
     * This matches Python behavior: prefix[0] if isinstance(prefix, list) else prefix.
     *
     * @return the normalized key prefix (first prefix if multiple), or null if not set
     */
    public String getPrefix() {
      if (prefix instanceof List) {
        @SuppressWarnings("unchecked")
        List<String> prefixList = (List<String>) prefix;
        return prefixList.isEmpty() ? null : prefixList.get(0);
      }
      return (String) prefix;
    }

    /**
     * Get the raw prefix value (can be String or List).
     *
     * <p>This method returns the prefix exactly as stored, without normalization. Use {@link
     * #getPrefix()} for the normalized prefix used in key construction.
     *
     * @return the raw prefix (String or List of String), or null if not set
     */
    public Object getPrefixRaw() {
      return prefix;
    }

    /**
     * Set the key prefix (single prefix)
     *
     * @param prefix the key prefix to set
     */
    public void setPrefix(String prefix) {
      this.prefix = prefix;
    }

    /**
     * Set multiple key prefixes.
     *
     * <p>Normalizes single-element lists to strings for backward compatibility. Python port:
     * matches behavior in convert_index_info_to_schema.
     *
     * @param prefixes the list of key prefixes to set
     */
    public void setPrefix(List<String> prefixes) {
      if (prefixes == null) {
        this.prefix = null;
      } else if (prefixes.size() == 1) {
        // Normalize single-element lists to string for backward compatibility
        this.prefix = prefixes.get(0);
      } else {
        this.prefix = List.copyOf(prefixes); // Defensive copy
      }
    }

    /**
     * Set prefix without normalization (package-private for Builder use).
     *
     * @param prefix the prefix to set (String or List<String>)
     */
    void setPrefixRaw(Object prefix) {
      this.prefix = prefix;
    }

    /**
     * Get the key separator
     *
     * @return the key separator
     */
    public String getKeySeparator() {
      return keySeparator;
    }

    /**
     * Set the key separator
     *
     * @param keySeparator the key separator to set
     */
    public void setKeySeparator(String keySeparator) {
      this.keySeparator = keySeparator;
    }

    /**
     * Get the storage type
     *
     * @return the storage type
     */
    public StorageType getStorageType() {
      return storageType;
    }

    /**
     * Set the storage type
     *
     * @param storageType the storage type to set
     */
    public void setStorageType(StorageType storageType) {
      this.storageType = storageType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Index index = (Index) o;
      return Objects.equals(name, index.name)
          && Objects.equals(prefix, index.prefix)
          && Objects.equals(keySeparator, index.keySeparator)
          && storageType == index.storageType;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, prefix, keySeparator, storageType);
    }
  }

  /** Builder for IndexSchema */
  public static class Builder {
    private final List<BaseField> fields = new ArrayList<>();
    private String name;
    private Object prefix; // Can be String or List<String>
    private StorageType storageType;

    /** Package-private constructor used by builder() and of() factory methods. */
    Builder() {}

    /**
     * Set the index name
     *
     * @param name the index name
     * @return this builder
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Set the key prefix (single prefix)
     *
     * @param prefix the key prefix
     * @return this builder
     */
    public Builder prefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    /**
     * Set multiple key prefixes.
     *
     * <p>Normalizes single-element lists to strings for backward compatibility. Python port:
     * matches behavior in convert_index_info_to_schema.
     *
     * @param prefixes the list of key prefixes
     * @return this builder
     */
    public Builder prefix(List<String> prefixes) {
      if (prefixes == null) {
        this.prefix = null;
      } else if (prefixes.size() == 1) {
        // Normalize single-element lists to string for backward compatibility
        this.prefix = prefixes.get(0);
      } else {
        this.prefix = List.copyOf(prefixes); // Defensive copy
      }
      return this;
    }

    /**
     * Set the key prefix (alias for prefix)
     *
     * @param prefix the key prefix
     * @return this builder
     */
    public Builder withPrefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    /**
     * Set the storage type
     *
     * @param storageType the storage type
     * @return this builder
     */
    public Builder storageType(StorageType storageType) {
      this.storageType = storageType;
      return this;
    }

    /**
     * Set the storage type (alias for storageType)
     *
     * @param storageType the storage type
     * @return this builder
     */
    public Builder withStorageType(StorageType storageType) {
      this.storageType = storageType;
      return this;
    }

    /**
     * Add a field to the schema
     *
     * @param field the field to add
     * @return this builder
     */
    public Builder field(BaseField field) {
      this.fields.add(field);
      return this;
    }

    /**
     * Add a text field with customization
     *
     * @param name the field name
     * @param customizer consumer to customize the field builder
     * @return this builder
     */
    public Builder addTextField(String name, Consumer<TextField.TextFieldBuilder> customizer) {
      TextField.TextFieldBuilder builder = TextField.builder().name(name);
      customizer.accept(builder);
      this.fields.add(builder.build());
      return this;
    }

    /**
     * Add a tag field with customization
     *
     * @param name the field name
     * @param customizer consumer to customize the field builder
     * @return this builder
     */
    public Builder addTagField(String name, Consumer<TagField.TagFieldBuilder> customizer) {
      TagField.TagFieldBuilder builder = TagField.builder().name(name);
      customizer.accept(builder);
      this.fields.add(builder.build());
      return this;
    }

    /**
     * Add a numeric field with customization
     *
     * @param name the field name
     * @param customizer consumer to customize the field builder
     * @return this builder
     */
    public Builder addNumericField(
        String name, Consumer<NumericField.NumericFieldBuilder> customizer) {
      NumericField.NumericFieldBuilder builder = NumericField.builder().name(name);
      customizer.accept(builder);
      this.fields.add(builder.build());
      return this;
    }

    /**
     * Add a vector field with customization
     *
     * @param name the field name
     * @param dimensions the vector dimensions
     * @param customizer consumer to customize the field builder
     * @return this builder
     */
    public Builder addVectorField(
        String name, int dimensions, Consumer<VectorField.VectorFieldBuilder> customizer) {
      VectorField.VectorFieldBuilder builder =
          VectorField.builder().name(name).dimensions(dimensions);
      customizer.accept(builder);
      this.fields.add(builder.build());
      return this;
    }

    /**
     * Build the IndexSchema
     *
     * @return the constructed IndexSchema
     */
    public IndexSchema build() {
      // Handle prefix (can be String or List<String>)
      String prefixStr = null;
      if (prefix instanceof String) {
        prefixStr = (String) prefix;
      } else if (prefix instanceof List) {
        @SuppressWarnings("unchecked")
        List<String> prefixList = (List<String>) prefix;
        prefixStr = prefixList.isEmpty() ? null : prefixList.get(0);
      }

      IndexSchema schema = new IndexSchema(name, prefixStr, storageType, fields);

      // Set the raw prefix (bypass normalization) for Lists to preserve multi-element lists
      // Access the actual index field directly, not the defensive copy from getIndex()
      if (prefix instanceof List) {
        schema.index.setPrefixRaw(prefix);
      }

      return schema;
    }

    /**
     * Set the builder from an Index configuration
     *
     * @param index the Index configuration to use
     * @return this builder
     */
    public Builder index(Index index) {
      this.name = index.getName();
      this.prefix = index.getPrefixRaw(); // Use raw prefix to preserve list
      this.storageType = index.getStorageType();
      return this;
    }
  }
}
