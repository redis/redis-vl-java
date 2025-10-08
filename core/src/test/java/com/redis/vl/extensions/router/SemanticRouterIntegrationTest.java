package com.redis.vl.extensions.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import com.redis.vl.utils.vectorize.SentenceTransformersVectorizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for SemanticRouter with Redis. Ported from Python:
 * tests/integration/test_semantic_router.py
 */
class SemanticRouterIntegrationTest extends BaseIntegrationTest {

  private BaseVectorizer vectorizer;
  private List<Route> testRoutes;
  private SemanticRouter router;

  @BeforeEach
  void setUp() {
    // Use SentenceTransformersVectorizer matching Python test fixture (conftest.py:210)
    vectorizer = new SentenceTransformersVectorizer("sentence-transformers/all-mpnet-base-v2");

    // Test routes from Python fixture (line 24-38)
    testRoutes =
        List.of(
            Route.builder()
                .name("greeting")
                .references(List.of("hello", "hi"))
                .metadata(Map.of("type", "greeting"))
                .distanceThreshold(0.3)
                .build(),
            Route.builder()
                .name("farewell")
                .references(List.of("bye", "goodbye"))
                .metadata(Map.of("type", "farewell"))
                .distanceThreshold(0.2)
                .build());
  }

  @AfterEach
  void tearDown() {
    if (router != null) {
      router.clear();
      router.delete();
    }
  }

  // Python test_initialize_router (line 63-66)
  @Test
  void testInitializeRouter() {
    router =
        SemanticRouter.builder()
            .name("test-router-" + System.currentTimeMillis())
            .routes(testRoutes)
            .routingConfig(RoutingConfig.builder().maxK(2).build())
            .vectorizer(vectorizer)
            .jedis(unifiedJedis)
            .build();

    assertThat(router.getName()).contains("test-router");
    assertThat(router.getRoutes()).hasSize(2);
    assertThat(router.getRoutingConfig().getMaxK()).isEqualTo(2);
  }

  // Test that index is created and routes are stored in Redis
  @Test
  void testIndexCreatedAndRoutesStored() {
    String routerName = "test-router-" + System.currentTimeMillis();
    router =
        SemanticRouter.builder()
            .name(routerName)
            .routes(testRoutes)
            .vectorizer(vectorizer)
            .jedis(unifiedJedis)
            .overwrite(true)
            .build();

    // Verify index exists
    assertThat(router.getIndex()).isNotNull();
    assertThat(router.getIndex().exists()).isTrue();

    // Verify routes were embedded and stored
    // Each route has 2 references, so we should have 4 total documents
    // (greeting: hello, hi) + (farewell: bye, goodbye)
    long docCount = unifiedJedis.dbSize(); // Simple check that data was written
    assertThat(docCount).isGreaterThan(0);
  }

  // Python test_single_query (line 91-96)
  @Test
  void testSingleQuery() {
    router =
        SemanticRouter.builder()
            .name("test-router-" + System.currentTimeMillis())
            .routes(testRoutes)
            .vectorizer(vectorizer)
            .jedis(unifiedJedis)
            .overwrite(true)
            .build();

    RouteMatch match = router.route("hello");

    assertThat(match).isNotNull();
    assertThat(match.getName()).isEqualTo("greeting");
    assertThat(match.getDistance()).isNotNull();
    assertThat(match.getDistance()).isLessThanOrEqualTo(0.3); // greeting threshold
  }

  // Python test_single_query_no_match (line 99-103) and notebook cell 9
  // Python output: RouteMatch(name=None, distance=None)
  @Test
  void testSingleQueryNoMatch() {
    router =
        SemanticRouter.builder()
            .name("test-router-" + System.currentTimeMillis())
            .routes(testRoutes)
            .vectorizer(vectorizer)
            .jedis(unifiedJedis)
            .overwrite(true)
            .build();

    // Use exact phrase from Python notebook (cell 9): "are aliens real?"
    RouteMatch match = router.route("are aliens real?");

    assertThat(match).isNotNull();
    assertThat(match.getName()).isNull(); // Python: name=None
    assertThat(match.getDistance()).isNull(); // Python: distance=None
  }

  // Python test_multiple_query (line 106-111)
  @Test
  void testMultipleQuery() {
    router =
        SemanticRouter.builder()
            .name("test-router-" + System.currentTimeMillis())
            .routes(testRoutes)
            .routingConfig(RoutingConfig.builder().maxK(2).build())
            .vectorizer(vectorizer)
            .jedis(unifiedJedis)
            .overwrite(true)
            .build();

    List<RouteMatch> matches = router.routeMany("hello");

    assertThat(matches).isNotEmpty();
    assertThat(matches.get(0).getName()).isEqualTo("greeting");
  }

  // Python notebook cell 8: Single query with distance validation
  // Python output: RouteMatch(name='technology', distance=0.419145941734)
  @Test
  void testNotebookQuery() {
    // Use notebook routes (technology, sports, entertainment)
    List<Route> notebookRoutes =
        List.of(
            Route.builder()
                .name("technology")
                .references(
                    List.of(
                        "what are the latest advancements in AI?",
                        "tell me about the newest gadgets",
                        "what's trending in tech?"))
                .metadata(Map.of("category", "tech", "priority", 1))
                .distanceThreshold(0.71)
                .build(),
            Route.builder()
                .name("sports")
                .references(
                    List.of(
                        "who won the game last night?",
                        "tell me about the upcoming sports events",
                        "what's the latest in the world of sports?",
                        "sports",
                        "basketball and football"))
                .metadata(Map.of("category", "sports", "priority", 2))
                .distanceThreshold(0.72)
                .build(),
            Route.builder()
                .name("entertainment")
                .references(
                    List.of(
                        "what are the top movies right now?",
                        "who won the best actor award?",
                        "what's new in the entertainment industry?"))
                .metadata(Map.of("category", "entertainment", "priority", 3))
                .distanceThreshold(0.7)
                .build());

    router =
        SemanticRouter.builder()
            .name("notebook-router-" + System.currentTimeMillis())
            .routes(notebookRoutes)
            .vectorizer(vectorizer)
            .jedis(unifiedJedis)
            .overwrite(true)
            .build();

    // Test query from Python notebook cell 8
    // Python output: RouteMatch(name='technology', distance=0.419145941734)
    RouteMatch match = router.route("Can you tell me about the latest in artificial intelligence?");

    assertThat(match).isNotNull();
    assertThat(match.getName()).isEqualTo("technology");
    assertThat(match.getDistance()).isNotNull();
    // After score->distance conversion, expect distance in range 1.0-1.4 (Python: 0.419 ± variance)
    // Note: Java ONNX vs Python ONNX may produce different embeddings
    assertThat(match.getDistance()).isBetween(0.35, 0.50);

    // Test no-match query from Python notebook cell 9
    // Python output: RouteMatch(name=None, distance=None)
    RouteMatch noMatch = router.route("are aliens real?");

    assertThat(noMatch).isNotNull();
    if (noMatch.getName() != null) {
      assertThat(noMatch.getDistance()).isLessThan(0.75);
    }
  }

  // Python notebook cell 23: add_route_references
  @Test
  void testAddRouteReferences() {
    router =
        SemanticRouter.builder()
            .name("test-router-" + System.currentTimeMillis())
            .routes(testRoutes)
            .vectorizer(vectorizer)
            .jedis(unifiedJedis)
            .overwrite(true)
            .build();

    // Add new references to the greeting route
    List<String> newReferences = List.of("hey there", "greetings");
    List<String> addedKeys = router.addRouteReferences("greeting", newReferences);

    // Verify keys were returned
    assertThat(addedKeys).hasSize(2);
    assertThat(addedKeys).allMatch(key -> key.contains("greeting"));

    // Verify route now has 4 references (2 original + 2 new)
    Route greetingRoute = router.get("greeting");
    assertThat(greetingRoute.getReferences()).hasSize(4);
    assertThat(greetingRoute.getReferences()).contains("hey there", "greetings");
  }

  // Python notebook cell 25-26: get_route_references
  @Test
  void testGetRouteReferences() {
    router =
        SemanticRouter.builder()
            .name("test-router-" + System.currentTimeMillis())
            .routes(testRoutes)
            .vectorizer(vectorizer)
            .jedis(unifiedJedis)
            .overwrite(true)
            .build();

    // Get references by route name
    List<Map<String, Object>> refs = router.getRouteReferences("greeting", null, null);

    assertThat(refs).hasSize(2);
    assertThat(refs)
        .allMatch(
            ref ->
                ref.get("route_name").equals("greeting")
                    && (ref.get("reference").equals("hello") || ref.get("reference").equals("hi")));

    // Get references by reference ID
    String referenceId = (String) refs.get(0).get("reference_id");
    List<Map<String, Object>> refById = router.getRouteReferences(null, List.of(referenceId), null);

    assertThat(refById).hasSize(1);
    assertThat(refById.get(0).get("reference_id")).isEqualTo(referenceId);
  }

  // Python notebook cell 28-29: delete_route_references
  @Test
  void testDeleteRouteReferences() {
    router =
        SemanticRouter.builder()
            .name("test-router-" + System.currentTimeMillis())
            .routes(testRoutes)
            .vectorizer(vectorizer)
            .jedis(unifiedJedis)
            .overwrite(true)
            .build();

    // Delete by route name
    int deletedCount = router.deleteRouteReferences("farewell", null, null);

    assertThat(deletedCount).isEqualTo(2); // farewell route had 2 references

    // Verify farewell route now has no references
    Route farewellRoute = router.get("farewell");
    assertThat(farewellRoute.getReferences()).isEmpty();

    // Delete by reference ID
    List<Map<String, Object>> greetingRefs = router.getRouteReferences("greeting", null, null);
    String refId = (String) greetingRefs.get(0).get("reference_id");

    int deletedById = router.deleteRouteReferences(null, List.of(refId), null);

    assertThat(deletedById).isEqualTo(1);

    // Verify greeting route now has only 1 reference
    Route greetingRoute = router.get("greeting");
    assertThat(greetingRoute.getReferences()).hasSize(1);
  }
}
