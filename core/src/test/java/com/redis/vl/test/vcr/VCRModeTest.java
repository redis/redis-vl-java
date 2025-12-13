package com.redis.vl.test.vcr;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for VCRMode enum - TDD RED phase. These tests will fail until we implement the VCRMode
 * enum.
 */
class VCRModeTest {

  @Test
  void shouldHavePlaybackMode() {
    assertThat(VCRMode.PLAYBACK).isNotNull();
    assertThat(VCRMode.PLAYBACK.name()).isEqualTo("PLAYBACK");
  }

  @Test
  void shouldHaveRecordMode() {
    assertThat(VCRMode.RECORD).isNotNull();
    assertThat(VCRMode.RECORD.name()).isEqualTo("RECORD");
  }

  @Test
  void shouldHaveRecordNewMode() {
    assertThat(VCRMode.RECORD_NEW).isNotNull();
    assertThat(VCRMode.RECORD_NEW.name()).isEqualTo("RECORD_NEW");
  }

  @Test
  void shouldHaveRecordFailedMode() {
    assertThat(VCRMode.RECORD_FAILED).isNotNull();
    assertThat(VCRMode.RECORD_FAILED.name()).isEqualTo("RECORD_FAILED");
  }

  @Test
  void shouldHavePlaybackOrRecordMode() {
    assertThat(VCRMode.PLAYBACK_OR_RECORD).isNotNull();
    assertThat(VCRMode.PLAYBACK_OR_RECORD.name()).isEqualTo("PLAYBACK_OR_RECORD");
  }

  @Test
  void shouldHaveOffMode() {
    assertThat(VCRMode.OFF).isNotNull();
    assertThat(VCRMode.OFF.name()).isEqualTo("OFF");
  }

  @Test
  void shouldHaveSixModes() {
    assertThat(VCRMode.values()).hasSize(6);
  }

  @Test
  void isRecordModeShouldReturnTrueForRecordModes() {
    assertThat(VCRMode.RECORD.isRecordMode()).isTrue();
    assertThat(VCRMode.RECORD_NEW.isRecordMode()).isTrue();
    assertThat(VCRMode.RECORD_FAILED.isRecordMode()).isTrue();
    assertThat(VCRMode.PLAYBACK_OR_RECORD.isRecordMode()).isTrue();
  }

  @Test
  void isRecordModeShouldReturnFalseForNonRecordModes() {
    assertThat(VCRMode.PLAYBACK.isRecordMode()).isFalse();
    assertThat(VCRMode.OFF.isRecordMode()).isFalse();
  }

  @Test
  void isPlaybackModeShouldReturnTrueForPlaybackModes() {
    assertThat(VCRMode.PLAYBACK.isPlaybackMode()).isTrue();
    assertThat(VCRMode.PLAYBACK_OR_RECORD.isPlaybackMode()).isTrue();
  }

  @Test
  void isPlaybackModeShouldReturnFalseForNonPlaybackModes() {
    assertThat(VCRMode.RECORD.isPlaybackMode()).isFalse();
    assertThat(VCRMode.RECORD_NEW.isPlaybackMode()).isFalse();
    assertThat(VCRMode.RECORD_FAILED.isPlaybackMode()).isFalse();
    assertThat(VCRMode.OFF.isPlaybackMode()).isFalse();
  }
}
