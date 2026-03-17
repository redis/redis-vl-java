package com.redis.vl.demos.facematch.model;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class Celebrity3DTest {

  @Test
  void testCreateCelebrity3D() {
    float[] embedding = new float[512];
    Celebrity celebrity =
        new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);

    Celebrity3D celeb3D = new Celebrity3D(celebrity, 1.0, 2.0, 3.0);

    assertThat(celeb3D.getCelebrity()).isEqualTo(celebrity);
    assertThat(celeb3D.getX()).isEqualTo(1.0);
    assertThat(celeb3D.getY()).isEqualTo(2.0);
    assertThat(celeb3D.getZ()).isEqualTo(3.0);
  }

  @Test
  void testNullCelebrityThrowsException() {
    assertThatThrownBy(() -> new Celebrity3D(null, 1.0, 2.0, 3.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Celebrity cannot be null");
  }

  @Test
  void testEquality() {
    float[] embedding = new float[512];
    Celebrity celebrity =
        new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);

    Celebrity3D celeb1 = new Celebrity3D(celebrity, 1.0, 2.0, 3.0);
    Celebrity3D celeb2 = new Celebrity3D(celebrity, 1.0, 2.0, 3.0);

    assertThat(celeb1).isEqualTo(celeb2);
    assertThat(celeb1.hashCode()).isEqualTo(celeb2.hashCode());
  }

  @Test
  void testInequality() {
    float[] embedding = new float[512];
    Celebrity celebrity1 =
        new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);
    Celebrity celebrity2 =
        new Celebrity("456", "Tom Cruise", "http://example.com/image2.jpg", embedding);

    Celebrity3D celeb1 = new Celebrity3D(celebrity1, 1.0, 2.0, 3.0);
    Celebrity3D celeb2 = new Celebrity3D(celebrity2, 1.0, 2.0, 3.0);

    assertThat(celeb1).isNotEqualTo(celeb2);
  }

  @Test
  void testToString() {
    float[] embedding = new float[512];
    Celebrity celebrity =
        new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);
    Celebrity3D celeb3D = new Celebrity3D(celebrity, 1.5, 2.5, 3.5);

    String str = celeb3D.toString();
    assertThat(str).contains("Tom Hanks");
    assertThat(str).contains("1.5");
    assertThat(str).contains("2.5");
    assertThat(str).contains("3.5");
  }
}
