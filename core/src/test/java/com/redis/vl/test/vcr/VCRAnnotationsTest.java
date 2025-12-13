package com.redis.vl.test.vcr;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Tests for VCR annotations - TDD RED phase. These tests will fail until we implement the
 * annotations.
 */
class VCRAnnotationsTest {

  @Test
  void vcrTestAnnotationShouldExist() {
    assertThat(VCRTest.class).isAnnotation();
  }

  @Test
  void vcrTestShouldHaveRuntimeRetention() {
    Retention retention = VCRTest.class.getAnnotation(Retention.class);
    assertThat(retention).isNotNull();
    assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
  }

  @Test
  void vcrTestShouldTargetTypes() {
    Target target = VCRTest.class.getAnnotation(Target.class);
    assertThat(target).isNotNull();
    assertThat(target.value()).contains(ElementType.TYPE);
  }

  @Test
  void vcrTestShouldHaveModeAttribute() throws NoSuchMethodException {
    var method = VCRTest.class.getMethod("mode");
    assertThat(method.getReturnType()).isEqualTo(VCRMode.class);
  }

  @Test
  void vcrTestShouldHaveDefaultModeOfPlaybackOrRecord() throws NoSuchMethodException {
    var method = VCRTest.class.getMethod("mode");
    VCRMode defaultValue = (VCRMode) method.getDefaultValue();
    assertThat(defaultValue).isEqualTo(VCRMode.PLAYBACK_OR_RECORD);
  }

  @Test
  void vcrTestShouldHaveDataDirAttribute() throws NoSuchMethodException {
    var method = VCRTest.class.getMethod("dataDir");
    assertThat(method.getReturnType()).isEqualTo(String.class);
  }

  @Test
  void vcrTestShouldHaveDefaultDataDir() throws NoSuchMethodException {
    var method = VCRTest.class.getMethod("dataDir");
    String defaultValue = (String) method.getDefaultValue();
    assertThat(defaultValue).isEqualTo("src/test/resources/vcr-data");
  }

  @Test
  void vcrTestShouldHaveRedisImageAttribute() throws NoSuchMethodException {
    var method = VCRTest.class.getMethod("redisImage");
    assertThat(method.getReturnType()).isEqualTo(String.class);
  }

  @Test
  void vcrTestShouldHaveDefaultRedisImage() throws NoSuchMethodException {
    var method = VCRTest.class.getMethod("redisImage");
    String defaultValue = (String) method.getDefaultValue();
    assertThat(defaultValue).isEqualTo("redis/redis-stack:latest");
  }

  @Test
  void vcrTestShouldBeExtendedWithVCRExtension() {
    ExtendWith extendWith = VCRTest.class.getAnnotation(ExtendWith.class);
    assertThat(extendWith).isNotNull();
    assertThat(extendWith.value()).contains(VCRExtension.class);
  }

  @Test
  void vcrRecordAnnotationShouldExist() {
    assertThat(VCRRecord.class).isAnnotation();
  }

  @Test
  void vcrRecordShouldTargetMethods() {
    Target target = VCRRecord.class.getAnnotation(Target.class);
    assertThat(target).isNotNull();
    assertThat(target.value()).contains(ElementType.METHOD);
  }

  @Test
  void vcrDisabledAnnotationShouldExist() {
    assertThat(VCRDisabled.class).isAnnotation();
  }

  @Test
  void vcrDisabledShouldTargetMethods() {
    Target target = VCRDisabled.class.getAnnotation(Target.class);
    assertThat(target).isNotNull();
    assertThat(target.value()).contains(ElementType.METHOD);
  }
}
