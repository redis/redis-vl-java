package com.redis.vl.extensions.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Test RoutingConfig data class */
class RoutingConfigTest {

  @Test
  void testCreateRoutingConfig() {
    RoutingConfig config =
        RoutingConfig.builder().maxK(5).aggregationMethod(DistanceAggregationMethod.MIN).build();

    assertThat(config.getMaxK()).isEqualTo(5);
    assertThat(config.getAggregationMethod()).isEqualTo(DistanceAggregationMethod.MIN);
  }

  @Test
  void testRoutingConfigDefaults() {
    RoutingConfig config = RoutingConfig.builder().build();

    assertThat(config.getMaxK()).isEqualTo(1);
    assertThat(config.getAggregationMethod()).isEqualTo(DistanceAggregationMethod.AVG);
  }

  @Test
  void testRoutingConfigValidation_InvalidMaxK() {
    assertThatThrownBy(() -> RoutingConfig.builder().maxK(0).build().validate())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxK must be greater than 0");
  }
}
