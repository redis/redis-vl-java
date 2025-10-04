package com.redis.vl.utils.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.*;
import lombok.Builder;
import okhttp3.*;

/**
 * Reranker that uses VoyageAI's Rerank API to rerank documents based on query relevance.
 *
 * <p>This reranker interacts with VoyageAI's /v1/rerank API, requiring an API key for
 * authentication. The API key can be provided directly in the {@code apiConfig} Map or through the
 * {@code VOYAGE_API_KEY} environment variable.
 *
 * <p>Users must obtain an API key from <a href="https://dash.voyageai.com/">VoyageAI Dashboard</a>.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Initialize with API key
 * Map<String, String> apiConfig = Map.of("api_key", "your-api-key");
 * VoyageAIReranker reranker = VoyageAIReranker.builder()
 *     .model("rerank-lite-1")
 *     .apiConfig(apiConfig)
 *     .limit(3)
 *     .build();
 *
 * // Rerank string documents
 * List<String> docs = Arrays.asList("doc1", "doc2", "doc3");
 * RerankResult result = reranker.rank("query", docs);
 * }</pre>
 *
 * @see <a href="https://docs.voyageai.com/docs/reranker">VoyageAI Rerank API</a>
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification =
        "Lombok @Builder generates methods that store mutable objects, "
            + "but defensive copies are made in constructor")
public class VoyageAIReranker extends BaseReranker {

  private static final String API_ENDPOINT = "https://api.voyageai.com/v1/rerank";
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final Map<String, String> apiConfig;
  private final OkHttpClient httpClient;
  private final ObjectMapper objectMapper;

  /**
   * Create a VoyageAIReranker using the builder.
   *
   * @param model The VoyageAI model to use (e.g., "rerank-lite-1", "rerank-2")
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
  private VoyageAIReranker(
      String model, Integer limit, Boolean returnScore, Map<String, String> apiConfig) {
    super(
        model != null ? model : "rerank-lite-1",
        null, // VoyageAI doesn't support rankBy
        limit != null ? limit : 5,
        returnScore != null ? returnScore : true);

    // Make defensive copy of apiConfig
    this.apiConfig =
        apiConfig != null ? Collections.unmodifiableMap(new HashMap<>(apiConfig)) : null;

    this.httpClient = new OkHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Rerank documents based on query relevance using VoyageAI's Rerank API.
   *
   * @param query The search query
   * @param docs List of documents (must be List&lt;String&gt;)
   * @return RerankResult with reranked documents and relevance scores
   * @throws IllegalArgumentException if query or docs are invalid
   */
  @Override
  public RerankResult rank(String query, List<?> docs) {
    return rank(query, docs, Collections.emptyMap());
  }

  /**
   * Rerank documents based on query relevance using VoyageAI's Rerank API with runtime parameter
   * overrides.
   *
   * @param query The search query
   * @param docs List of documents (must be List&lt;String&gt;)
   * @param kwargs Optional parameters to override defaults (limit, return_score, truncation)
   * @return RerankResult with reranked documents and relevance scores
   * @throws IllegalArgumentException if query or docs are invalid
   */
  @SuppressWarnings("unchecked")
  public RerankResult rank(String query, List<?> docs, Map<String, Object> kwargs) {
    validateQuery(query);
    validateDocs(docs);

    if (docs.isEmpty()) {
      return new RerankResult(Collections.emptyList(), Collections.emptyList());
    }

    // Get API key from config or environment
    String apiKey = null;
    if (apiConfig != null && apiConfig.containsKey("api_key")) {
      apiKey = apiConfig.get("api_key");
    }
    if (apiKey == null || apiKey.isEmpty()) {
      apiKey = System.getenv("VOYAGE_API_KEY");
    }
    if (apiKey == null || apiKey.isEmpty()) {
      throw new IllegalArgumentException(
          "VoyageAI API key is required. "
              + "Provide it in apiConfig or set the VOYAGE_API_KEY environment variable.");
    }

    // Extract runtime parameters with defaults
    int effectiveLimit = (Integer) kwargs.getOrDefault("limit", this.limit);
    boolean effectiveReturnScore = (Boolean) kwargs.getOrDefault("return_score", this.returnScore);
    Object truncation = kwargs.get("truncation");

    // VoyageAI only supports string documents
    List<String> stringDocs;
    if (docs.get(0) instanceof Map) {
      // Extract "content" field from dict docs
      stringDocs = new ArrayList<>();
      for (Object doc : docs) {
        Map<String, Object> docMap = (Map<String, Object>) doc;
        if (docMap.containsKey("content")) {
          stringDocs.add(String.valueOf(docMap.get("content")));
        } else {
          throw new IllegalArgumentException(
              "VoyageAI reranker requires documents to be strings or have a 'content' field");
        }
      }
    } else {
      stringDocs = (List<String>) docs;
    }

    try {
      // Build request JSON
      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("query", query);
      requestBody.put("documents", stringDocs);
      requestBody.put("model", model);
      requestBody.put("top_k", effectiveLimit);
      if (truncation != null) {
        requestBody.put("truncation", truncation);
      }

      String jsonBody = objectMapper.writeValueAsString(requestBody);

      // Make HTTP request
      Request request =
          new Request.Builder()
              .url(API_ENDPOINT)
              .post(RequestBody.create(jsonBody, JSON))
              .addHeader("Authorization", "Bearer " + apiKey)
              .addHeader("Content-Type", "application/json")
              .build();

      try (Response response = httpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          throw new RuntimeException(
              "VoyageAI API request failed: "
                  + response.code()
                  + " "
                  + (response.body() != null ? response.body().string() : ""));
        }

        ResponseBody responseBody = response.body();
        if (responseBody == null) {
          throw new RuntimeException("VoyageAI API returned null response body");
        }

        String responseBodyString = responseBody.string();
        JsonNode jsonResponse = objectMapper.readTree(responseBodyString);
        JsonNode results = jsonResponse.get("data");

        List<Object> rerankedDocs = new ArrayList<>();
        List<Double> scores = new ArrayList<>();

        for (JsonNode result : results) {
          int index = result.get("index").asInt();
          double score = result.get("relevance_score").asDouble();

          rerankedDocs.add(docs.get(index));
          scores.add(score);
        }

        return new RerankResult(rerankedDocs, effectiveReturnScore ? scores : null);
      }

    } catch (IOException e) {
      throw new RuntimeException("Failed to call VoyageAI rerank API", e);
    }
  }
}
