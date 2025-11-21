package com.redis.vl.query;

import java.util.Set;

/**
 * AggregateHybridQuery combines text and vector search in Redis using aggregation.
 *
 * <p>This is the primary name for hybrid queries in RedisVL, matching the Python implementation. It
 * is a type alias for {@link HybridQuery}.
 *
 * <p>Ported from Python: redisvl/query/aggregate.py:57-315 (AggregateHybridQuery class)
 *
 * <p>It allows you to perform a hybrid search using both text and vector similarity. It scores
 * documents based on a weighted combination of text and vector similarity.
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
 * HybridQuery query = AggregateHybridQuery.builder()
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
 *     .stopwords(HybridQuery.loadDefaultStopwords("english"))
 *     .dialect(2)
 *     .build();
 * List&lt;Map&lt;String, Object&gt;&gt; results = index.query(query);
 * </pre>
 *
 * @since 0.1.0
 */
public final class AggregateHybridQuery {

  // Private constructor to prevent instantiation
  private AggregateHybridQuery() {
    throw new UnsupportedOperationException("AggregateHybridQuery is a type alias for HybridQuery");
  }

  /**
   * Create a new builder for AggregateHybridQuery (delegates to HybridQuery.builder()).
   *
   * @return A new HybridQuery.HybridQueryBuilder instance
   */
  public static HybridQuery.HybridQueryBuilder builder() {
    return HybridQuery.builder();
  }

  /**
   * Load default stopwords for a given language (delegates to HybridQuery.loadDefaultStopwords()).
   *
   * @param language the language (e.g., "english", "german")
   * @return set of stopwords
   */
  public static Set<String> loadDefaultStopwords(String language) {
    return HybridQuery.loadDefaultStopwords(language);
  }
}
