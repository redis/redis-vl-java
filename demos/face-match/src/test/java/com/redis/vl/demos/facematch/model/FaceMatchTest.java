package com.redis.vl.demos.facematch.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FaceMatchTest {

    @Test
    void testCreateFaceMatch() {
        float[] embedding = new float[512];
        Celebrity celebrity = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);

        FaceMatch match = new FaceMatch(celebrity, 0.25, 1);

        assertThat(match.getCelebrity()).isEqualTo(celebrity);
        assertThat(match.getDistance()).isEqualTo(0.25);
        assertThat(match.getRank()).isEqualTo(1);
    }

    @Test
    void testNullCelebrityThrowsException() {
        assertThatThrownBy(() -> new FaceMatch(null, 0.25, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Celebrity cannot be null");
    }

    @Test
    void testNegativeDistanceThrowsException() {
        float[] embedding = new float[512];
        Celebrity celebrity = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);

        assertThatThrownBy(() -> new FaceMatch(celebrity, -0.1, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Distance cannot be negative");
    }

    @Test
    void testInvalidRankThrowsException() {
        float[] embedding = new float[512];
        Celebrity celebrity = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);

        assertThatThrownBy(() -> new FaceMatch(celebrity, 0.25, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Rank must be positive");
    }

    @Test
    void testCompareTo() {
        float[] embedding = new float[512];
        Celebrity celebrity1 = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);
        Celebrity celebrity2 = new Celebrity("456", "Tom Cruise", "http://example.com/image2.jpg", embedding);

        FaceMatch match1 = new FaceMatch(celebrity1, 0.1, 1);
        FaceMatch match2 = new FaceMatch(celebrity2, 0.2, 2);

        assertThat(match1.compareTo(match2)).isLessThan(0);
        assertThat(match2.compareTo(match1)).isGreaterThan(0);
    }

    @Test
    void testToString() {
        float[] embedding = new float[512];
        Celebrity celebrity = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);
        FaceMatch match = new FaceMatch(celebrity, 0.25, 1);

        String str = match.toString();
        assertThat(str).contains("Tom Hanks");
        assertThat(str).contains("0.25");
        assertThat(str).contains("1");
    }
}
