package com.redis.vl.extensions.router;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Test SemanticRouter - ported from Python test_semantic_router.py */
class SemanticRouterTest {

  @Test
  void testCreateRouterWithName() {
    SemanticRouter router = new SemanticRouter("test-router");

    assertThat(router.getName()).isEqualTo("test-router");
  }

  // Python test_initialize_router (line 63-66)
  @Test
  void testInitializeRouter() {
    List<Route> routes =
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

    RoutingConfig config = RoutingConfig.builder().maxK(2).build();

    SemanticRouter router =
        SemanticRouter.builder().name("test-router").routes(routes).routingConfig(config).build();

    assertThat(router.getName()).isEqualTo("test-router");
    assertThat(router.getRoutes()).hasSize(2);
    assertThat(router.getRoutingConfig().getMaxK()).isEqualTo(2);
  }

  // Python test_router_properties (line 69-76)
  @Test
  void testRouterProperties() {
    List<Route> routes =
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

    SemanticRouter router = SemanticRouter.builder().name("test-router").routes(routes).build();

    List<String> routeNames = router.getRouteNames();
    assertThat(routeNames).contains("greeting", "farewell");

    Map<String, Double> thresholds = router.getRouteThresholds();
    assertThat(thresholds.get("greeting")).isEqualTo(0.3);
    assertThat(thresholds.get("farewell")).isEqualTo(0.2);
  }

  // Python test_get_route (line 79-83)
  @Test
  void testGetRoute() {
    List<Route> routes =
        List.of(Route.builder().name("greeting").references(List.of("hello", "hi")).build());

    SemanticRouter router = SemanticRouter.builder().name("test-router").routes(routes).build();

    Route route = router.get("greeting");
    assertThat(route).isNotNull();
    assertThat(route.getName()).isEqualTo("greeting");
    assertThat(route.getReferences()).contains("hello");
  }

  // Python test_get_non_existing_route (line 86-88)
  @Test
  void testGetNonExistingRoute() {
    SemanticRouter router = SemanticRouter.builder().name("test-router").routes(List.of()).build();

    Route route = router.get("non_existent_route");
    assertThat(route).isNull();
  }

  // Python test_update_routing_config (line 114-121)
  @Test
  void testUpdateRoutingConfig() {
    SemanticRouter router = SemanticRouter.builder().name("test-router").routes(List.of()).build();

    RoutingConfig newConfig =
        RoutingConfig.builder().maxK(27).aggregationMethod(DistanceAggregationMethod.MIN).build();

    router.updateRoutingConfig(newConfig);

    assertThat(router.getRoutingConfig().getMaxK()).isEqualTo(27);
    assertThat(router.getRoutingConfig().getAggregationMethod())
        .isEqualTo(DistanceAggregationMethod.MIN);
  }

  // Python test_to_dict (line 177-181) - simplified for current implementation
  @Test
  void testToMap() {
    List<Route> routes =
        List.of(Route.builder().name("greeting").references(List.of("hello", "hi")).build());

    SemanticRouter router = SemanticRouter.builder().name("test-router").routes(routes).build();

    Map<String, Object> routerMap = router.toMap();

    assertThat(routerMap.get("name")).isEqualTo("test-router");
    assertThat((List<?>) routerMap.get("routes")).hasSize(1);
    assertThat(routerMap.get("routingConfig")).isNotNull();
  }
}
