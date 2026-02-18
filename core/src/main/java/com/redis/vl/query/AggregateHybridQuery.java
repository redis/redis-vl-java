package com.redis.vl.query;

import com.redis.vl.utils.ArrayUtils;
import com.redis.vl.utils.FullTextQueryHelper;
import java.util.*;
import redis.clients.jedis.search.aggr.AggregationBuilder;
import redis.clients.jedis.search.aggr.SortedField;

/**
 * AggregateHybridQuery combines text and vector search in Redis using FT.AGGREGATE.
 *
 * <p>Ported from Python: redisvl/query/aggregate.py:57-329 (AggregateHybridQuery class)
 *
 * <p>It allows you to perform a hybrid search using both text and vector similarity. It scores
 * documents based on a weighted combination of text and vector similarity using the formula:
 *
 * <pre>
 * hybrid_score = (1 - alpha) * text_score + alpha * vector_similarity
 * </pre>
 *
 * <p>Where {@code text_score} is the BM25 score from the text search and {@code vector_similarity}
 * is the normalized cosine similarity from the vector search.
 *
 * <p><strong>Redis Version Requirements:</strong> This query uses the ADDSCORES option in
 * FT.AGGREGATE to expose the internal text search score (@__score). This feature requires
 * <strong>Redis 7.4.0 or later</strong>. On older Redis versions, the query will fail.
 *
 * <p><strong>Note on Runtime Parameters:</strong> AggregateHybridQuery uses Redis FT.AGGREGATE for
 * aggregation-based hybrid search. As of Redis Stack 7.2+, runtime parameters (efRuntime, epsilon,
 * etc.) are NOT supported in FT.AGGREGATE queries. If you need runtime parameter support, use
 * {@link VectorQuery} or {@link VectorRangeQuery} instead.
 *
 * <p>For native FT.HYBRID support with built-in score fusion (RRF, LINEAR), use {@link HybridQuery}
 * instead. This requires Redis 8.4+.
 *
 * <p>Python equivalent:
 *
 * <pre>
 * query = AggregateHybridQuery(
 *     text="example text",
 *     text_field_name="text_field",
 *     vector=[0.1, 0.2, 0.3],
 *     vector_field_name="vector_field",
 *     text_scorer="BM25STD",
 *     filter_expression=None,
 *     alpha=0.7,
 *     dtype="float32",
 *     num_results=10,
 *     return_fields=["field1", "field2"],
 *     stopwords="english",
 *     dialect=2,
 * )
 * results = index.query(query)
 * </pre>
 *
 * <p>Java equivalent:
 *
 * <pre>
 * AggregateHybridQuery query = AggregateHybridQuery.builder()
 *     .text("example text")
 *     .textFieldName("text_field")
 *     .vector(new float[]{0.1f, 0.2f, 0.3f})
 *     .vectorFieldName("vector_field")
 *     .textScorer("BM25STD")
 *     .filterExpression(null)
 *     .alpha(0.7f)
 *     .dtype("float32")
 *     .numResults(10)
 *     .returnFields(List.of("field1", "field2"))
 *     .stopwords(FullTextQueryHelper.loadDefaultStopwords("english"))
 *     .dialect(2)
 *     .build();
 * List&lt;Map&lt;String, Object&gt;&gt; results = index.query(query);
 * </pre>
 *
 * <p>This class is final to prevent finalizer attacks, as it throws exceptions in constructors for
 * input validation (SEI CERT OBJ11-J).
 *
 * @since 0.1.0
 */
public final class AggregateHybridQuery extends AggregationQuery {

  private static final String DISTANCE_ID = "vector_distance";
  private static final String VECTOR_PARAM = "vector";

  private final String text;
  private final String textFieldName;
  private final float[] vector;
  private final String vectorFieldName;
  private final String textScorer;
  private final Object filterExpression;
  private final float alpha;
  private final String dtype;
  private final int numResults;
  private final List<String> returnFields;
  private final Set<String> stopwords;
  private final int dialect;

  private AggregateHybridQuery(AggregateHybridQueryBuilder builder) {
    this.text = builder.text;
    this.textFieldName = builder.textFieldName;
    this.vector = builder.vector != null ? builder.vector.clone() : null;
    this.vectorFieldName = builder.vectorFieldName;
    this.textScorer = builder.textScorer;
    this.filterExpression = builder.filterExpression;
    this.alpha = builder.alpha;
    this.dtype = builder.dtype;
    this.numResults = builder.numResults;
    this.returnFields =
        builder.returnFields != null ? List.copyOf(builder.returnFields) : List.of();
    this.stopwords = builder.stopwords != null ? Set.copyOf(builder.stopwords) : Set.of();
    this.dialect = builder.dialect;

    if (this.text == null || this.text.trim().isEmpty()) {
      throw new IllegalArgumentException("text string cannot be empty");
    }

    String tokenized = FullTextQueryHelper.tokenizeAndEscapeQuery(this.text, this.stopwords);
    if (tokenized.isEmpty()) {
      throw new IllegalArgumentException("text string cannot be empty after removing stopwords");
    }
  }

  /**
   * Create a new builder for AggregateHybridQuery.
   *
   * @return A new AggregateHybridQueryBuilder instance
   */
  public static AggregateHybridQueryBuilder builder() {
    return new AggregateHybridQueryBuilder();
  }

  /**
   * Load default stopwords for a given language.
   *
   * @param language the language (e.g., "english", "german")
   * @return set of stopwords
   */
  public static Set<String> loadDefaultStopwords(String language) {
    return FullTextQueryHelper.loadDefaultStopwords(language);
  }

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

  public String getTextScorer() {
    return textScorer;
  }

  public Object getFilterExpression() {
    return filterExpression;
  }

  public float getAlpha() {
    return alpha;
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

  public int getDialect() {
    return dialect;
  }

  /** Builder for creating AggregateHybridQuery instances with fluent API. */
  public static class AggregateHybridQueryBuilder {
    private String text;
    private String textFieldName;
    private float[] vector;
    private String vectorFieldName;
    private String textScorer = "BM25STD";
    private Object filterExpression;
    private float alpha = 0.7f;
    private String dtype = "float32";
    private int numResults = 10;
    private List<String> returnFields = List.of();
    private Set<String> stopwords = FullTextQueryHelper.loadDefaultStopwords("english");
    private int dialect = 2;

    AggregateHybridQueryBuilder() {}

    public AggregateHybridQueryBuilder text(String text) {
      this.text = text;
      return this;
    }

    public AggregateHybridQueryBuilder textFieldName(String textFieldName) {
      this.textFieldName = textFieldName;
      return this;
    }

    public AggregateHybridQueryBuilder vector(float[] vector) {
      this.vector = vector != null ? vector.clone() : null;
      return this;
    }

    public AggregateHybridQueryBuilder vectorFieldName(String vectorFieldName) {
      this.vectorFieldName = vectorFieldName;
      return this;
    }

    public AggregateHybridQueryBuilder textScorer(String textScorer) {
      this.textScorer = textScorer;
      return this;
    }

    public AggregateHybridQueryBuilder filterExpression(Filter filterExpression) {
      this.filterExpression = filterExpression;
      return this;
    }

    public AggregateHybridQueryBuilder filterExpression(String filterExpression) {
      this.filterExpression = filterExpression;
      return this;
    }

    public AggregateHybridQueryBuilder alpha(float alpha) {
      this.alpha = alpha;
      return this;
    }

    public AggregateHybridQueryBuilder dtype(String dtype) {
      this.dtype = dtype;
      return this;
    }

    public AggregateHybridQueryBuilder numResults(int numResults) {
      this.numResults = numResults;
      return this;
    }

    public AggregateHybridQueryBuilder returnFields(List<String> returnFields) {
      this.returnFields = returnFields != null ? List.copyOf(returnFields) : List.of();
      return this;
    }

    public AggregateHybridQueryBuilder stopwords(Set<String> stopwords) {
      this.stopwords = stopwords != null ? Set.copyOf(stopwords) : Set.of();
      return this;
    }

    public AggregateHybridQueryBuilder dialect(int dialect) {
      this.dialect = dialect;
      return this;
    }

    public AggregateHybridQuery build() {
      return new AggregateHybridQuery(this);
    }
  }

  @Override
  public String buildQueryString() {
    String filterStr = null;
    if (filterExpression instanceof Filter) {
      filterStr = ((Filter) filterExpression).build();
    } else if (filterExpression instanceof String) {
      filterStr = (String) filterExpression;
    }

    String knnQuery =
        String.format(
            "KNN %d @%s $%s AS %s", numResults, vectorFieldName, VECTOR_PARAM, DISTANCE_ID);

    String textQuery =
        String.format(
            "(~@%s:(%s)",
            textFieldName, FullTextQueryHelper.tokenizeAndEscapeQuery(text, stopwords));

    if (filterStr != null && !filterStr.equals("*")) {
      textQuery += " AND " + filterStr;
    }

    return String.format("%s)=>[%s]", textQuery, knnQuery);
  }

  @Override
  public AggregationBuilder buildRedisAggregation() {
    String queryString = buildQueryString();
    AggregationBuilder aggregation = new AggregationBuilder(queryString);

    aggregation.dialect(dialect);
    aggregation.addScores();

    aggregation.apply("(2 - @" + DISTANCE_ID + ")/2", "vector_similarity");
    aggregation.apply("@__score", "text_score");

    String hybridScoreFormula =
        String.format("%f*@text_score + %f*@vector_similarity", (1 - alpha), alpha);
    aggregation.apply(hybridScoreFormula, "hybrid_score");

    aggregation.sortBy(numResults, SortedField.desc("@hybrid_score"));

    if (!returnFields.isEmpty()) {
      aggregation.load(returnFields.toArray(String[]::new));
    }

    return aggregation;
  }

  @Override
  public Map<String, Object> getParams() {
    byte[] vectorBytes = ArrayUtils.floatArrayToBytes(vector);
    Map<String, Object> params = new HashMap<>();
    params.put(VECTOR_PARAM, vectorBytes);
    return params;
  }
}
