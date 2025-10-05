package com.redis.vl.extensions.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Test Route data class - ported from Python Route schema */
class RouteTest {

  @Test
  void testCreateRoute() {
    Route route =
        Route.builder()
            .name("greeting")
            .references(List.of("hello", "hi"))
            .metadata(Map.of("type", "greeting"))
            .distanceThreshold(0.3)
            .build();

    assertThat(route.getName()).isEqualTo("greeting");
    assertThat(route.getReferences()).containsExactly("hello", "hi");
    assertThat(route.getMetadata()).containsEntry("type", "greeting");
    assertThat(route.getDistanceThreshold()).isEqualTo(0.3);
  }

  @Test
  void testRouteDefaultDistanceThreshold() {
    Route route = Route.builder().name("test").references(List.of("test")).build();

    assertThat(route.getDistanceThreshold()).isEqualTo(0.5);
  }

  @Test
  void testRouteValidation_EmptyName() {
    assertThatThrownBy(
            () -> Route.builder().name("").references(List.of("test")).build().validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name must not be empty");
  }

  @Test
  void testRouteValidation_EmptyReferences() {
    assertThatThrownBy(() -> Route.builder().name("test").references(List.of()).build().validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("References must not be empty");
  }
}
