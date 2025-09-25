package com.redis.vl.schema;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.*;
import java.util.function.Consumer;
import lombok.Getter;

/** Represents the schema definition for a Redis search index */
public class IndexSchema {

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

  /** Create IndexSchema from YAML string */
  @SuppressWarnings("unchecked")
  public static IndexSchema fromYaml(String yaml) {
    try {
      Map<String, Object> data = YAML_MAPPER.readValue(yaml, Map.class);
      return fromMap(data);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to parse YAML schema", e);
    }
  }

  /** Create IndexSchema from YAML file */
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

  /** Create IndexSchema from JSON string */
  @SuppressWarnings("unchecked")
  public static IndexSchema fromJson(String json) {
    try {
      Map<String, Object> data = JSON_MAPPER.readValue(json, Map.class);
      return fromMap(data);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to parse JSON schema", e);
    }
  }

  /** Create IndexSchema from dictionary (Map) */
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

        String separator = (String) fieldData.get("separator");
        if (separator != null) tagBuilder.separator(separator);

        Boolean caseSensitive = (Boolean) fieldData.get("caseSensitive");
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
        Map<String, Object> attrs = (Map<String, Object>) fieldData.get("attrs");
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

  /** Create a builder */
  public static Builder builder() {
    return new Builder();
  }

  /** Create with fluent API */
  public static Builder of(String name) {
    return new Builder().name(name);
  }

  /** Get the index name */
  public String getName() {
    return index.getName();
  }

  /** Get the prefix */
  public String getPrefix() {
    return index.getPrefix();
  }

  /** Get the storage type */
  public StorageType getStorageType() {
    return index.getStorageType();
  }

  /** Get a copy of the fields list */
  public List<BaseField> getFields() {
    return new ArrayList<>(fields);
  }

  /** Get a field by name */
  public BaseField getField(String name) {
    return fieldMap.get(name);
  }

  /** Check if a field exists */
  public boolean hasField(String name) {
    return fieldMap.containsKey(name);
  }

  /** Get fields of a specific type */
  @SuppressWarnings("unchecked")
  public <T extends BaseField> List<T> getFieldsByType(Class<T> fieldType) {
    return fields.stream().filter(fieldType::isInstance).map(field -> (T) field).toList();
  }

  /** Remove a field by name */
  public void removeField(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Field name is required");
    }
    BaseField removed = fieldMap.remove(name);
    if (removed != null) {
      fields.removeIf(field -> field.getName().equals(name));
    }
  }

  /** Add a single field */
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

  /** Add multiple fields from a list of maps */
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

  /** Serialize to YAML */
  public String toYaml() {
    try {
      Map<String, Object> data = toMap();
      return YAML_MAPPER.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize schema to YAML", e);
    }
  }

  /** Serialize to JSON */
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
    if (index.getPrefix() != null) {
      indexData.put("prefix", index.getPrefix());
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

  /** Get the index configuration (defensive copy) */
  public Index getIndex() {
    // Return a new Index with the same values to prevent external modification
    Index copy = new Index();
    copy.setName(index.getName());
    copy.setPrefix(index.getPrefix());
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
    HASH("hash"),
    JSON("json");

    private final String value;

    StorageType(String value) {
      this.value = value;
    }

    public static StorageType fromValue(String value) {
      for (StorageType type : values()) {
        if (type.value.equalsIgnoreCase(value)) {
          return type;
        }
      }
      throw new IllegalArgumentException("Unknown storage type: " + value);
    }

    public String getValue() {
      return value;
    }
  }

  /** Inner class to hold index configuration */
  @Getter
  public static class Index {
    private String name;
    private String prefix;
    private String keySeparator = ":";
    private StorageType storageType = StorageType.HASH;

    public Index() {}

    public Index(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getPrefix() {
      return prefix;
    }

    public void setPrefix(String prefix) {
      this.prefix = prefix;
    }

    public String getKeySeparator() {
      return keySeparator;
    }

    public void setKeySeparator(String keySeparator) {
      this.keySeparator = keySeparator;
    }

    public StorageType getStorageType() {
      return storageType;
    }

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
    private String prefix;
    private StorageType storageType;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder prefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public Builder withPrefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public Builder storageType(StorageType storageType) {
      this.storageType = storageType;
      return this;
    }

    public Builder withStorageType(StorageType storageType) {
      this.storageType = storageType;
      return this;
    }

    public Builder field(BaseField field) {
      this.fields.add(field);
      return this;
    }

    public Builder addTextField(String name, Consumer<TextField.TextFieldBuilder> customizer) {
      TextField.TextFieldBuilder builder = TextField.builder().name(name);
      customizer.accept(builder);
      this.fields.add(builder.build());
      return this;
    }

    public Builder addTagField(String name, Consumer<TagField.TagFieldBuilder> customizer) {
      TagField.TagFieldBuilder builder = TagField.builder().name(name);
      customizer.accept(builder);
      this.fields.add(builder.build());
      return this;
    }

    public Builder addNumericField(
        String name, Consumer<NumericField.NumericFieldBuilder> customizer) {
      NumericField.NumericFieldBuilder builder = NumericField.builder().name(name);
      customizer.accept(builder);
      this.fields.add(builder.build());
      return this;
    }

    public Builder addVectorField(
        String name, int dimensions, Consumer<VectorField.VectorFieldBuilder> customizer) {
      VectorField.VectorFieldBuilder builder =
          VectorField.builder().name(name).dimensions(dimensions);
      customizer.accept(builder);
      this.fields.add(builder.build());
      return this;
    }

    public IndexSchema build() {
      return new IndexSchema(name, prefix, storageType, fields);
    }

    public Builder index(Index index) {
      this.name = index.getName();
      this.prefix = index.getPrefix();
      this.storageType = index.getStorageType();
      return this;
    }
  }
}
