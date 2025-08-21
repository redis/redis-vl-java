package com.redis.vl.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for IndexSchema */
@DisplayName("IndexSchema Tests")
class IndexSchemaTest {

  private IndexSchema createTestSchema() {
    return IndexSchema.builder()
        .name("test-index")
        .prefix("test:")
        .storageType(IndexSchema.StorageType.HASH)
        .field(TextField.of("title").build())
        .field(NumericField.of("price").build())
        .build();
  }

  @Test
  @DisplayName("Should create IndexSchema with builder")
  void shouldCreateIndexSchemaWithBuilder() {
    // Given
    String indexName = "test-index";
    String prefix = "doc:";

    // When
    IndexSchema schema =
        IndexSchema.builder()
            .name(indexName)
            .prefix(prefix)
            .storageType(IndexSchema.StorageType.JSON)
            .field(TextField.of("title").sortable().build())
            .field(TagField.of("category").build())
            .field(NumericField.of("price").sortable().build())
            .field(VectorField.of("embedding", 768).build())
            .build();

    // Then
    assertThat(schema.getName()).isEqualTo(indexName);
    assertThat(schema.getPrefix()).isEqualTo(prefix);
    assertThat(schema.getStorageType()).isEqualTo(IndexSchema.StorageType.JSON);
    assertThat(schema.getFields()).hasSize(4);
    assertThat(schema.getField("title")).isInstanceOf(TextField.class);
    assertThat(schema.getField("category")).isInstanceOf(TagField.class);
    assertThat(schema.getField("price")).isInstanceOf(NumericField.class);
    assertThat(schema.getField("embedding")).isInstanceOf(VectorField.class);
  }

  @Test
  @DisplayName("Should create IndexSchema from YAML")
  void shouldCreateIndexSchemaFromYaml() {
    // Given
    String yaml =
        """
        version: '0.1.0'
        index:
          name: product-index
          prefix: 'product:'
          storage_type: hash
        fields:
          - name: title
            type: text
            sortable: true
          - name: category
            type: tag
            separator: ','
          - name: price
            type: numeric
            sortable: true
          - name: embedding
            type: vector
            attrs:
              dims: 768
              distance_metric: cosine
              algorithm: flat
        """;

    // When
    IndexSchema schema = IndexSchema.fromYaml(yaml);

    // Then
    assertThat(schema.getName()).isEqualTo("product-index");
    assertThat(schema.getPrefix()).isEqualTo("product:");
    assertThat(schema.getStorageType()).isEqualTo(IndexSchema.StorageType.HASH);
    assertThat(schema.getFields()).hasSize(4);
  }

  @Test
  @DisplayName("Should create IndexSchema from JSON")
  void shouldCreateIndexSchemaFromJson() {
    // Given
    String json =
        """
        {
          "version": "0.1.0",
          "index": {
            "name": "user-index",
            "prefix": "user:",
            "storage_type": "json"
          },
          "fields": [
            {
              "name": "username",
              "type": "tag",
              "sortable": false
            },
            {
              "name": "bio",
              "type": "text",
              "weight": 2.0
            }
          ]
        }
        """;

    // When
    IndexSchema schema = IndexSchema.fromJson(json);

    // Then
    assertThat(schema.getName()).isEqualTo("user-index");
    assertThat(schema.getPrefix()).isEqualTo("user:");
    assertThat(schema.getStorageType()).isEqualTo(IndexSchema.StorageType.JSON);
    assertThat(schema.getFields()).hasSize(2);
  }

  @Test
  @DisplayName("Should validate required fields")
  void shouldValidateRequiredFields() {
    // When/Then - missing name
    assertThatThrownBy(() -> IndexSchema.builder().build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Index name is required");

    // When/Then - empty name
    assertThatThrownBy(() -> IndexSchema.builder().name("").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Index name is required");
  }

  @Test
  @DisplayName("Should use default values")
  void shouldUseDefaultValues() {
    // When
    IndexSchema schema = IndexSchema.builder().name("test-index").build();

    // Then
    assertThat(schema.getName()).isEqualTo("test-index");
    assertThat(schema.getPrefix()).isNull();
    assertThat(schema.getStorageType()).isEqualTo(IndexSchema.StorageType.HASH);
    assertThat(schema.getFields()).isEmpty();
  }

  @Test
  @DisplayName("Should add fields with fluent API")
  void shouldAddFieldsWithFluentApi() {
    // When
    IndexSchema schema =
        IndexSchema.of("test-index")
            .withPrefix("test:")
            .withStorageType(IndexSchema.StorageType.JSON)
            .addTextField("title", field -> field.sortable().weight(2.0))
            .addTagField("tags", field -> field.separator("|"))
            .addNumericField("score", NumericField.NumericFieldBuilder::sortable)
            .addVectorField(
                "embedding", 384, field -> field.withDistanceMetric(VectorField.DistanceMetric.L2))
            .build();

    // Then
    assertThat(schema.getFields()).hasSize(4);
    assertThat(schema.getField("title")).isInstanceOf(TextField.class);
    assertThat(((TextField) schema.getField("title")).getWeight()).isEqualTo(2.0);
    assertThat(((TagField) schema.getField("tags")).getSeparator()).isEqualTo("|");
    assertThat(((VectorField) schema.getField("embedding")).getDistanceMetric())
        .isEqualTo(VectorField.DistanceMetric.L2);
  }

  @Test
  @DisplayName("Should serialize to YAML")
  void shouldSerializeToYaml() {
    // Given
    IndexSchema schema =
        IndexSchema.builder()
            .name("test-index")
            .prefix("test:")
            .storageType(IndexSchema.StorageType.JSON)
            .field(TextField.of("content").build())
            .build();

    // When
    String yaml = schema.toYaml();

    // Then
    assertThat(yaml).contains("name: \"test-index\"");
    assertThat(yaml).contains("prefix: \"test:\"");
    assertThat(yaml).contains("storage_type: \"json\"");
    assertThat(yaml).contains("name: \"content\"");
    assertThat(yaml).contains("type: \"text\"");
  }

  @Test
  @DisplayName("Should serialize to JSON")
  void shouldSerializeToJson() {
    // Given
    IndexSchema schema =
        IndexSchema.builder().name("test-index").field(VectorField.of("vec", 128).build()).build();

    // When
    String json = schema.toJson();

    // Then
    assertThat(json).contains("\"name\":\"test-index\"");
    assertThat(json).contains("\"storage_type\":\"hash\"");
    assertThat(json).contains("\"name\":\"vec\"");
    assertThat(json).contains("\"type\":\"vector\"");
    assertThat(json).contains("\"dims\":128");
  }

  @Test
  @DisplayName("Should get field by name")
  void shouldGetFieldByName() {
    // Given
    TextField titleField = TextField.of("title").build();
    NumericField priceField = NumericField.of("price").build();

    IndexSchema schema =
        IndexSchema.builder().name("test-index").field(titleField).field(priceField).build();

    // When/Then
    assertThat(schema.getField("title")).isSameAs(titleField);
    assertThat(schema.getField("price")).isSameAs(priceField);
    assertThat(schema.getField("nonexistent")).isNull();
  }

  @Test
  @DisplayName("Should check if field exists")
  void shouldCheckIfFieldExists() {
    // Given
    IndexSchema schema =
        IndexSchema.builder().name("test-index").field(TextField.of("title").build()).build();

    // When/Then
    assertThat(schema.hasField("title")).isTrue();
    assertThat(schema.hasField("nonexistent")).isFalse();
  }

  @Test
  @DisplayName("Should get fields by type")
  void shouldGetFieldsByType() {
    // Given
    IndexSchema schema =
        IndexSchema.builder()
            .name("test-index")
            .field(TextField.of("title").build())
            .field(TextField.of("content").build())
            .field(NumericField.of("price").build())
            .field(VectorField.of("embedding", 768).build())
            .build();

    // When
    List<TextField> textFields = schema.getFieldsByType(TextField.class);
    List<NumericField> numericFields = schema.getFieldsByType(NumericField.class);
    List<VectorField> vectorFields = schema.getFieldsByType(VectorField.class);

    // Then
    assertThat(textFields).hasSize(2);
    assertThat(numericFields).hasSize(1);
    assertThat(vectorFields).hasSize(1);
  }

  @Test
  @DisplayName("Should throw exception when fromDict receives null")
  void shouldThrowExceptionWhenFromDictReceivesNull() {
    // When/Then
    assertThatThrownBy(() -> IndexSchema.fromDict(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Schema data cannot be null");
  }

  @Test
  @DisplayName("Should throw exception when fromDict receives map without index section")
  void shouldThrowExceptionWhenFromDictMissingIndexSection() {
    // Given
    var invalidSchema =
        java.util.Map.<String, Object>of(
            "fields", java.util.List.of() // Missing "index" section
            );

    // When/Then
    assertThatThrownBy(() -> IndexSchema.fromDict(invalidSchema))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing 'index' section in schema");
  }

  @Test
  @DisplayName("Should remove field by name")
  void shouldRemoveField() {
    // Given
    IndexSchema schema = createTestSchema();
    assertTrue(schema.hasField("title"));
    assertTrue(schema.hasField("price"));
    int initialFieldCount = schema.getFields().size();

    // When
    schema.removeField("title");

    // Then
    assertFalse(schema.hasField("title"));
    assertTrue(schema.hasField("price"));
    assertEquals(initialFieldCount - 1, schema.getFields().size());
  }

  @Test
  @DisplayName("Should add single field")
  void shouldAddSingleField() {
    // Given
    IndexSchema schema = createTestSchema();
    int initialFieldCount = schema.getFields().size();
    assertFalse(schema.hasField("description"));

    // When
    BaseField newField = new TextField("description");
    schema.addField(newField);

    // Then
    assertTrue(schema.hasField("description"));
    assertEquals(initialFieldCount + 1, schema.getFields().size());
    assertEquals(newField, schema.getField("description"));
  }

  @Test
  @DisplayName("Should add multiple fields from list of maps")
  void shouldAddFieldsFromMaps() {
    // Given
    IndexSchema schema = createTestSchema();
    int initialFieldCount = schema.getFields().size();

    List<Map<String, Object>> newFields =
        List.of(
            Map.of("name", "category", "type", "tag"),
            Map.of(
                "name", "embedding",
                "type", "vector",
                "attrs",
                    Map.of(
                        "dims", 128,
                        "distance_metric", "cosine",
                        "algorithm", "hnsw",
                        "datatype", "float32")));

    // When
    schema.addFields(newFields);

    // Then
    assertTrue(schema.hasField("category"));
    assertTrue(schema.hasField("embedding"));
    assertEquals(initialFieldCount + 2, schema.getFields().size());

    BaseField categoryField = schema.getField("category");
    assertInstanceOf(TagField.class, categoryField);

    BaseField embeddingField = schema.getField("embedding");
    assertInstanceOf(VectorField.class, embeddingField);
    VectorField vectorField = (VectorField) embeddingField;
    assertEquals(128, vectorField.getDimensions());
  }

  @Test
  @DisplayName("Should throw exception when adding duplicate field")
  void shouldThrowExceptionWhenAddingDuplicateField() {
    // Given
    IndexSchema schema = createTestSchema();
    BaseField duplicateField = new TextField("title"); // Already exists

    // When/Then
    assertThatThrownBy(() -> schema.addField(duplicateField))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Field 'title' already exists");
  }

  @Test
  @DisplayName("Should throw exception when adding duplicate field via addFields")
  void shouldThrowExceptionWhenAddingDuplicateFieldViaAddFields() {
    // Given
    IndexSchema schema = createTestSchema();
    List<Map<String, Object>> fields =
        List.of(
            Map.of("name", "title", "type", "text") // Already exists
            );

    // When/Then
    assertThatThrownBy(() -> schema.addFields(fields))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Field 'title' already exists");
  }

  @Test
  @DisplayName("Should handle immutable maps from Map.of()")
  void shouldHandleImmutableMaps() {
    // Given - using Map.of() creates immutable maps
    var schema =
        java.util.Map.of(
            "index",
                java.util.Map.of(
                    "name", "test-index",
                    "prefix", "test:",
                    "storage_type", "hash"),
            "fields",
                java.util.List.of(
                    java.util.Map.of("name", "title", "type", "text"),
                    java.util.Map.of("name", "price", "type", "numeric")));

    // When
    IndexSchema indexSchema = IndexSchema.fromDict(schema);

    // Then
    assertThat(indexSchema).isNotNull();
    assertThat(indexSchema.getName()).isEqualTo("test-index");
    assertThat(indexSchema.getPrefix()).isEqualTo("test:");
    assertThat(indexSchema.getStorageType()).isEqualTo(IndexSchema.StorageType.HASH);
    assertThat(indexSchema.getFields()).hasSize(2);
  }
}
