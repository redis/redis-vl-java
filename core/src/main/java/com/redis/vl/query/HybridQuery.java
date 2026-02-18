package com.redis.vl.query;

import com.redis.vl.utils.ArrayUtils;
import com.redis.vl.utils.FullTextQueryHelper;
import java.util.*;
import redis.clients.jedis.search.Combiner;
import redis.clients.jedis.search.Combiners;
import redis.clients.jedis.search.Limit;
import redis.clients.jedis.search.Scorer;
import redis.clients.jedis.search.Scorers;
import redis.clients.jedis.search.hybrid.FTHybridParams;
import redis.clients.jedis.search.hybrid.FTHybridPostProcessingParams;
import redis.clients.jedis.search.hybrid.FTHybridSearchParams;
import redis.clients.jedis.search.hybrid.FTHybridVectorParams;

/**
 * HybridQuery combines text and vector search using the native Redis FT.HYBRID command.
 *
 * <p>Ported from Python: redisvl/query/hybrid.py (HybridQuery class)
 *
 * <p>This query uses the native FT.HYBRID command available in Redis 8.4+ which provides built-in
 * score fusion via RRF (Reciprocal Rank Fusion) or LINEAR combination methods. This is the
 * recommended approach for hybrid search on Redis 8.4+.
 *
 * <p>For older Redis versions (7.4+) that use FT.AGGREGATE with manual score fusion, see {@link
 * AggregateHybridQuery}.
 *
 * <p><strong>Alpha Semantics:</strong> In this class, {@code linearAlpha} represents the text
 * weight in the LINEAR combiner (matching the Python convention). This differs from {@link
 * AggregateHybridQuery} where {@code alpha} represents the vector weight. The different parameter
 * names prevent confusion.
 *
 * <p>Python equivalent:
 *
 * <pre>
 * query = HybridQuery(
 *     text="example text",
 *     text_field_name="text_field",
 *     vector=[0.1, 0.2, 0.3],
 *     vector_field_name="vector_field",
 *     text_scorer="BM25STD",
 *     combination_method="RRF",
 *     num_results=10,
 * )
 * results = index.query(query)
 * </pre>
 *
 * <p>Java equivalent:
 *
 * <pre>
 * HybridQuery query = HybridQuery.builder()
 *     .text("example text")
 *     .textFieldName("text_field")
 *     .vector(new float[]{0.1f, 0.2f, 0.3f})
 *     .vectorFieldName("vector_field")
 *     .textScorer("BM25STD")
 *     .combinationMethod(CombinationMethod.RRF)
 *     .numResults(10)
 *     .build();
 * List&lt;Map&lt;String, Object&gt;&gt; results = index.query(query);
 * </pre>
 *
 * <p>This class is final to prevent finalizer attacks, as it throws exceptions in constructors for
 * input validation (SEI CERT OBJ11-J).
 *
 * @since 0.2.0
 */
public final class HybridQuery {

  /** Vector search method for FT.HYBRID VSIM clause. */
  public enum VectorSearchMethod {
    KNN,
    RANGE
  }

  /** Score combination method for FT.HYBRID COMBINE clause. */
  public enum CombinationMethod {
    RRF,
    LINEAR
  }

  private static final String DEFAULT_VECTOR_PARAM = "vector";

  private final String text;
  private final String textFieldName;
  private final float[] vector;
  private final String vectorFieldName;
  private final String vectorParamName;
  private final String textScorer;
  private final String yieldTextScoreAs;
  private final VectorSearchMethod vectorSearchMethod;
  private final int knnEfRuntime;
  private final Float rangeRadius;
  private final float rangeEpsilon;
  private final String yieldVsimScoreAs;
  private final Object filterExpression;
  private final CombinationMethod combinationMethod;
  private final int rrfWindow;
  private final int rrfConstant;
  private final float linearAlpha;
  private final String yieldCombinedScoreAs;
  private final String dtype;
  private final int numResults;
  private final List<String> returnFields;
  private final Set<String> stopwords;

  private HybridQuery(HybridQueryBuilder builder) {
    this.text = builder.text;
    this.textFieldName = builder.textFieldName;
    this.vector = builder.vector != null ? builder.vector.clone() : null;
    this.vectorFieldName = builder.vectorFieldName;
    this.vectorParamName = builder.vectorParamName;
    this.textScorer = builder.textScorer;
    this.yieldTextScoreAs = builder.yieldTextScoreAs;
    this.vectorSearchMethod = builder.vectorSearchMethod;
    this.knnEfRuntime = builder.knnEfRuntime;
    this.rangeRadius = builder.rangeRadius;
    this.rangeEpsilon = builder.rangeEpsilon;
    this.yieldVsimScoreAs = builder.yieldVsimScoreAs;
    this.filterExpression = builder.filterExpression;
    this.combinationMethod = builder.combinationMethod;
    this.rrfWindow = builder.rrfWindow;
    this.rrfConstant = builder.rrfConstant;
    this.linearAlpha = builder.linearAlpha;
    this.yieldCombinedScoreAs = builder.yieldCombinedScoreAs;
    this.dtype = builder.dtype;
    this.numResults = builder.numResults;
    this.returnFields =
        builder.returnFields != null ? List.copyOf(builder.returnFields) : List.of();
    this.stopwords = builder.stopwords != null ? Set.copyOf(builder.stopwords) : Set.of();

    if (this.text == null || this.text.trim().isEmpty()) {
      throw new IllegalArgumentException("text string cannot be empty");
    }

    String tokenized = FullTextQueryHelper.tokenizeAndEscapeQuery(this.text, this.stopwords);
    if (tokenized.isEmpty()) {
      throw new IllegalArgumentException("text string cannot be empty after removing stopwords");
    }

    if (this.vectorSearchMethod == VectorSearchMethod.RANGE && this.rangeRadius == null) {
      throw new IllegalArgumentException(
          "rangeRadius is required when vectorSearchMethod is RANGE");
    }
  }

  public static HybridQueryBuilder builder() {
    return new HybridQueryBuilder();
  }

  public static Set<String> loadDefaultStopwords(String language) {
    return FullTextQueryHelper.loadDefaultStopwords(language);
  }

  // Getters

  public String getText() {
    return text;
  }

  public String getTextFieldName() {
    return textFieldName;
  }

  public float[] getVector() {
    return vector != null ? vector.clone() : null;
  }

  public String getVectorFieldName() {
    return vectorFieldName;
  }

  public String getVectorParamName() {
    return vectorParamName;
  }

  public String getTextScorer() {
    return textScorer;
  }

  public String getYieldTextScoreAs() {
    return yieldTextScoreAs;
  }

  public VectorSearchMethod getVectorSearchMethod() {
    return vectorSearchMethod;
  }

  public int getKnnEfRuntime() {
    return knnEfRuntime;
  }

  public Float getRangeRadius() {
    return rangeRadius;
  }

  public float getRangeEpsilon() {
    return rangeEpsilon;
  }

  public String getYieldVsimScoreAs() {
    return yieldVsimScoreAs;
  }

  public Object getFilterExpression() {
    return filterExpression;
  }

  public CombinationMethod getCombinationMethod() {
    return combinationMethod;
  }

  public int getRrfWindow() {
    return rrfWindow;
  }

  public int getRrfConstant() {
    return rrfConstant;
  }

  public float getLinearAlpha() {
    return linearAlpha;
  }

  public String getYieldCombinedScoreAs() {
    return yieldCombinedScoreAs;
  }

  public String getDtype() {
    return dtype;
  }

  public int getNumResults() {
    return numResults;
  }

  public List<String> getReturnFields() {
    return Collections.unmodifiableList(returnFields);
  }

  public Set<String> getStopwords() {
    return Collections.unmodifiableSet(stopwords);
  }

  /**
   * Build the query string for the SEARCH clause.
   *
   * <p>Tokenizes the text, removes stopwords, escapes special characters, and adds optional filter
   * expression.
   *
   * @return the query string for FT.HYBRID SEARCH clause
   */
  public String buildQueryString() {
    String tokenized = FullTextQueryHelper.tokenizeAndEscapeQuery(text, stopwords);
    String textQuery = String.format("@%s:(%s)", textFieldName, tokenized);

    String filterStr = resolveFilterString();
    if (filterStr != null && !filterStr.equals("*")) {
      return String.format("(%s %s)", textQuery, filterStr);
    }

    return textQuery;
  }

  /**
   * Build the {@link FTHybridParams} for the native FT.HYBRID command.
   *
   * @return the configured FTHybridParams
   */
  public FTHybridParams buildFTHybridParams() {
    // Build SEARCH clause
    FTHybridSearchParams.Builder searchBuilder =
        FTHybridSearchParams.builder().query(buildQueryString()).scorer(resolveScorer(textScorer));
    if (yieldTextScoreAs != null) {
      searchBuilder.scoreAlias(yieldTextScoreAs);
    }

    // Build VSIM clause
    FTHybridVectorParams.Builder vsimBuilder =
        FTHybridVectorParams.builder()
            .field("@" + vectorFieldName)
            .vector("$" + vectorParamName)
            .method(buildVectorMethod());

    String filterStr = resolveFilterString();
    if (filterStr != null && !filterStr.equals("*")) {
      vsimBuilder.filter(filterStr);
    }
    if (yieldVsimScoreAs != null) {
      vsimBuilder.scoreAlias(yieldVsimScoreAs);
    }

    // Build COMBINE clause
    Combiner combiner = buildCombiner();

    // Build POST-PROCESSING clause
    FTHybridPostProcessingParams.Builder postBuilder = FTHybridPostProcessingParams.builder();
    if (!returnFields.isEmpty()) {
      postBuilder.load(returnFields.toArray(new String[0]));
    }
    postBuilder.limit(Limit.of(0, numResults));

    // Assemble FTHybridParams
    FTHybridParams.Builder paramsBuilder =
        FTHybridParams.builder()
            .search(searchBuilder.build())
            .vectorSearch(vsimBuilder.build())
            .combine(combiner)
            .postProcessing(postBuilder.build())
            .param(vectorParamName, ArrayUtils.floatArrayToBytes(vector));

    return paramsBuilder.build();
  }

  /**
   * Convert this HybridQuery to an AggregateHybridQuery for fallback when FT.HYBRID is not
   * available.
   *
   * <p>Maps the LINEAR alpha semantics: HybridQuery's linearAlpha (text weight) maps to
   * AggregateHybridQuery's alpha as (1 - linearAlpha) since AggregateHybridQuery's alpha is the
   * vector weight.
   *
   * @return an AggregateHybridQuery with equivalent parameters
   */
  public AggregateHybridQuery toAggregateHybridQuery() {
    // Map linearAlpha (text weight in FT.HYBRID) to alpha (vector weight in FT.AGGREGATE)
    float aggregateAlpha =
        (combinationMethod == CombinationMethod.LINEAR) ? (1.0f - linearAlpha) : 0.7f;

    AggregateHybridQuery.AggregateHybridQueryBuilder builder =
        AggregateHybridQuery.builder()
            .text(text)
            .textFieldName(textFieldName)
            .vector(vector)
            .vectorFieldName(vectorFieldName)
            .textScorer(textScorer)
            .alpha(aggregateAlpha)
            .dtype(dtype)
            .numResults(numResults)
            .returnFields(returnFields)
            .stopwords(stopwords);

    if (filterExpression instanceof Filter f) {
      builder.filterExpression(f);
    } else if (filterExpression instanceof String s) {
      builder.filterExpression(s);
    }

    return builder.build();
  }

  /**
   * Get the parameters map for the query (vector parameter).
   *
   * @return parameter map with vector bytes
   */
  public Map<String, Object> getParams() {
    byte[] vectorBytes = ArrayUtils.floatArrayToBytes(vector);
    Map<String, Object> params = new HashMap<>();
    params.put(vectorParamName, vectorBytes);
    return params;
  }

  private String resolveFilterString() {
    if (filterExpression instanceof Filter) {
      return ((Filter) filterExpression).build();
    } else if (filterExpression instanceof String) {
      return (String) filterExpression;
    }
    return null;
  }

  private FTHybridVectorParams.VectorMethod buildVectorMethod() {
    if (vectorSearchMethod == VectorSearchMethod.RANGE) {
      FTHybridVectorParams.Range range =
          FTHybridVectorParams.Range.of(rangeRadius).epsilon(rangeEpsilon);
      return range;
    }
    // Default: KNN
    return FTHybridVectorParams.Knn.of(numResults).efRuntime(knnEfRuntime);
  }

  private Combiner buildCombiner() {
    if (combinationMethod == CombinationMethod.LINEAR) {
      Combiners.Linear linear = Combiners.linear().alpha(linearAlpha).beta(1.0 - linearAlpha);
      if (yieldCombinedScoreAs != null) {
        return linear.as(yieldCombinedScoreAs);
      }
      return linear;
    }
    // Default: RRF
    Combiners.RRF rrf = Combiners.rrf().window(rrfWindow).constant(rrfConstant);
    if (yieldCombinedScoreAs != null) {
      return rrf.as(yieldCombinedScoreAs);
    }
    return rrf;
  }

  private static Scorer resolveScorer(String textScorer) {
    return switch (textScorer.toUpperCase()) {
      case "BM25STD" -> Scorers.bm25std();
      case "BM25STD.NORM" -> Scorers.bm25stdNorm();
      case "TFIDF" -> Scorers.tfidf();
      case "TFIDF.DOCNORM" -> Scorers.tfidfDocnorm();
      case "DISMAX" -> Scorers.dismax();
      case "DOCSCORE" -> Scorers.docscore();
      case "HAMMING" -> Scorers.hamming();
      default -> throw new IllegalArgumentException("Unknown scorer: " + textScorer);
    };
  }

  /** Builder for creating HybridQuery instances with fluent API. */
  public static class HybridQueryBuilder {
    private String text;
    private String textFieldName;
    private float[] vector;
    private String vectorFieldName;
    private String vectorParamName = DEFAULT_VECTOR_PARAM;
    private String textScorer = "BM25STD";
    private String yieldTextScoreAs;
    private VectorSearchMethod vectorSearchMethod = VectorSearchMethod.KNN;
    private int knnEfRuntime = 10;
    private Float rangeRadius;
    private float rangeEpsilon = 0.01f;
    private String yieldVsimScoreAs;
    private Object filterExpression;
    private CombinationMethod combinationMethod = CombinationMethod.RRF;
    private int rrfWindow = 20;
    private int rrfConstant = 60;
    private float linearAlpha = 0.3f;
    private String yieldCombinedScoreAs;
    private String dtype = "float32";
    private int numResults = 10;
    private List<String> returnFields = List.of();
    private Set<String> stopwords = FullTextQueryHelper.loadDefaultStopwords("english");

    HybridQueryBuilder() {}

    public HybridQueryBuilder text(String text) {
      this.text = text;
      return this;
    }

    public HybridQueryBuilder textFieldName(String textFieldName) {
      this.textFieldName = textFieldName;
      return this;
    }

    public HybridQueryBuilder vector(float[] vector) {
      this.vector = vector != null ? vector.clone() : null;
      return this;
    }

    public HybridQueryBuilder vectorFieldName(String vectorFieldName) {
      this.vectorFieldName = vectorFieldName;
      return this;
    }

    public HybridQueryBuilder vectorParamName(String vectorParamName) {
      this.vectorParamName = vectorParamName;
      return this;
    }

    public HybridQueryBuilder textScorer(String textScorer) {
      this.textScorer = textScorer;
      return this;
    }

    public HybridQueryBuilder yieldTextScoreAs(String yieldTextScoreAs) {
      this.yieldTextScoreAs = yieldTextScoreAs;
      return this;
    }

    public HybridQueryBuilder vectorSearchMethod(VectorSearchMethod vectorSearchMethod) {
      this.vectorSearchMethod = vectorSearchMethod;
      return this;
    }

    public HybridQueryBuilder knnEfRuntime(int knnEfRuntime) {
      this.knnEfRuntime = knnEfRuntime;
      return this;
    }

    public HybridQueryBuilder rangeRadius(float rangeRadius) {
      this.rangeRadius = rangeRadius;
      return this;
    }

    public HybridQueryBuilder rangeEpsilon(float rangeEpsilon) {
      this.rangeEpsilon = rangeEpsilon;
      return this;
    }

    public HybridQueryBuilder yieldVsimScoreAs(String yieldVsimScoreAs) {
      this.yieldVsimScoreAs = yieldVsimScoreAs;
      return this;
    }

    public HybridQueryBuilder filterExpression(Filter filterExpression) {
      this.filterExpression = filterExpression;
      return this;
    }

    public HybridQueryBuilder filterExpression(String filterExpression) {
      this.filterExpression = filterExpression;
      return this;
    }

    public HybridQueryBuilder combinationMethod(CombinationMethod combinationMethod) {
      this.combinationMethod = combinationMethod;
      return this;
    }

    public HybridQueryBuilder rrfWindow(int rrfWindow) {
      this.rrfWindow = rrfWindow;
      return this;
    }

    public HybridQueryBuilder rrfConstant(int rrfConstant) {
      this.rrfConstant = rrfConstant;
      return this;
    }

    public HybridQueryBuilder linearAlpha(float linearAlpha) {
      this.linearAlpha = linearAlpha;
      return this;
    }

    public HybridQueryBuilder yieldCombinedScoreAs(String yieldCombinedScoreAs) {
      this.yieldCombinedScoreAs = yieldCombinedScoreAs;
      return this;
    }

    public HybridQueryBuilder dtype(String dtype) {
      this.dtype = dtype;
      return this;
    }

    public HybridQueryBuilder numResults(int numResults) {
      this.numResults = numResults;
      return this;
    }

    public HybridQueryBuilder returnFields(List<String> returnFields) {
      this.returnFields = returnFields != null ? List.copyOf(returnFields) : List.of();
      return this;
    }

    public HybridQueryBuilder stopwords(Set<String> stopwords) {
      this.stopwords = stopwords != null ? Set.copyOf(stopwords) : Set.of();
      return this;
    }

    public HybridQuery build() {
      return new HybridQuery(this);
    }
  }
}
