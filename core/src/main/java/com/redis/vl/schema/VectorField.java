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

  // SVS-VAMANA algorithm parameters
  /** Maximum edges per node in the VAMANA graph (default: 40) */
  @JsonProperty("graphMaxDegree")
  private final Integer graphMaxDegree;

  /** Build-time candidate window size (default: 250) */
  @JsonProperty("constructionWindowSize")
  private final Integer constructionWindowSize;

  /** Search-time candidate window size (default: 20) - primary tuning parameter */
  @JsonProperty("searchWindowSize")
  private final Integer searchWindowSize;

  /** Range query boundary expansion factor for SVS (default: 0.01) */
  @JsonProperty("svsEpsilon")
  private final Double svsEpsilon;

  /** Vector compression type (optional) */
  @JsonProperty("compression")
  private final CompressionType compression;

  /** Dimensionality reduction for LeanVec compression (must be < dimensions) */
  @JsonProperty("reduce")
  private final Integer reduce;

  /** Minimum vectors before compression training kicks in (default: 10,240) */
  @JsonProperty("trainingThreshold")
  private final Integer trainingThreshold;

  /**
   * Create a VectorField with name and dimensions (defaults to FLAT algorithm, COSINE distance)
   *
   * @param name Field name
   * @param dimensions Number of dimensions in the vector
   */
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
    this.graphMaxDegree = null;
    this.constructionWindowSize = null;
    this.searchWindowSize = null;
    this.svsEpsilon = null;
    this.compression = null;
    this.reduce = null;
    this.trainingThreshold = null;
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
      Double epsilon,
      // SVS-VAMANA parameters
      Integer graphMaxDegree,
      Integer constructionWindowSize,
      Integer searchWindowSize,
      Double svsEpsilon,
      CompressionType compression,
      Integer reduce,
      Integer trainingThreshold) {
    super(name, alias, indexed != null ? indexed : true, sortable != null ? sortable : false);
    if (dimensions <= 0) {
      throw new IllegalArgumentException("Dimensions must be positive");
    }
    this.dimensions = dimensions;
    this.algorithm = algorithm != null ? algorithm : VectorAlgorithm.FLAT;
    this.distanceMetric = distanceMetric != null ? distanceMetric : DistanceMetric.COSINE;
    this.dataType = dataType != null ? dataType : VectorDataType.FLOAT32;

    // FLAT parameters
    this.initialCapacity = initialCapacity;
    this.blockSize = blockSize;

    // HNSW parameters
    this.hnswM = hnswM;
    this.hnswEfConstruction = hnswEfConstruction;
    this.hnswEfRuntime = hnswEfRuntime;
    this.epsilon = epsilon;

    // SVS-VAMANA parameters
    this.graphMaxDegree = graphMaxDegree;
    this.constructionWindowSize = constructionWindowSize;
    this.searchWindowSize = searchWindowSize;
    this.svsEpsilon = svsEpsilon;
    this.compression = compression;
    this.reduce = reduce;
    this.trainingThreshold = trainingThreshold;

    // Validate SVS-specific constraints
    validateSVSConstraints();
  }

  /**
   * Create a VectorField with fluent API
   *
   * @param name Field name
   * @param dimensions Number of dimensions in the vector
   * @return VectorField builder
   */
  public static VectorFieldBuilder of(String name, int dimensions) {
    return new VectorFieldBuilder(name, dimensions);
  }

  /**
   * Create a VectorField builder (Lombok-style)
   *
   * @return VectorField builder
   */
  public static VectorFieldBuilder builder() {
    return new VectorFieldBuilder(null, 0);
  }

  /**
   * Get the algorithm as our enum type
   *
   * @return Algorithm type
   */
  public Algorithm getAlgorithm() {
    if (algorithm == VectorAlgorithm.HNSW) {
      return Algorithm.HNSW;
    } else if (algorithm == VectorAlgorithm.SVS_VAMANA) {
      return Algorithm.SVS_VAMANA;
    }
    return Algorithm.FLAT;
  }

  /**
   * Get dimensions
   *
   * @return Number of dimensions
   */
  public int getDimensions() {
    return dimensions;
  }

  /**
   * Get distance metric
   *
   * @return Distance metric
   */
  public DistanceMetric getDistanceMetric() {
    return distanceMetric;
  }

  @Override
  public FieldType getFieldType() {
    return FieldType.VECTOR;
  }

  /**
   * Validate SVS-VAMANA specific constraints.
   *
   * <p>Validation rules:
   *
   * <ul>
   *   <li>SVS only supports FLOAT16 and FLOAT32 data types
   *   <li>reduce parameter must be less than dimensions
   *   <li>reduce parameter only valid with LeanVec compression
   *   <li>LVQ compression prohibits reduce parameter
   * </ul>
   *
   * @throws IllegalArgumentException if SVS constraints are violated
   */
  private void validateSVSConstraints() {
    // Only validate if using SVS-VAMANA algorithm
    if (this.algorithm != VectorAlgorithm.SVS_VAMANA) {
      return;
    }

    // Datatype validation: SVS only supports FLOAT16 and FLOAT32
    if (dataType != VectorDataType.FLOAT16 && dataType != VectorDataType.FLOAT32) {
      throw new IllegalArgumentException(
          String.format(
              "SVS-VAMANA only supports FLOAT16 and FLOAT32 data types. Got: %s. "
                  + "Unsupported types: BFLOAT16, FLOAT64, INT8, UINT8.",
              dataType.getValue()));
    }

    // Reduce validation
    if (reduce != null) {
      // reduce must be less than dimensions
      if (reduce >= dimensions) {
        throw new IllegalArgumentException(
            String.format("reduce (%d) must be less than dimensions (%d)", reduce, dimensions));
      }

      // reduce requires compression to be set
      if (compression == null) {
        throw new IllegalArgumentException(
            "reduce parameter requires compression to be set. "
                + "Use LeanVec4x8 or LeanVec8x8 compression with reduce.");
      }

      // reduce only valid with LeanVec compression
      if (!compression.isLeanVec()) {
        throw new IllegalArgumentException(
            String.format(
                "reduce parameter is only supported with LeanVec compression types. "
                    + "Got compression=%s. "
                    + "Either use LeanVec4x8/LeanVec8x8 or remove the reduce parameter.",
                compression.getValue()));
      }
    }

    // Warning: LeanVec without reduce is not recommended
    if (compression != null && compression.isLeanVec() && reduce == null) {
      // Note: In Java we can't easily log warnings without a logger dependency
      // Could add org.slf4j.Logger here or just document this in JavaDoc
      System.err.println(
          String.format(
              "WARNING: LeanVec compression selected without 'reduce'. "
                  + "Consider setting reduce=%d for better performance",
              dimensions / 2));
    }

    // Warning: Low graph_max_degree
    if (graphMaxDegree != null && graphMaxDegree < 32) {
      System.err.println(
          String.format(
              "WARNING: graphMaxDegree=%d is low. "
                  + "Consider values between 32-64 for better recall.",
              graphMaxDegree));
    }

    // Warning: High search_window_size
    if (searchWindowSize != null && searchWindowSize > 100) {
      System.err.println(
          String.format(
              "WARNING: searchWindowSize=%d is high. "
                  + "This may impact query latency. Consider values between 20-50.",
              searchWindowSize));
    }
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
    } else if (algorithm == VectorAlgorithm.SVS_VAMANA) {
      // SVS-VAMANA graph parameters
      if (graphMaxDegree != null) {
        attributes.put("GRAPH_MAX_DEGREE", graphMaxDegree);
      }
      if (constructionWindowSize != null) {
        attributes.put("CONSTRUCTION_WINDOW_SIZE", constructionWindowSize);
      }
      if (searchWindowSize != null) {
        attributes.put("SEARCH_WINDOW_SIZE", searchWindowSize);
      }
      if (svsEpsilon != null) {
        attributes.put("EPSILON", svsEpsilon);
      }

      // SVS-VAMANA compression parameters
      if (compression != null) {
        attributes.put("COMPRESSION", compression.getValue());
      }
      if (reduce != null) {
        attributes.put("REDUCE", reduce);
      }
      if (trainingThreshold != null) {
        attributes.put("TRAINING_THRESHOLD", trainingThreshold);
      }
    }

    return jedisField;
  }

  /** Vector indexing algorithms */
  public enum Algorithm {
    /** FLAT algorithm for exact vector search */
    FLAT("FLAT"),
    /** HNSW algorithm for approximate nearest neighbor search */
    HNSW("HNSW"),
    /** SVS-VAMANA algorithm with compression support (Redis ≥ 8.2.0) */
    SVS_VAMANA("SVS-VAMANA");

    private final String value;

    Algorithm(String value) {
      this.value = value;
    }

    /**
     * Get the algorithm value
     *
     * @return Algorithm value
     */
    public String getValue() {
      return value;
    }
  }

  /** Distance metrics for vector similarity */
  public enum DistanceMetric {
    /** L2 (Euclidean) distance */
    L2("L2"),
    /** Inner Product distance */
    IP("IP"),
    /** Cosine distance */
    COSINE("COSINE");

    private final String value;

    DistanceMetric(String value) {
      this.value = value;
    }

    /**
     * Get the distance metric value
     *
     * @return Distance metric value
     */
    public String getValue() {
      return value;
    }
  }

  /** Vector data types */
  public enum VectorDataType {
    /** Brain Float 16-bit (specialized for ML) */
    BFLOAT16("BFLOAT16"),
    /** IEEE 754 half-precision 16-bit float */
    FLOAT16("FLOAT16"),
    /** IEEE 754 single-precision 32-bit float */
    FLOAT32("FLOAT32"),
    /** IEEE 754 double-precision 64-bit float */
    FLOAT64("FLOAT64"),
    /** 8-bit signed integer */
    INT8("INT8"),
    /** 8-bit unsigned integer */
    UINT8("UINT8");

    private final String value;

    VectorDataType(String value) {
      this.value = value;
    }

    /**
     * Get the data type value
     *
     * @return Data type value
     */
    public String getValue() {
      return value;
    }
  }

  /**
   * Vector compression types for SVS-VAMANA algorithm.
   *
   * <p>Compression families:
   *
   * <ul>
   *   <li><b>LVQ</b> (Learned Vector Quantization): Reduces storage without dimensionality
   *       reduction
   *   <li><b>LeanVec</b>: Combines dimensionality reduction with quantization for maximum
   *       compression
   * </ul>
   *
   * <p>Bit depths:
   *
   * <ul>
   *   <li>4-bit: Higher compression, lower accuracy
   *   <li>8-bit: Lower compression, higher accuracy
   *   <li>4x4, 4x8, 8x8: Hybrid approaches with different primary/secondary quantization
   * </ul>
   */
  public enum CompressionType {
    /** Learned Vector Quantization - 4 bits per dimension */
    LVQ4("LVQ4"),
    /** Learned Vector Quantization - 4x4 bits (hybrid) */
    LVQ4x4("LVQ4x4"),
    /** Learned Vector Quantization - 4x8 bits (hybrid) */
    LVQ4x8("LVQ4x8"),
    /** Learned Vector Quantization - 8 bits per dimension */
    LVQ8("LVQ8"),
    /** LeanVec with 4x8 bit quantization (supports dimensionality reduction) */
    LeanVec4x8("LeanVec4x8"),
    /** LeanVec with 8x8 bit quantization (supports dimensionality reduction) */
    LeanVec8x8("LeanVec8x8");

    private final String value;

    CompressionType(String value) {
      this.value = value;
    }

    /**
     * Get the compression type value for Redis
     *
     * @return Compression type value
     */
    public String getValue() {
      return value;
    }

    /**
     * Check if this is a LeanVec compression type. LeanVec types support dimensionality reduction
     * via the 'reduce' parameter.
     *
     * @return true if LeanVec compression type
     */
    public boolean isLeanVec() {
      return this == LeanVec4x8 || this == LeanVec8x8;
    }

    /**
     * Check if this is an LVQ compression type. LVQ types do NOT support dimensionality reduction.
     *
     * @return true if LVQ compression type
     */
    public boolean isLVQ() {
      return this == LVQ4 || this == LVQ4x4 || this == LVQ4x8 || this == LVQ8;
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
    private Integer graphMaxDegree;
    private Integer constructionWindowSize;
    private Integer searchWindowSize;
    private Double svsEpsilon;
    private CompressionType compression;
    private Integer reduce;
    private Integer trainingThreshold;

    private VectorFieldBuilder(String name, int dimensions) {
      this.name = name;
      this.dimensions = dimensions;
    }

    /**
     * Set the field name
     *
     * @param name Field name
     * @return This builder
     */
    public VectorFieldBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Set the field alias
     *
     * @param alias Field alias
     * @return This builder
     */
    public VectorFieldBuilder alias(String alias) {
      this.alias = alias;
      return this;
    }

    /**
     * Set the field alias (alternative method)
     *
     * @param alias Field alias
     * @return This builder
     */
    public VectorFieldBuilder withAlias(String alias) {
      this.alias = alias;
      return this;
    }

    /**
     * Set whether the field is indexed
     *
     * @param indexed True if indexed
     * @return This builder
     */
    public VectorFieldBuilder indexed(boolean indexed) {
      this.indexed = indexed;
      return this;
    }

    /**
     * Set whether the field is sortable
     *
     * @param sortable True if sortable
     * @return This builder
     */
    public VectorFieldBuilder sortable(boolean sortable) {
      this.sortable = sortable;
      return this;
    }

    /**
     * Set the number of dimensions
     *
     * @param dimensions Number of dimensions in the vector
     * @return This builder
     */
    public VectorFieldBuilder dimensions(int dimensions) {
      this.dimensions = dimensions;
      return this;
    }

    /**
     * Set the vector indexing algorithm
     *
     * @param algorithm Vector algorithm (FLAT or HNSW)
     * @return This builder
     */
    public VectorFieldBuilder algorithm(VectorAlgorithm algorithm) {
      this.algorithm = algorithm;
      return this;
    }

    /**
     * Set the vector indexing algorithm (alternative method)
     *
     * @param algorithm Vector algorithm (FLAT or HNSW)
     * @return This builder
     */
    public VectorFieldBuilder withAlgorithm(VectorAlgorithm algorithm) {
      this.algorithm = algorithm;
      return this;
    }

    /**
     * Set the distance metric
     *
     * @param distanceMetric Distance metric (L2, IP, or COSINE)
     * @return This builder
     */
    public VectorFieldBuilder distanceMetric(DistanceMetric distanceMetric) {
      this.distanceMetric = distanceMetric;
      return this;
    }

    /**
     * Set the distance metric (alternative method)
     *
     * @param distanceMetric Distance metric (L2, IP, or COSINE)
     * @return This builder
     */
    public VectorFieldBuilder withDistanceMetric(DistanceMetric distanceMetric) {
      this.distanceMetric = distanceMetric;
      return this;
    }

    /**
     * Set the data type
     *
     * @param dataType Data type (FLOAT32 or FLOAT64)
     * @return This builder
     */
    public VectorFieldBuilder dataType(VectorDataType dataType) {
      this.dataType = dataType;
      return this;
    }

    /**
     * Set the initial capacity for FLAT algorithm
     *
     * @param initialCapacity Initial capacity
     * @return This builder
     */
    public VectorFieldBuilder initialCapacity(int initialCapacity) {
      this.initialCapacity = initialCapacity;
      return this;
    }

    /**
     * Set the block size for FLAT algorithm
     *
     * @param blockSize Block size
     * @return This builder
     */
    public VectorFieldBuilder blockSize(int blockSize) {
      this.blockSize = blockSize;
      return this;
    }

    /**
     * Set the M parameter for HNSW algorithm
     *
     * @param m M parameter
     * @return This builder
     */
    public VectorFieldBuilder hnswM(int m) {
      this.hnswM = m;
      return this;
    }

    /**
     * Set the M parameter for HNSW algorithm (alternative method)
     *
     * @param m M parameter
     * @return This builder
     */
    public VectorFieldBuilder withHnswM(int m) {
      this.hnswM = m;
      return this;
    }

    /**
     * Set the EF_CONSTRUCTION parameter for HNSW algorithm
     *
     * @param efConstruction EF_CONSTRUCTION parameter
     * @return This builder
     */
    public VectorFieldBuilder hnswEfConstruction(int efConstruction) {
      this.hnswEfConstruction = efConstruction;
      return this;
    }

    /**
     * Set the EF_CONSTRUCTION parameter for HNSW algorithm (alternative method)
     *
     * @param efConstruction EF_CONSTRUCTION parameter
     * @return This builder
     */
    public VectorFieldBuilder withHnswEfConstruction(int efConstruction) {
      this.hnswEfConstruction = efConstruction;
      return this;
    }

    /**
     * Set the EF_RUNTIME parameter for HNSW algorithm
     *
     * @param efRuntime EF_RUNTIME parameter
     * @return This builder
     */
    public VectorFieldBuilder hnswEfRuntime(int efRuntime) {
      this.hnswEfRuntime = efRuntime;
      return this;
    }

    /**
     * Set the epsilon parameter for HNSW algorithm
     *
     * @param epsilon Epsilon parameter
     * @return This builder
     */
    public VectorFieldBuilder epsilon(double epsilon) {
      this.epsilon = epsilon;
      return this;
    }

    // ===== SVS-VAMANA Parameters =====

    /**
     * Set the graph max degree for SVS-VAMANA algorithm.
     *
     * <p>Controls the maximum number of edges per node in the VAMANA graph. Higher values improve
     * recall but increase memory usage and build time.
     *
     * @param graphMaxDegree Max edges per node (recommended: 32-64, default: 40)
     * @return This builder
     */
    public VectorFieldBuilder graphMaxDegree(int graphMaxDegree) {
      this.graphMaxDegree = graphMaxDegree;
      return this;
    }

    /**
     * Set the construction window size for SVS-VAMANA algorithm.
     *
     * <p>Number of candidates considered during graph construction. Higher values improve index
     * quality but increase build time.
     *
     * @param constructionWindowSize Build-time candidates (default: 250)
     * @return This builder
     */
    public VectorFieldBuilder constructionWindowSize(int constructionWindowSize) {
      this.constructionWindowSize = constructionWindowSize;
      return this;
    }

    /**
     * Set the search window size for SVS-VAMANA algorithm.
     *
     * <p>Number of candidates considered during search. This is the primary tuning parameter for
     * accuracy vs performance trade-off. Higher values improve recall but increase query latency.
     *
     * @param searchWindowSize Search candidates (recommended: 20-50, default: 20)
     * @return This builder
     */
    public VectorFieldBuilder searchWindowSize(int searchWindowSize) {
      this.searchWindowSize = searchWindowSize;
      return this;
    }

    /**
     * Set the epsilon parameter for SVS-VAMANA range queries.
     *
     * <p>Boundary expansion factor for range queries.
     *
     * @param svsEpsilon Epsilon value (default: 0.01)
     * @return This builder
     */
    public VectorFieldBuilder svsEpsilon(double svsEpsilon) {
      this.svsEpsilon = svsEpsilon;
      return this;
    }

    /**
     * Set the compression type for SVS-VAMANA algorithm.
     *
     * <p>Available compression types:
     *
     * <ul>
     *   <li><b>LVQ4, LVQ4x4, LVQ4x8, LVQ8</b>: Learned Vector Quantization (no dimension reduction)
     *   <li><b>LeanVec4x8, LeanVec8x8</b>: Supports dimension reduction via reduce parameter
     * </ul>
     *
     * @param compression Compression type
     * @return This builder
     */
    public VectorFieldBuilder compression(CompressionType compression) {
      this.compression = compression;
      return this;
    }

    /**
     * Set the dimensionality reduction factor for LeanVec compression.
     *
     * <p><b>Important</b>: Only valid with LeanVec compression types. Must be less than the vector
     * dimensions.
     *
     * <p>Recommended values: dimensions/2 or dimensions/4
     *
     * @param reduce Target dimensions after reduction (must be &lt; dimensions)
     * @return This builder
     * @throws IllegalArgumentException if used without LeanVec compression
     */
    public VectorFieldBuilder reduce(int reduce) {
      this.reduce = reduce;
      return this;
    }

    /**
     * Set the training threshold for SVS-VAMANA compression.
     *
     * <p>Minimum number of vectors required before compression training begins.
     *
     * @param trainingThreshold Minimum vectors (default: 10,240)
     * @return This builder
     */
    public VectorFieldBuilder trainingThreshold(int trainingThreshold) {
      this.trainingThreshold = trainingThreshold;
      return this;
    }

    /**
     * Build the VectorField
     *
     * @return VectorField instance
     */
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
          epsilon,
          // SVS-VAMANA parameters
          graphMaxDegree,
          constructionWindowSize,
          searchWindowSize,
          svsEpsilon,
          compression,
          reduce,
          trainingThreshold);
    }
  }
}
