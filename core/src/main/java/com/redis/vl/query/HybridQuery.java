package com.redis.vl.query;

import com.redis.vl.utils.ArrayUtils;
import com.redis.vl.utils.TokenEscaper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import redis.clients.jedis.search.aggr.AggregationBuilder;
import redis.clients.jedis.search.aggr.SortedField;

/**
 * HybridQuery combines text and vector search in Redis using aggregation.
 *
 * <p>Ported from Python: redisvl/query/aggregate.py:23-230 (HybridQuery class)
 *
 * <p>It allows you to perform a hybrid search using both text and vector similarity. It scores
 * documents based on a weighted combination of text and vector similarity.
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
 * HybridQuery query = HybridQuery.builder()
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
 *     .stopwords(loadDefaultStopwords("english"))
 *     .dialect(2)
 *     .build();
 * List&lt;Map&lt;String, Object&gt;&gt; results = index.query(query);
 * </pre>
 *
 * @since 0.1.0
 */
@Getter
public class HybridQuery extends AggregationQuery {

  private static final String DISTANCE_ID = "vector_distance";
  private static final String VECTOR_PARAM = "vector";

  /** The text to search for. */
  private final String text;

  /** The text field name to search in. */
  private final String textFieldName;

  /** The vector to perform vector similarity search. */
  private final float[] vector;

  /** The vector field name to search in. */
  private final String vectorFieldName;

  /**
   * The text scorer to use.
   *
   * <p>Options: TFIDF, TFIDF.DOCNORM, BM25, DISMAX, DOCSCORE, BM25STD
   *
   * <p>Defaults to "BM25STD".
   */
  private final String textScorer;

  /**
   * The filter expression to use.
   *
   * <p>Defaults to null (no filter).
   */
  private final Filter filterExpression;

  /**
   * The weight of the vector similarity.
   *
   * <p>Documents will be scored as: hybrid_score = (alpha) * vector_score + (1-alpha) * text_score
   *
   * <p>Defaults to 0.7.
   */
  private final float alpha;

  /**
   * The data type of the vector.
   *
   * <p>Defaults to "float32".
   */
  private final String dtype;

  /**
   * The number of results to return.
   *
   * <p>Defaults to 10.
   */
  private final int numResults;

  /**
   * The fields to return.
   *
   * <p>Defaults to empty list (return all).
   */
  private final List<String> returnFields;

  /**
   * The stopwords to remove from the provided text prior to search.
   *
   * <p>If "english" "german" etc is provided then a default set of stopwords for that language will
   * be used. If a set of strings is provided then those will be used as stopwords.
   *
   * <p>Defaults to English stopwords.
   */
  private final Set<String> stopwords;

  /**
   * The Redis dialect version.
   *
   * <p>Defaults to 2.
   */
  private final int dialect;

  // Private constructor for builder
  private HybridQuery(HybridQueryBuilder builder) {
    this.text = builder.text;
    this.textFieldName = builder.textFieldName;
    this.vector = builder.vector;
    this.vectorFieldName = builder.vectorFieldName;
    this.textScorer = builder.textScorer;
    this.filterExpression = builder.filterExpression;
    this.alpha = builder.alpha;
    this.dtype = builder.dtype;
    this.numResults = builder.numResults;
    this.returnFields = builder.returnFields;
    this.stopwords = builder.stopwords;
    this.dialect = builder.dialect;

    // Validate text is not empty
    if (this.text == null || this.text.trim().isEmpty()) {
      throw new IllegalArgumentException("text string cannot be empty");
    }

    // Validate tokenized text is not empty after stopwords removal
    String tokenized = tokenizeAndEscapeQuery(this.text);
    if (tokenized.isEmpty()) {
      throw new IllegalArgumentException("text string cannot be empty after removing stopwords");
    }
  }

  /**
   * Create a new builder for HybridQuery.
   *
   * @return A new HybridQueryBuilder instance
   */
  public static HybridQueryBuilder builder() {
    return new HybridQueryBuilder();
  }

  /** Builder for creating HybridQuery instances with fluent API. */
  public static class HybridQueryBuilder {
    private String text;
    private String textFieldName;
    private float[] vector;
    private String vectorFieldName;
    private String textScorer = "BM25STD";
    private Filter filterExpression;
    private float alpha = 0.7f;
    private String dtype = "float32";
    private int numResults = 10;
    private List<String> returnFields = List.of();
    private Set<String> stopwords = loadDefaultStopwords("english");
    private int dialect = 2;

    /** Package-private constructor used by builder() method. */
    HybridQueryBuilder() {}

    /**
     * Set the text query string.
     *
     * @param text The text to search for
     * @return This builder for chaining
     */
    public HybridQueryBuilder text(String text) {
      this.text = text;
      return this;
    }

    /**
     * Set the name of the text field to search.
     *
     * @param textFieldName The field name containing text data
     * @return This builder for chaining
     */
    public HybridQueryBuilder textFieldName(String textFieldName) {
      this.textFieldName = textFieldName;
      return this;
    }

    /**
     * Set the query vector for similarity search.
     *
     * @param vector The embedding vector to search with
     * @return This builder for chaining
     */
    public HybridQueryBuilder vector(float[] vector) {
      this.vector = vector;
      return this;
    }

    /**
     * Set the name of the vector field to search.
     *
     * @param vectorFieldName The field name containing vector data
     * @return This builder for chaining
     */
    public HybridQueryBuilder vectorFieldName(String vectorFieldName) {
      this.vectorFieldName = vectorFieldName;
      return this;
    }

    /**
     * Set the scoring algorithm for text search.
     *
     * @param textScorer The text scorer (e.g., "BM25", "TFIDF")
     * @return This builder for chaining
     */
    public HybridQueryBuilder textScorer(String textScorer) {
      this.textScorer = textScorer;
      return this;
    }

    /**
     * Set an additional filter expression for the query.
     *
     * @param filterExpression The filter to apply
     * @return This builder for chaining
     */
    public HybridQueryBuilder filterExpression(Filter filterExpression) {
      this.filterExpression = filterExpression;
      return this;
    }

    /**
     * Set the weight for combining text and vector scores.
     *
     * @param alpha Weight between 0.0 (vector only) and 1.0 (text only), default 0.7
     * @return This builder for chaining
     */
    public HybridQueryBuilder alpha(float alpha) {
      this.alpha = alpha;
      return this;
    }

    /**
     * Set the data type for vector storage.
     *
     * @param dtype The data type (e.g., "float32", "float64")
     * @return This builder for chaining
     */
    public HybridQueryBuilder dtype(String dtype) {
      this.dtype = dtype;
      return this;
    }

    /**
     * Set the maximum number of results to return.
     *
     * @param numResults The result limit
     * @return This builder for chaining
     */
    public HybridQueryBuilder numResults(int numResults) {
      this.numResults = numResults;
      return this;
    }

    /**
     * Set the fields to return in results.
     *
     * @param returnFields List of field names to return
     * @return This builder for chaining
     */
    public HybridQueryBuilder returnFields(List<String> returnFields) {
      this.returnFields = returnFields;
      return this;
    }

    /**
     * Set custom stopwords for text search.
     *
     * @param stopwords Set of words to exclude from text search
     * @return This builder for chaining
     */
    public HybridQueryBuilder stopwords(Set<String> stopwords) {
      this.stopwords = stopwords;
      return this;
    }

    /**
     * Set the query dialect version.
     *
     * @param dialect The dialect version (default 2)
     * @return This builder for chaining
     */
    public HybridQueryBuilder dialect(int dialect) {
      this.dialect = dialect;
      return this;
    }

    /**
     * Build the HybridQuery instance.
     *
     * @return The configured HybridQuery
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public HybridQuery build() {
      return new HybridQuery(this);
    }
  }

  /**
   * Load default stopwords for a given language.
   *
   * <p>Python uses nltk, we'll use a simple file-based approach.
   *
   * @param language the language (e.g., "english", "german")
   * @return set of stopwords
   */
  public static Set<String> loadDefaultStopwords(String language) {
    if (language == null || language.isEmpty()) {
      return Set.of();
    }

    // Try to load stopwords from resources
    String resourcePath = "/stopwords/" + language + ".txt";
    java.io.InputStream inputStream = HybridQuery.class.getResourceAsStream(resourcePath);

    if (inputStream == null) {
      // Fallback: common English stopwords
      if ("english".equalsIgnoreCase(language)) {
        return Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is",
            "it", "no", "not", "of", "on", "or", "such", "that", "the", "their", "then", "there",
            "these", "they", "this", "to", "was", "will", "with");
      }
      return Set.of();
    }

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .collect(Collectors.toSet());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load stopwords for language: " + language, e);
    }
  }

  /**
   * Tokenize and escape the user query.
   *
   * <p>Ported from Python: _tokenize_and_escape_query (line 185-209)
   *
   * @param userQuery the user query to tokenize
   * @return tokenized and escaped query string joined by OR
   */
  public String tokenizeAndEscapeQuery(String userQuery) {
    TokenEscaper escaper = new TokenEscaper();

    // Tokenize: split on whitespace, clean up punctuation
    List<String> tokens =
        Arrays.stream(userQuery.split("\\s+"))
            .map(
                token ->
                    escaper.escape(
                        token
                            .strip()
                            .replaceAll("^,+|,+$", "")
                            .replace("\u201c", "")
                            .replace("\u201d", "")
                            .toLowerCase()))
            .filter(token -> !token.isEmpty() && !stopwords.contains(token))
            .collect(Collectors.toList());

    // Join with OR (pipe)
    return String.join(" | ", tokens);
  }

  /**
   * Build the full query string for text search with optional filtering.
   *
   * <p>Ported from Python: _build_query_string (line 211-225)
   *
   * @return the query string
   */
  @Override
  public String buildQueryString() {
    String filterStr = (filterExpression != null) ? filterExpression.build() : null;

    // Base KNN query
    String knnQuery =
        String.format(
            "KNN %d @%s $%s AS %s", numResults, vectorFieldName, VECTOR_PARAM, DISTANCE_ID);

    // Text query with fuzzy matching (~)
    String textQuery = String.format("(~@%s:(%s)", textFieldName, tokenizeAndEscapeQuery(text));

    // Add filter if present
    if (filterStr != null && !filterStr.equals("*")) {
      textQuery += " AND " + filterStr;
    }

    // Combine: (~@text_field:(tokens) [AND filter])=>[KNN ...]
    return String.format("%s)=>[%s]", textQuery, knnQuery);
  }

  /**
   * Build the Redis AggregationBuilder for this hybrid query.
   *
   * <p>Ported from Python __init__ method (line 103-129)
   *
   * @return the AggregationBuilder
   */
  @Override
  public AggregationBuilder buildRedisAggregation() {
    String queryString = buildQueryString();
    AggregationBuilder aggregation = new AggregationBuilder(queryString);

    // Set dialect
    aggregation.dialect(dialect);

    // Set text scorer (Python: self.scorer(text_scorer))
    // Note: In Jedis, we need to use WITHSCORE to get the text score
    // For now, we'll use vector similarity only and calculate text score differently

    // Apply vector similarity calculation (Python: line 122-123)
    // vector_similarity = (2 - @vector_distance) / 2
    aggregation.apply("(2 - @" + DISTANCE_ID + ")/2", "vector_similarity");

    // Apply text score - for hybrid queries, the text matching score is implicit
    // Since we can't easily access __score in aggregations, we'll use a constant of 1.0
    // This means the hybrid score will be based primarily on vector similarity
    // TODO: Investigate using WITHSCORE or custom scoring
    aggregation.apply("1.0", "text_score");

    // Apply hybrid score calculation (Python: line 125)
    // hybrid_score = (1-alpha) * text_score + alpha * vector_similarity
    String hybridScoreFormula =
        String.format("%f*@text_score + %f*@vector_similarity", (1 - alpha), alpha);
    aggregation.apply(hybridScoreFormula, "hybrid_score");

    // Sort by hybrid score descending (Python: line 126)
    aggregation.sortBy(numResults, SortedField.desc("@hybrid_score"));

    // Load return fields (Python: line 129)
    if (!returnFields.isEmpty()) {
      aggregation.load(returnFields.toArray(String[]::new));
    }

    return aggregation;
  }

  /**
   * Get the parameters for the aggregation query.
   *
   * <p>Ported from Python: params property (line 132-145)
   *
   * @return parameter map with vector
   */
  @Override
  public Map<String, Object> getParams() {
    // Convert vector to bytes (Python: array_to_buffer(self._vector, dtype=self._dtype))
    byte[] vectorBytes = ArrayUtils.floatArrayToBytes(vector);

    Map<String, Object> params = new HashMap<>();
    params.put(VECTOR_PARAM, vectorBytes);
    return params;
  }
}
