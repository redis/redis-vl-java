package com.redis.vl.test.vcr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for VCRRegistry with null Redis (local cache only mode). */
class VCRRegistryTest {

  private VCRRegistry registry;

  @BeforeEach
  void setUp() {
    // Use null jedis for local-only mode (no Redis required for unit tests)
    registry = new VCRRegistry(null);
  }

  @Test
  void shouldReturnMissingStatusForUnknownTest() {
    VCRRegistry.RecordingStatus status = registry.getTestStatus("unknown:test");
    assertThat(status).isEqualTo(VCRRegistry.RecordingStatus.MISSING);
  }

  @Test
  void shouldRegisterSuccessfulTest() {
    String testId = "MyTest:testMethod";
    List<String> cassettes = List.of("vcr:llm:MyTest:testMethod:0001");

    registry.registerSuccess(testId, cassettes);

    assertThat(registry.getTestStatus(testId)).isEqualTo(VCRRegistry.RecordingStatus.RECORDED);
  }

  @Test
  void shouldRegisterFailedTest() {
    String testId = "MyTest:failingMethod";

    registry.registerFailure(testId, "Test failed with NPE");

    assertThat(registry.getTestStatus(testId)).isEqualTo(VCRRegistry.RecordingStatus.FAILED);
  }

  @Test
  void shouldTrackAllRecordedTests() {
    registry.registerSuccess("Test1:method1", List.of());
    registry.registerSuccess("Test2:method2", List.of());
    registry.registerFailure("Test3:method3", "error");

    assertThat(registry.getAllRecordedTests())
        .containsExactlyInAnyOrder("Test1:method1", "Test2:method2", "Test3:method3");
  }

  // Tests for determineEffectiveMode

  @Test
  void recordNewShouldRecordMissingTests() {
    VCRMode effective = registry.determineEffectiveMode("missing:test", VCRMode.RECORD_NEW);
    assertThat(effective).isEqualTo(VCRMode.RECORD);
  }

  @Test
  void recordNewShouldPlaybackRecordedTests() {
    registry.registerSuccess("recorded:test", List.of());

    VCRMode effective = registry.determineEffectiveMode("recorded:test", VCRMode.RECORD_NEW);
    assertThat(effective).isEqualTo(VCRMode.PLAYBACK);
  }

  @Test
  void recordFailedShouldRecordFailedTests() {
    registry.registerFailure("failed:test", "error");

    VCRMode effective = registry.determineEffectiveMode("failed:test", VCRMode.RECORD_FAILED);
    assertThat(effective).isEqualTo(VCRMode.RECORD);
  }

  @Test
  void recordFailedShouldRecordMissingTests() {
    VCRMode effective = registry.determineEffectiveMode("missing:test", VCRMode.RECORD_FAILED);
    assertThat(effective).isEqualTo(VCRMode.RECORD);
  }

  @Test
  void recordFailedShouldPlaybackRecordedTests() {
    registry.registerSuccess("recorded:test", List.of());

    VCRMode effective = registry.determineEffectiveMode("recorded:test", VCRMode.RECORD_FAILED);
    assertThat(effective).isEqualTo(VCRMode.PLAYBACK);
  }

  @Test
  void playbackOrRecordShouldPlaybackRecordedTests() {
    registry.registerSuccess("recorded:test", List.of());

    VCRMode effective =
        registry.determineEffectiveMode("recorded:test", VCRMode.PLAYBACK_OR_RECORD);
    assertThat(effective).isEqualTo(VCRMode.PLAYBACK);
  }

  @Test
  void playbackOrRecordShouldRecordMissingTests() {
    VCRMode effective = registry.determineEffectiveMode("missing:test", VCRMode.PLAYBACK_OR_RECORD);
    assertThat(effective).isEqualTo(VCRMode.RECORD);
  }

  @Test
  void playbackModeShouldAlwaysReturnPlayback() {
    VCRMode effective = registry.determineEffectiveMode("any:test", VCRMode.PLAYBACK);
    assertThat(effective).isEqualTo(VCRMode.PLAYBACK);
  }

  @Test
  void recordModeShouldAlwaysReturnRecord() {
    registry.registerSuccess("recorded:test", List.of());

    VCRMode effective = registry.determineEffectiveMode("recorded:test", VCRMode.RECORD);
    assertThat(effective).isEqualTo(VCRMode.RECORD);
  }

  @Test
  void offModeShouldAlwaysReturnOff() {
    VCRMode effective = registry.determineEffectiveMode("any:test", VCRMode.OFF);
    assertThat(effective).isEqualTo(VCRMode.OFF);
  }
}
