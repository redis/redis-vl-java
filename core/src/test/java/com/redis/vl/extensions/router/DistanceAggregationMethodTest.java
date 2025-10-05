package com.redis.vl.extensions.router;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Test DistanceAggregationMethod enum */
class DistanceAggregationMethodTest {

  @Test
  void testEnumValues() {
    assertThat(DistanceAggregationMethod.AVG.getValue()).isEqualTo("avg");
    assertThat(DistanceAggregationMethod.MIN.getValue()).isEqualTo("min");
    assertThat(DistanceAggregationMethod.SUM.getValue()).isEqualTo("sum");
  }

  @Test
  void testFromValue() {
    assertThat(DistanceAggregationMethod.fromValue("avg")).isEqualTo(DistanceAggregationMethod.AVG);
    assertThat(DistanceAggregationMethod.fromValue("min")).isEqualTo(DistanceAggregationMethod.MIN);
    assertThat(DistanceAggregationMethod.fromValue("sum")).isEqualTo(DistanceAggregationMethod.SUM);
  }
}
