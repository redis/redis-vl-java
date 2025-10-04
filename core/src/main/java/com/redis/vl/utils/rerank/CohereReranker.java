package com.redis.vl.utils.rerank;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Builder;

/**
 * Reranker that uses Cohere's Rerank API to rerank documents based on query relevance.
 *
 * <p>This reranker interacts with Cohere's /rerank API, requiring an API key for authentication.
 * The API key can be provided directly in the {@code apiConfig} Map or through the {@code
 * COHERE_API_KEY} environment variable.
 *
 * <p>Users must obtain an API key from <a
 * href="https://dashboard.cohere.com/">https://dashboard.cohere.com/</a>. Additionally, the {@code
 * com.cohere:cohere-java} library must be available on the classpath.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Initialize with API key
 * Map<String, String> apiConfig = Map.of("api_key", "your-api-key");
 * CohereReranker reranker = CohereReranker.builder()
 *     .apiConfig(apiConfig)
 *     .limit(3)
 *     .build();
 *
 * // Rerank string documents
 * List<String> docs = Arrays.asList("doc1", "doc2", "doc3");
 * RerankResult result = reranker.rank("query", docs);
 *
 * // Rerank dict documents with rankBy fields
 * List<Map<String, Object>> dictDocs = Arrays.asList(
 *     Map.of("content", "doc1", "source", "wiki"),
 *     Map.of("content", "doc2", "source", "textbook")
 * );
 * CohereReranker reranker2 = CohereReranker.builder()
 *     .apiConfig(apiConfig)
 *     .rankBy(Arrays.asList("content", "source"))
 *     .build();
 * RerankResult result2 = reranker2.rank("query", dictDocs);
 * }</pre>
 *
 * @see <a href="https://docs.cohere.com/reference/rerank">Cohere Rerank API</a>
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification =
        "Lombok @Builder generates methods that store mutable objects, "
            + "but defensive copies are made in constructor")
public class CohereReranker extends BaseReranker {

  private final Map<String, String> apiConfig;
  private volatile Object client; // com.cohere.api.Cohere (lazy loaded)

  /**
   * Create a CohereReranker using the builder.
   *
   * @param model The Cohere model to use (default: "rerank-english-v3.0")
   * @param rankBy List of fields to rank by for dict documents (optional)
   * @param limit Maximum number of results to return (default: 5)
   * @param returnScore Whether to return relevance scores (default: true)
   * @param apiConfig Map containing API configuration (must have "api_key" key)
   */
  @Builder
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Lombok builder generates methods that store mutable objects, "
              + "but defensive copies are made in constructor")
  private CohereReranker(
      String model,
      List<String> rankBy,
      Integer limit,
      Boolean returnScore,
      Map<String, String> apiConfig) {
    super(
        model != null ? model : "rerank-english-v3.0",
        rankBy,
        limit != null ? limit : 5,
        returnScore != null ? returnScore : true);

    // Make defensive copy of apiConfig to avoid EI2 SpotBugs warning
    this.apiConfig =
        apiConfig != null ? Collections.unmodifiableMap(new HashMap<>(apiConfig)) : null;
  }

  /** Initialize the Cohere client using the API key from apiConfig or environment. */
  private synchronized void initializeClient() {
    if (client != null) {
      return;
    }

    // Check for Cohere SDK availability
    try {
      Class.forName("com.cohere.api.Cohere");
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Cohere reranker requires the cohere-java library. "
              + "Please add dependency: com.cohere:cohere-java:1.8.1",
          e);
    }

    // Get API key from config or environment
    String apiKey = null;
    if (apiConfig != null && apiConfig.containsKey("api_key")) {
      apiKey = apiConfig.get("api_key");
    }
    if (apiKey == null || apiKey.isEmpty()) {
      apiKey = System.getenv("COHERE_API_KEY");
    }
    if (apiKey == null || apiKey.isEmpty()) {
      throw new IllegalArgumentException(
          "Cohere API key is required. "
              + "Provide it in apiConfig or set the COHERE_API_KEY environment variable.");
    }

    // Create Cohere client
    this.client = createCohereClient(apiKey);
  }

  /**
   * Create a Cohere client instance.
   *
   * @param apiKey The API key
   * @return Cohere client
   */
  private Object createCohereClient(String apiKey) {
    try {
      // Import Cohere class
      Class<?> cohereClass = Class.forName("com.cohere.api.Cohere");

      // Call Cohere.builder()
      Object builder = cohereClass.getMethod("builder").invoke(null);

      // Get builder class
      Class<?> builderClass = builder.getClass();

      // Call .token(apiKey)
      builderClass.getMethod("token", String.class).invoke(builder, apiKey);

      // Call .clientName("redisvl4j")
      builderClass.getMethod("clientName", String.class).invoke(builder, "redisvl4j");

      // Call .build()
      return builderClass.getMethod("build").invoke(builder);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create Cohere client", e);
    }
  }

  /**
   * Rerank documents based on query relevance using Cohere's Rerank API.
   *
   * @param query The search query
   * @param docs List of documents (either List&lt;String&gt; or List&lt;Map&lt;String,
   *     Object&gt;&gt;)
   * @return RerankResult with reranked documents and relevance scores
   * @throws IllegalArgumentException if query or docs are invalid
   */
  @Override
  public RerankResult rank(String query, List<?> docs) {
    validateQuery(query);
    validateDocs(docs);

    if (docs.isEmpty()) {
      return new RerankResult(Collections.emptyList(), Collections.emptyList());
    }

    // Lazy initialize client
    if (client == null) {
      initializeClient();
    }

    // Determine if we're working with strings or dicts
    boolean isDictDocs = !docs.isEmpty() && docs.get(0) instanceof Map;

    if (isDictDocs && (rankBy == null || rankBy.isEmpty())) {
      throw new IllegalArgumentException(
          "If reranking dictionary-like docs, you must provide a list of rankBy fields");
    }

    try {
      // Call Cohere rerank API
      Object response = callRerankApi(query, docs, isDictDocs);

      // Extract results
      return extractResults(docs, response);

    } catch (Exception e) {
      throw new RuntimeException("Failed to call Cohere rerank API", e);
    }
  }

  /**
   * Call the Cohere rerank API.
   *
   * @param query The search query
   * @param docs Documents to rerank
   * @param isDictDocs Whether documents are Maps or Strings
   * @return Response from Cohere API
   * @throws Exception if API call fails
   */
  private Object callRerankApi(String query, List<?> docs, boolean isDictDocs) throws Exception {
    // Get RerankRequest.builder()
    Class<?> rerankRequestClass = Class.forName("com.cohere.api.requests.RerankRequest");
    Object requestBuilder = rerankRequestClass.getMethod("builder").invoke(null);

    // Get builder class (it's a staged builder, so we need to follow the stages)
    Class<?> currentStageClass = requestBuilder.getClass();

    // Set query (QueryStage -> DocumentsStage)
    Object documentsStage =
        currentStageClass.getMethod("query", String.class).invoke(requestBuilder, query);

    // Convert docs to RerankRequestDocumentsItem list
    List<Object> documentItems = convertToDocumentItems(docs, isDictDocs);

    // Set documents (DocumentsStage -> _FinalStage)
    Class<?> documentsStageClass = documentsStage.getClass();
    Object finalStage =
        documentsStageClass
            .getMethod("documents", List.class)
            .invoke(documentsStage, documentItems);

    // On the final stage, set optional parameters
    Class<?> finalStageClass = finalStage.getClass();

    // Set model if not default
    finalStage = finalStageClass.getMethod("model", String.class).invoke(finalStage, model);

    // Set topN (limit)
    finalStage = finalStageClass.getMethod("topN", Integer.class).invoke(finalStage, limit);

    // Set rankFields for dict documents
    if (isDictDocs && rankBy != null && !rankBy.isEmpty()) {
      finalStage = finalStageClass.getMethod("rankFields", List.class).invoke(finalStage, rankBy);
    }

    // Build the request
    Object request = finalStageClass.getMethod("build").invoke(finalStage);

    // Call client.rerank(request)
    Class<?> cohereClass = client.getClass();
    return cohereClass.getMethod("rerank", rerankRequestClass).invoke(client, request);
  }

  /**
   * Convert documents to RerankRequestDocumentsItem list.
   *
   * @param docs Documents
   * @param isDictDocs Whether documents are Maps or Strings
   * @return List of RerankRequestDocumentsItem
   * @throws Exception if conversion fails
   */
  private List<Object> convertToDocumentItems(List<?> docs, boolean isDictDocs) throws Exception {
    Class<?> documentItemClass = Class.forName("com.cohere.api.types.RerankRequestDocumentsItem");

    List<Object> result = new ArrayList<>();
    for (Object doc : docs) {
      Object item;
      if (isDictDocs) {
        // Convert Map<String, Object> to Map<String, String> for Cohere API
        @SuppressWarnings("unchecked")
        Map<String, Object> docMap = (Map<String, Object>) doc;
        Map<String, String> stringMap =
            docMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));

        // Call RerankRequestDocumentsItem.of(Map<String, String>)
        item = documentItemClass.getMethod("of", Map.class).invoke(null, stringMap);
      } else {
        // Call RerankRequestDocumentsItem.of(String)
        item = documentItemClass.getMethod("of", String.class).invoke(null, doc);
      }
      result.add(item);
    }
    return result;
  }

  /**
   * Extract reranked results from Cohere API response.
   *
   * @param originalDocs Original documents
   * @param response Cohere API response
   * @return RerankResult with reranked documents and scores
   * @throws Exception if extraction fails
   */
  private RerankResult extractResults(List<?> originalDocs, Object response) throws Exception {
    // Get results from response
    Class<?> responseClass = response.getClass();
    List<?> results = (List<?>) responseClass.getMethod("getResults").invoke(response);

    List<Object> rerankedDocs = new ArrayList<>();
    List<Double> scores = new ArrayList<>();

    // Extract each result
    for (Object result : results) {
      Class<?> resultClass = result.getClass();

      // Get index
      int index = (Integer) resultClass.getMethod("getIndex").invoke(result);

      // Get relevance score (float -> double)
      float scoreFloat = (Float) resultClass.getMethod("getRelevanceScore").invoke(result);
      double score = (double) scoreFloat;

      // Add to results
      rerankedDocs.add(originalDocs.get(index));
      scores.add(score);
    }

    return new RerankResult(rerankedDocs, returnScore ? scores : null);
  }
}
