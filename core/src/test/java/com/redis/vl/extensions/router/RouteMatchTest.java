package com.redis.vl.extensions.router;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Test RouteMatch data class - ported from Python RouteMatch schema */
class RouteMatchTest {

  @Test
  void testCreateRouteMatch() {
    RouteMatch match = RouteMatch.builder().name("greeting").distance(0.25).build();

    assertThat(match.getName()).isEqualTo("greeting");
    assertThat(match.getDistance()).isEqualTo(0.25);
  }

  @Test
  void testRouteMatchDefaults() {
    RouteMatch match = RouteMatch.builder().build();

    assertThat(match.getName()).isNull();
    assertThat(match.getDistance()).isNull();
  }
}
