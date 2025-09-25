package com.redis.vl.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

/**
 * VectorField represents a vector field in Redis for similarity search. Supports both FLAT and HNSW
 * algorithms with various distance metrics.
 */
@Getter
public class VectorField extends BaseField {

  /** Number of dimensions in the vector */
  @JsonProperty("dimensions")
  private final int dimensions;

  /** Vector indexing algorithm (FLAT or HNSW) */
  @JsonProperty("algorithm")
  private final VectorAlgorithm algorithm;

  /** Distance metric for similarity calculation */
  @JsonProperty("distanceMetric")
  private final DistanceMetric distanceMetric;

  /** Data type of vector elements */
  @JsonProperty("dataType")
  private final VectorDataType dataType;

  // FLAT algorithm parameters
  @JsonProperty("initialCapacity")
  private final Integer initialCapacity;

  @JsonProperty("blockSize")
  private final Integer blockSize;

  // HNSW algorithm parameters
  @JsonProperty("hnswM")
  private final Integer hnswM;

  @JsonProperty("hnswEfConstruction")
  private final Integer hnswEfConstruction;

  @JsonProperty("hnswEfRuntime")
  private final Integer hnswEfRuntime;

  @JsonProperty("epsilon")
  private final Double epsilon;

  /** Create a VectorField with name and dimensions (defaults to FLAT algorithm, COSINE distance) */
  public VectorField(String name, int dimensions) {
    super(name);
    if (dimensions <= 0) {
      throw new IllegalArgumentException("Dimensions must be positive");
    }
    this.dimensions = dimensions;
    this.algorithm = VectorAlgorithm.FLAT;
    this.distanceMetric = DistanceMetric.COSINE;
    this.dataType = VectorDataType.FLOAT32;
    this.initialCapacity = null;
    this.blockSize = null;
    this.hnswM = null;
    this.hnswEfConstruction = null;
    this.hnswEfRuntime = null;
    this.epsilon = null;
  }

  /** Create a VectorField with all properties */
  private VectorField(
      String name,
      String alias,
      Boolean indexed,
      Boolean sortable,
      int dimensions,
      VectorAlgorithm algorithm,
      DistanceMetric distanceMetric,
      VectorDataType dataType,
      Integer initialCapacity,
      Integer blockSize,
      Integer hnswM,
      Integer hnswEfConstruction,
      Integer hnswEfRuntime,
      Double epsilon) {
    super(name, alias, indexed != null ? indexed : true, sortable != null ? sortable : false);
    if (dimensions <= 0) {
      throw new IllegalArgumentException("Dimensions must be positive");
    }
    this.dimensions = dimensions;
    this.algorithm = algorithm != null ? algorithm : VectorAlgorithm.FLAT;
    this.distanceMetric = distanceMetric != null ? distanceMetric : DistanceMetric.COSINE;
    this.dataType = dataType != null ? dataType : VectorDataType.FLOAT32;
    this.initialCapacity = initialCapacity;
    this.blockSize = blockSize;
    this.hnswM = hnswM;
    this.hnswEfConstruction = hnswEfConstruction;
    this.hnswEfRuntime = hnswEfRuntime;
    this.epsilon = epsilon;
  }

  /** Create a VectorField with fluent API */
  public static VectorFieldBuilder of(String name, int dimensions) {
    return new VectorFieldBuilder(name, dimensions);
  }

  /** Create a VectorField builder (Lombok-style) */
  public static VectorFieldBuilder builder() {
    return new VectorFieldBuilder(null, 0);
  }

  /** Get the algorithm as our enum type */
  public Algorithm getAlgorithm() {
    if (algorithm == VectorAlgorithm.HNSW) {
      return Algorithm.HNSW;
    }
    return Algorithm.FLAT;
  }

  /** Get dimensions */
  public int getDimensions() {
    return dimensions;
  }

  /** Get distance metric */
  public DistanceMetric getDistanceMetric() {
    return distanceMetric;
  }

  @Override
  public FieldType getFieldType() {
    return FieldType.VECTOR;
  }

  @Override
  public SchemaField toJedisSchemaField() {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("DIM", dimensions);
    attributes.put("DISTANCE_METRIC", distanceMetric.getValue());
    attributes.put("TYPE", dataType.getValue());

    redis.clients.jedis.search.schemafields.VectorField jedisField =
        new redis.clients.jedis.search.schemafields.VectorField(name, algorithm, attributes);

    if (alias != null) {
      jedisField.as(alias);
    }

    // Add algorithm-specific parameters
    if (algorithm == VectorAlgorithm.FLAT) {
      if (initialCapacity != null) {
        attributes.put("INITIAL_CAP", initialCapacity);
      }
      if (blockSize != null) {
        attributes.put("BLOCK_SIZE", blockSize);
      }
    } else if (algorithm == VectorAlgorithm.HNSW) {
      if (hnswM != null) {
        attributes.put("M", hnswM);
      }
      if (hnswEfConstruction != null) {
        attributes.put("EF_CONSTRUCTION", hnswEfConstruction);
      }
      if (hnswEfRuntime != null) {
        attributes.put("EF_RUNTIME", hnswEfRuntime);
      }
      if (epsilon != null) {
        attributes.put("EPSILON", epsilon);
      }
    }

    return jedisField;
  }

  /** Vector indexing algorithms */
  public enum Algorithm {
    FLAT("FLAT"),
    HNSW("HNSW");

    private final String value;

    Algorithm(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  /** Distance metrics for vector similarity */
  public enum DistanceMetric {
    L2("L2"),
    IP("IP"),
    COSINE("COSINE");

    private final String value;

    DistanceMetric(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  /** Vector data types */
  public enum VectorDataType {
    FLOAT32("FLOAT32"),
    FLOAT64("FLOAT64");

    private final String value;

    VectorDataType(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  /** Fluent builder for VectorField */
  public static class VectorFieldBuilder {
    private String name;
    private String alias;
    private Boolean indexed;
    private Boolean sortable;
    private int dimensions;
    private VectorAlgorithm algorithm;
    private DistanceMetric distanceMetric;
    private VectorDataType dataType;
    private Integer initialCapacity;
    private Integer blockSize;
    private Integer hnswM;
    private Integer hnswEfConstruction;
    private Integer hnswEfRuntime;
    private Double epsilon;

    private VectorFieldBuilder(String name, int dimensions) {
      this.name = name;
      this.dimensions = dimensions;
    }

    public VectorFieldBuilder name(String name) {
      this.name = name;
      return this;
    }

    public VectorFieldBuilder alias(String alias) {
      this.alias = alias;
      return this;
    }

    public VectorFieldBuilder withAlias(String alias) {
      this.alias = alias;
      return this;
    }

    public VectorFieldBuilder indexed(boolean indexed) {
      this.indexed = indexed;
      return this;
    }

    public VectorFieldBuilder sortable(boolean sortable) {
      this.sortable = sortable;
      return this;
    }

    public VectorFieldBuilder dimensions(int dimensions) {
      this.dimensions = dimensions;
      return this;
    }

    public VectorFieldBuilder algorithm(VectorAlgorithm algorithm) {
      this.algorithm = algorithm;
      return this;
    }

    public VectorFieldBuilder withAlgorithm(VectorAlgorithm algorithm) {
      this.algorithm = algorithm;
      return this;
    }

    public VectorFieldBuilder distanceMetric(DistanceMetric distanceMetric) {
      this.distanceMetric = distanceMetric;
      return this;
    }

    public VectorFieldBuilder withDistanceMetric(DistanceMetric distanceMetric) {
      this.distanceMetric = distanceMetric;
      return this;
    }

    public VectorFieldBuilder dataType(VectorDataType dataType) {
      this.dataType = dataType;
      return this;
    }

    // FLAT algorithm parameters
    public VectorFieldBuilder initialCapacity(int initialCapacity) {
      this.initialCapacity = initialCapacity;
      return this;
    }

    public VectorFieldBuilder blockSize(int blockSize) {
      this.blockSize = blockSize;
      return this;
    }

    // HNSW algorithm parameters
    public VectorFieldBuilder hnswM(int m) {
      this.hnswM = m;
      return this;
    }

    public VectorFieldBuilder withHnswM(int m) {
      this.hnswM = m;
      return this;
    }

    public VectorFieldBuilder hnswEfConstruction(int efConstruction) {
      this.hnswEfConstruction = efConstruction;
      return this;
    }

    public VectorFieldBuilder withHnswEfConstruction(int efConstruction) {
      this.hnswEfConstruction = efConstruction;
      return this;
    }

    public VectorFieldBuilder hnswEfRuntime(int efRuntime) {
      this.hnswEfRuntime = efRuntime;
      return this;
    }

    public VectorFieldBuilder epsilon(double epsilon) {
      this.epsilon = epsilon;
      return this;
    }

    public VectorField build() {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Field name cannot be null or empty");
      }
      if (dimensions <= 0) {
        throw new IllegalArgumentException("Dimensions must be positive");
      }
      return new VectorField(
          name,
          alias,
          indexed,
          sortable,
          dimensions,
          algorithm,
          distanceMetric,
          dataType,
          initialCapacity,
          blockSize,
          hnswM,
          hnswEfConstruction,
          hnswEfRuntime,
          epsilon);
    }
  }
}
