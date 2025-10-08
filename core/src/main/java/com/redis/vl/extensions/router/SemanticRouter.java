package com.redis.vl.extensions.router;

import com.redis.vl.index.SearchIndex;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import redis.clients.jedis.UnifiedJedis;

/**
 * Semantic Router for managing and querying route vectors. Ported from Python:
 * redisvl/extensions/router/semantic.py:33
 */
@Getter
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification =
        "SemanticRouter fields are intentionally exposed for Python API compatibility and shared references")
public class SemanticRouter {
  private final String name;
  private final List<Route> routes;
  private RoutingConfig routingConfig;
  private final BaseVectorizer vectorizer;
  private final UnifiedJedis unifiedJedis;
  private SearchIndex index;

  /**
   * Legacy constructor for backwards compatibility.
   *
   * @param name the router name
   */
  public SemanticRouter(String name) {
    this.name = name;
    this.routes = List.of();
    this.routingConfig = RoutingConfig.builder().build();
    this.vectorizer = null;
    this.unifiedJedis = null;
    this.index = null;
  }

  /**
   * Create a new builder for SemanticRouter.
   *
   * @return a new SemanticRouterBuilder
   */
  public static SemanticRouterBuilder builder() {
    return new SemanticRouterBuilder();
  }

  private SemanticRouter(SemanticRouterBuilder builder) {
    this.name = builder.name;
    this.routes = builder.routes != null ? builder.routes : List.of();
    this.routingConfig =
        builder.routingConfig != null ? builder.routingConfig : RoutingConfig.builder().build();
    this.vectorizer = builder.vectorizer;
    this.unifiedJedis = builder.unifiedJedis;
    this.index = null; // Will be initialized when needed
  }

  /** Builder for SemanticRouter. */
  @SuppressWarnings("javadoc")
  public static class SemanticRouterBuilder {
    private String name;
    private List<Route> routes;
    private RoutingConfig routingConfig;
    private BaseVectorizer vectorizer;
    private UnifiedJedis unifiedJedis;
    private boolean overwrite = false;

    /**
     * Set the router name.
     *
     * @param name the router name
     * @return this builder
     */
    public SemanticRouterBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Set the routes.
     *
     * @param routes the list of routes
     * @return this builder
     */
    public SemanticRouterBuilder routes(List<Route> routes) {
      this.routes = routes;
      return this;
    }

    /**
     * Set the routing configuration.
     *
     * @param routingConfig the routing configuration
     * @return this builder
     */
    public SemanticRouterBuilder routingConfig(RoutingConfig routingConfig) {
      this.routingConfig = routingConfig;
      return this;
    }

    /**
     * Set the vectorizer.
     *
     * @param vectorizer the vectorizer
     * @return this builder
     */
    public SemanticRouterBuilder vectorizer(BaseVectorizer vectorizer) {
      this.vectorizer = vectorizer;
      return this;
    }

    /**
     * Set the UnifiedJedis client.
     *
     * @param unifiedJedis the UnifiedJedis client
     * @return this builder
     */
    public SemanticRouterBuilder jedis(UnifiedJedis unifiedJedis) {
      this.unifiedJedis = unifiedJedis;
      return this;
    }

    /**
     * Set whether to overwrite existing index.
     *
     * @param overwrite true to overwrite
     * @return this builder
     */
    public SemanticRouterBuilder overwrite(boolean overwrite) {
      this.overwrite = overwrite;
      return this;
    }

    /**
     * Build the SemanticRouter.
     *
     * @return the built SemanticRouter
     */
    public SemanticRouter build() {
      SemanticRouter router = new SemanticRouter(this);
      if (unifiedJedis != null && vectorizer != null) {
        router.initializeIndex(overwrite);
      }
      return router;
    }
  }

  /**
   * Get the list of route names. Ported from Python: route_names property (line 187)
   *
   * @return list of route names
   */
  public List<String> getRouteNames() {
    return routes.stream().map(Route::getName).toList();
  }

  /**
   * Get the distance thresholds for each route. Ported from Python: route_thresholds property (line
   * 196)
   *
   * @return map of route names to distance thresholds
   */
  public java.util.Map<String, Double> getRouteThresholds() {
    java.util.Map<String, Double> thresholds = new java.util.HashMap<>();
    for (Route route : routes) {
      thresholds.put(route.getName(), route.getDistanceThreshold());
    }
    return thresholds;
  }

  /**
   * Convert router to dictionary for JSON serialization. Ported from Python: to_dict() (line 562)
   *
   * @return Map representation of the router
   */
  public java.util.Map<String, Object> toDict() {
    java.util.Map<String, Object> dict = new java.util.HashMap<>();
    dict.put("name", name);

    java.util.List<java.util.Map<String, Object>> routeDicts = new java.util.ArrayList<>();
    for (Route route : routes) {
      routeDicts.add(route.toDict());
    }
    dict.put("routes", routeDicts);

    java.util.Map<String, Object> vectorizerDict = new java.util.HashMap<>();
    vectorizerDict.put("type", vectorizer != null ? vectorizer.getClass().getSimpleName() : null);
    vectorizerDict.put(
        "model", vectorizer != null ? vectorizer.getModelName() : null); // Use getModelName()
    dict.put("vectorizer", vectorizerDict);

    java.util.Map<String, Object> configDict = new java.util.HashMap<>();
    configDict.put("max_k", routingConfig.getMaxK());
    configDict.put("aggregation_method", routingConfig.getAggregationMethod().name().toLowerCase());
    dict.put("routing_config", configDict);

    return dict;
  }

  /**
   * Save router configuration to Redis as JSON. Ported from Python: line 110
   *
   * <p>Stores the router configuration at key "{name}:route_config"
   */
  private void saveRouterConfig() {
    if (unifiedJedis == null) {
      return;
    }
    String key = name + ":route_config";
    com.google.gson.Gson gson = new com.google.gson.Gson();
    String json = gson.toJson(toDict());
    unifiedJedis.jsonSet(key, json);
  }

  /**
   * Load router configuration from Redis JSON. Ported from Python: from_existing() (line 138)
   *
   * @param name the router name
   * @param jedis the Redis client
   * @return the loaded router configuration
   */
  private static java.util.Map<String, Object> loadRouterConfig(
      String name, redis.clients.jedis.UnifiedJedis jedis) {
    String key = name + ":route_config";
    Object jsonObj = jedis.jsonGet(key);
    if (jsonObj == null) {
      throw new IllegalStateException("No router config found for: " + name);
    }
    String json = jsonObj.toString();
    com.google.gson.Gson gson = new com.google.gson.Gson();
    com.google.gson.reflect.TypeToken<java.util.Map<String, Object>> typeToken =
        new com.google.gson.reflect.TypeToken<java.util.Map<String, Object>>() {};
    return gson.fromJson(json, typeToken.getType());
  }

  /**
   * Get a route by its name. Ported from Python: get() (line 262)
   *
   * @param routeName the name of the route
   * @return the route, or null if not found
   */
  public Route get(String routeName) {
    return routes.stream().filter(r -> r.getName().equals(routeName)).findFirst().orElse(null);
  }

  /**
   * Update the routing configuration. Ported from Python: update_routing_config() (line 204)
   *
   * @param newConfig the new routing configuration
   */
  public void updateRoutingConfig(RoutingConfig newConfig) {
    this.routingConfig = newConfig;
  }

  /**
   * Convert the SemanticRouter to a Map representation. Ported from Python: to_dict() (line 562)
   *
   * @return map representation of the router
   */
  public java.util.Map<String, Object> toMap() {
    java.util.Map<String, Object> map = new java.util.HashMap<>();
    map.put("name", name);
    map.put("routes", routes);
    map.put("routingConfig", routingConfig);
    return map;
  }

  /** Clear all route data from Redis. Ported from Python: clear() (line 501) */
  public void clear() {
    if (index != null) {
      // TODO: Implement clearing route data from Redis
    }
  }

  /** Delete the semantic router index from Redis. Ported from Python: delete() (line 497) */
  public void delete() {
    if (index != null) {
      index.delete(true); // Drop index and data
    }
  }

  /**
   * Initialize the search index and handle Redis connection. Ported from Python:
   * _initialize_index() (line 148)
   */
  private void initializeIndex(boolean overwrite) {
    if (vectorizer == null || unifiedJedis == null) {
      throw new IllegalStateException(
          "Vectorizer and UnifiedJedis are required for index initialization");
    }

    // Create schema from router parameters
    com.redis.vl.schema.IndexSchema schema =
        SemanticRouterIndexSchema.fromParams(
            name, vectorizer.getDimensions(), vectorizer.getDataType());

    // Create search index
    this.index = new SearchIndex(schema, unifiedJedis);

    // Create the index in Redis
    boolean existed = index.exists();
    if (!existed || overwrite) {
      index.create(overwrite);
      // Add routes to Redis
      addRoutes(this.routes);
      // Save router configuration as JSON (Python: line 110)
      saveRouterConfig();
    }
  }

  /** Add routes to the router and index. Ported from Python: _add_routes() (line 227) */
  private void addRoutes(List<Route> routesToAdd) {
    if (routesToAdd == null || routesToAdd.isEmpty()) {
      return;
    }

    java.util.List<java.util.Map<String, Object>> routeReferences = new java.util.ArrayList<>();

    for (Route route : routesToAdd) {
      // Embed route references as a batch
      java.util.List<String> references = route.getReferences();
      java.util.List<float[]> referenceVectors = vectorizer.embedBatch(references);

      // Create a document for each reference
      for (int i = 0; i < references.size(); i++) {
        String reference = references.get(i);
        String referenceHash = hashify(reference);
        float[] vector = referenceVectors.get(i);
        String key = routeRefKey(index, route.getName(), referenceHash);

        java.util.Map<String, Object> doc = new java.util.HashMap<>();
        doc.put("id", key); // Full key including prefix
        doc.put("reference_id", referenceHash);
        doc.put("route_name", route.getName());
        doc.put("reference", reference);
        doc.put("vector", vector);

        routeReferences.add(doc);
      }
    }

    // Load all route references into Redis using "id" as the key field
    if (!routeReferences.isEmpty()) {
      index.load(routeReferences, "id");
    }
  }

  /** Create a hash of a string for use as a reference ID. Ported from Python: hashify utility */
  private String hashify(String text) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (int i = 0; i < Math.min(8, hash.length); i++) {
        String hex = Integer.toHexString(0xff & hash[i]);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      // Fallback to simple hash
      return Integer.toHexString(text.hashCode());
    }
  }

  /**
   * Route a query to the best matching route. Ported from Python: __call__() (line 410-439)
   *
   * @param text the query text to route
   * @return the best matching route, or RouteMatch with null name if no match
   */
  public RouteMatch route(String text) {
    return route(text, null, null);
  }

  /**
   * Route a query to the best matching route with full control. Ported from Python: __call__()
   * (line 410-439)
   *
   * @param text the query text (optional if vector provided)
   * @param vector pre-computed embedding vector (optional if text provided)
   * @param aggregationMethod method to aggregate distances (null uses config default)
   * @return the best matching route, or RouteMatch with null name if no match
   */
  public RouteMatch route(
      String text, float[] vector, DistanceAggregationMethod aggregationMethod) {
    if (vector == null) {
      if (text == null || text.isEmpty()) {
        throw new IllegalArgumentException("Must provide text or vector");
      }
      vector = vectorizer.embed(text);
    }

    if (aggregationMethod == null) {
      aggregationMethod = routingConfig.getAggregationMethod();
    }

    List<RouteMatch> matches = getRouteMatches(vector, aggregationMethod, 1);
    if (matches.isEmpty()) {
      return RouteMatch.builder().build();
    }

    RouteMatch match = matches.get(0);
    // Check distance threshold
    if (match.getDistance() != null) {
      Double threshold = getRouteThresholds().get(match.getName());
      if (threshold != null && match.getDistance() > threshold) {
        return RouteMatch.builder().build();
      }
    }

    return match;
  }

  /**
   * Route a query to multiple matching routes. Ported from Python: route_many() (line 442-477)
   *
   * @param text the query text to route
   * @return list of matching routes
   */
  public List<RouteMatch> routeMany(String text) {
    return routeMany(text, null, null, null);
  }

  /**
   * Route a query to multiple matching routes with full control. Ported from Python: route_many()
   * (line 442-477)
   *
   * @param text the query text (optional if vector provided)
   * @param maxK maximum number of routes to return (null uses config default)
   * @param vector pre-computed embedding vector (optional if text provided)
   * @param aggregationMethod method to aggregate distances (null uses config default)
   * @return list of matching routes
   */
  public List<RouteMatch> routeMany(
      String text, Integer maxK, float[] vector, DistanceAggregationMethod aggregationMethod) {
    if (vector == null) {
      if (text == null || text.isEmpty()) {
        throw new IllegalArgumentException("Must provide text or vector");
      }
      vector = vectorizer.embed(text);
    }

    if (maxK == null) {
      maxK = routingConfig.getMaxK();
    }

    if (aggregationMethod == null) {
      aggregationMethod = routingConfig.getAggregationMethod();
    }

    List<RouteMatch> matches = getRouteMatches(vector, aggregationMethod, maxK);

    // Filter by distance thresholds
    java.util.Map<String, Double> thresholds = getRouteThresholds();
    return matches.stream()
        .filter(
            match -> {
              if (match.getDistance() == null) {
                return true; // Keep matches with no distance info
              }
              Double threshold = thresholds.get(match.getName());
              return threshold == null || match.getDistance() <= threshold;
            })
        .toList();
  }

  /**
   * Get route matches using vector similarity search and aggregation. Ported from Python:
   * _get_route_matches() (line 324-361)
   *
   * @param vector the query vector
   * @param aggregationMethod method to aggregate distances
   * @param maxK maximum number of routes to return
   * @return list of route matches sorted by distance
   */
  private List<RouteMatch> getRouteMatches(
      float[] vector, DistanceAggregationMethod aggregationMethod, int maxK) {
    if (index == null) {
      throw new IllegalStateException("Index not initialized");
    }

    // Get max distance threshold across all routes (Python: line 334)
    double maxThreshold =
        routes.stream().mapToDouble(Route::getDistanceThreshold).max().orElse(1.0);

    // Create vector range query with max threshold (Python: line 336-341)
    com.redis.vl.query.VectorRangeQuery vectorQuery =
        com.redis.vl.query.VectorRangeQuery.builder()
            .vector(vector)
            .field("vector")
            .distanceThreshold(maxThreshold)
            .numResults(1000) // Get many results for aggregation
            .build();

    // Determine the reducer based on aggregation method
    // The KNN query names the score field as "vector_distance" (Python: DISTANCE_ID)
    redis.clients.jedis.search.aggr.Reducer distanceReducer;
    String reducedField = "distance"; // Output field name after reduction
    switch (aggregationMethod) {
      case MIN:
        distanceReducer =
            redis.clients.jedis.search.aggr.Reducers.min("vector_distance").as(reducedField);
        break;
      case SUM:
        distanceReducer =
            redis.clients.jedis.search.aggr.Reducers.sum("vector_distance").as(reducedField);
        break;
      case AVG:
      default:
        distanceReducer =
            redis.clients.jedis.search.aggr.Reducers.avg("vector_distance").as(reducedField);
        break;
    }

    // Build distance threshold filter (Python: _distance_threshold_filter, line 280)
    // Creates filter like: (@route_name == 'greeting' && @distance < 0.3) || (@route_name ==
    // 'farewell' && @distance < 0.2)
    // Note: Use the reduced field name with @ prefix
    StringBuilder filterBuilder = new StringBuilder();
    for (int i = 0; i < routes.size(); i++) {
      Route route = routes.get(i);
      if (i > 0) {
        filterBuilder.append(" || ");
      }
      filterBuilder
          .append("(@route_name == '")
          .append(route.getName())
          .append("' && @")
          .append(reducedField) // Use the reducer's output field name
          .append(" < ")
          .append(route.getDistanceThreshold())
          .append(")");
    }

    // Build aggregation request (Python: _build_aggregate_request, line 308-320)
    redis.clients.jedis.search.aggr.AggregationBuilder aggregation =
        new redis.clients.jedis.search.aggr.AggregationBuilder(vectorQuery.toQueryString())
            .params(vectorQuery.toParams())
            .load("route_name", "vector_distance")
            .groupBy("@route_name", distanceReducer)
            .filter(filterBuilder.toString()) // Apply per-route distance thresholds
            .sortBy(redis.clients.jedis.search.aggr.SortedField.asc("@" + reducedField))
            .limit(0, maxK)
            .dialect(2); // Use dialect 2 for filter support (Python: line 315)

    // Execute search with aggregation
    redis.clients.jedis.search.aggr.AggregationResult result =
        unifiedJedis.ftAggregate(index.getName(), aggregation);

    // Convert results to RouteMatch list
    java.util.List<RouteMatch> matches = new java.util.ArrayList<>();
    for (java.util.Map<String, Object> row : result.getResults()) {
      String routeName = (String) row.get("route_name");
      Object distanceObj = row.get(reducedField);

      // Jedis aggregation results come back as strings, not numbers
      Double score;
      if (distanceObj instanceof Number) {
        score = ((Number) distanceObj).doubleValue();
      } else if (distanceObj instanceof String) {
        score = Double.parseDouble((String) distanceObj);
      } else {
        score = null;
      }

      // VECTOR_RANGE with $YIELD_DISTANCE_AS returns actual distance (0-2 for COSINE)
      Double distance = score;

      matches.add(RouteMatch.builder().name(routeName).distance(distance).build());
    }

    return matches;
  }

  /**
   * Add reference(s) to an existing route. Ported from Python: add_route_references() (line 651)
   *
   * @param routeName the name of the route to add references to
   * @param references the list of references to add
   * @return list of added reference keys
   */
  public List<String> addRouteReferences(String routeName, List<String> references) {
    if (references == null || references.isEmpty()) {
      throw new IllegalArgumentException("References must not be empty");
    }

    java.util.List<java.util.Map<String, Object>> routeReferences = new java.util.ArrayList<>();
    java.util.List<String> keys = new java.util.ArrayList<>();

    // Embed route references as a batch
    java.util.List<float[]> referenceVectors = vectorizer.embedBatch(references);

    // Create documents for each reference
    for (int i = 0; i < references.size(); i++) {
      String reference = references.get(i);
      String referenceHash = hashify(reference);
      float[] vector = referenceVectors.get(i);
      String key = routeRefKey(index, routeName, referenceHash);

      java.util.Map<String, Object> doc = new java.util.HashMap<>();
      doc.put("id", key); // Full key including prefix
      doc.put("reference_id", referenceHash);
      doc.put("route_name", routeName);
      doc.put("reference", reference);
      doc.put("vector", vector);

      routeReferences.add(doc);
      keys.add(key);
    }

    // Load references into Redis using "id" as the key field
    index.load(routeReferences, "id");

    // Update the route's references list
    Route route = get(routeName);
    if (route == null) {
      throw new IllegalArgumentException("Route " + routeName + " not found in the SemanticRouter");
    }
    route.getReferences().addAll(references);

    // Save updated router configuration (Python: _update_router_state, line 800)
    saveRouterConfig();

    return keys;
  }

  /**
   * Get references for an existing route. Ported from Python: get_route_references() (line 714)
   *
   * @param routeName the name of the route (optional if referenceIds or keys provided)
   * @param referenceIds list of reference IDs to retrieve (optional)
   * @param keys list of fully qualified keys to retrieve (optional)
   * @return list of reference documents
   */
  public List<Map<String, Object>> getRouteReferences(
      String routeName, List<String> referenceIds, List<String> keys) {
    List<com.redis.vl.query.FilterQuery> queries;

    if (referenceIds != null && !referenceIds.isEmpty()) {
      queries = makeFilterQueries(referenceIds);
    } else if (routeName != null && !routeName.isEmpty()) {
      // Scan for keys matching pattern
      if (keys == null || keys.isEmpty()) {
        keys = scanByPattern(unifiedJedis, index.getPrefix() + ":" + routeName + ":*");
      }

      // Extract reference IDs from keys (last part after final colon)
      List<String> refIds = new java.util.ArrayList<>();
      for (String key : keys) {
        String[] parts = key.split(":");
        if (parts.length > 0) {
          refIds.add(parts[parts.length - 1]);
        }
      }
      queries = makeFilterQueries(refIds);
    } else {
      throw new IllegalArgumentException(
          "Must provide a route name, reference ids, or keys to get references");
    }

    // Execute batch query
    List<List<Map<String, Object>>> results = new java.util.ArrayList<>();
    for (com.redis.vl.query.FilterQuery query : queries) {
      List<Map<String, Object>> queryResult = index.query(query);
      results.add(queryResult);
    }

    // Flatten results - take first result from each query that has results
    List<Map<String, Object>> flatResults = new java.util.ArrayList<>();
    for (List<Map<String, Object>> result : results) {
      if (result != null && !result.isEmpty()) {
        flatResults.add(result.get(0));
      }
    }

    return flatResults;
  }

  /**
   * Delete references from an existing route. Ported from Python: delete_route_references() (line
   * 750)
   *
   * @param routeName the name of the route (optional if referenceIds or keys provided)
   * @param referenceIds list of reference IDs to delete (optional)
   * @param keys list of fully qualified keys to delete (optional)
   * @return number of deleted references
   */
  public int deleteRouteReferences(String routeName, List<String> referenceIds, List<String> keys) {
    List<String> keysToDelete = keys;

    if (referenceIds != null
        && !referenceIds.isEmpty()
        && (keysToDelete == null || keysToDelete.isEmpty())) {
      // Get keys from reference IDs
      List<com.redis.vl.query.FilterQuery> queries = makeFilterQueries(referenceIds);
      keysToDelete = new java.util.ArrayList<>();
      for (com.redis.vl.query.FilterQuery query : queries) {
        List<Map<String, Object>> queryResult = index.query(query);
        if (queryResult != null && !queryResult.isEmpty()) {
          Object id = queryResult.get(0).get("id");
          if (id != null) {
            keysToDelete.add(id.toString());
          }
        }
      }
    } else if (keysToDelete == null || keysToDelete.isEmpty()) {
      // Scan for keys by route name
      keysToDelete = scanByPattern(unifiedJedis, index.getPrefix() + ":" + routeName + ":*");
    }

    if (keysToDelete == null || keysToDelete.isEmpty()) {
      throw new IllegalArgumentException("No references found for route " + routeName);
    }

    // Collect references to be deleted
    List<java.util.Map.Entry<String, String>> toBeDeleted = new java.util.ArrayList<>();
    for (String key : keysToDelete) {
      String[] parts = key.split(":");
      String rName = parts.length >= 3 ? parts[parts.length - 2] : routeName;
      java.util.Map<String, String> refData = unifiedJedis.hgetAll(key);
      if (refData != null && refData.containsKey("reference")) {
        toBeDeleted.add(new java.util.AbstractMap.SimpleEntry<>(rName, refData.get("reference")));
      }
    }

    // Delete keys from Redis
    int deleted = index.dropKeys(keysToDelete);

    // Update route references lists
    for (java.util.Map.Entry<String, String> entry : toBeDeleted) {
      Route route = get(entry.getKey());
      if (route == null) {
        throw new IllegalArgumentException(
            "Route " + entry.getKey() + " not found in the SemanticRouter");
      }
      route.getReferences().remove(entry.getValue());
    }

    // Save updated router configuration (Python: _update_router_state, line 800)
    saveRouterConfig();

    return deleted;
  }

  /** Generate route reference key. Ported from Python: _route_ref_key() (line 223) */
  private static String routeRefKey(
      com.redis.vl.index.SearchIndex index, String routeName, String referenceHash) {
    // Return key WITHOUT prefix - the storage layer will add the prefix automatically
    return routeName + ":" + referenceHash;
  }

  /**
   * Create filter queries for the given reference IDs. Ported from Python: _make_filter_queries()
   * (line 698)
   */
  private static List<com.redis.vl.query.FilterQuery> makeFilterQueries(List<String> ids) {
    List<com.redis.vl.query.FilterQuery> queries = new java.util.ArrayList<>();

    for (String id : ids) {
      com.redis.vl.query.Filter filter = com.redis.vl.query.Filter.tag("reference_id", id);
      com.redis.vl.query.FilterQuery fq =
          com.redis.vl.query.FilterQuery.builder()
              .returnFields(List.of("reference_id", "route_name", "reference"))
              .filterExpression(filter)
              .build();
      queries.add(fq);
    }

    return queries;
  }

  /** Scan Redis for keys matching a pattern. Similar to Python's scan_by_pattern utility */
  private static List<String> scanByPattern(UnifiedJedis jedis, String pattern) {
    List<String> matchingKeys = new java.util.ArrayList<>();
    redis.clients.jedis.resps.ScanResult<String> scanResult =
        jedis.scan("0", new redis.clients.jedis.params.ScanParams().match(pattern).count(1000));

    matchingKeys.addAll(scanResult.getResult());

    String cursor = scanResult.getCursor();
    while (!cursor.equals("0")) {
      scanResult =
          jedis.scan(
              cursor, new redis.clients.jedis.params.ScanParams().match(pattern).count(1000));
      matchingKeys.addAll(scanResult.getResult());
      cursor = scanResult.getCursor();
    }

    return matchingKeys;
  }
}
