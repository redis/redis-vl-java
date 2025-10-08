package com.redis.vl.demos.facematch.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CelebrityTest {

    @Test
    void testCreateCelebrity() {
        float[] embedding = new float[512];
        for (int i = 0; i < 512; i++) {
            embedding[i] = (float) Math.random();
        }

        Celebrity celebrity = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);

        assertThat(celebrity.getId()).isEqualTo("123");
        assertThat(celebrity.getName()).isEqualTo("Tom Hanks");
        assertThat(celebrity.getImageUrl()).isEqualTo("http://example.com/image.jpg");
        assertThat(celebrity.getEmbedding()).hasSize(512);
    }

    @Test
    void testEmbeddingDefensiveCopy() {
        float[] embedding = new float[512];
        embedding[0] = 1.0f;

        Celebrity celebrity = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);

        // Modify original array
        embedding[0] = 999.0f;

        // Celebrity's embedding should be unchanged
        assertThat(celebrity.getEmbedding()[0]).isEqualTo(1.0f);
    }

    @Test
    void testGetEmbeddingReturnsDefensiveCopy() {
        float[] embedding = new float[512];
        embedding[0] = 1.0f;

        Celebrity celebrity = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);

        float[] retrieved = celebrity.getEmbedding();
        retrieved[0] = 999.0f;

        // Celebrity's internal embedding should be unchanged
        assertThat(celebrity.getEmbedding()[0]).isEqualTo(1.0f);
    }

    @Test
    void testNullIdThrowsException() {
        float[] embedding = new float[512];
        assertThatThrownBy(() -> new Celebrity(null, "Tom Hanks", "http://example.com/image.jpg", embedding))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ID cannot be null");
    }

    @Test
    void testEmptyIdThrowsException() {
        float[] embedding = new float[512];
        assertThatThrownBy(() -> new Celebrity("", "Tom Hanks", "http://example.com/image.jpg", embedding))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ID cannot be empty");
    }

    @Test
    void testNullNameThrowsException() {
        float[] embedding = new float[512];
        assertThatThrownBy(() -> new Celebrity("123", null, "http://example.com/image.jpg", embedding))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Name cannot be null");
    }

    @Test
    void testEmptyNameThrowsException() {
        float[] embedding = new float[512];
        assertThatThrownBy(() -> new Celebrity("123", "", "http://example.com/image.jpg", embedding))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Name cannot be empty");
    }

    @Test
    void testNullEmbeddingThrowsException() {
        assertThatThrownBy(() -> new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Embedding cannot be null");
    }

    @Test
    void testWrongEmbeddingDimensionThrowsException() {
        float[] embedding = new float[256]; // Wrong dimension
        assertThatThrownBy(() -> new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Embedding must be 512-dimensional");
    }

    @Test
    void testEquality() {
        float[] embedding1 = new float[512];
        float[] embedding2 = new float[512];
        for (int i = 0; i < 512; i++) {
            embedding1[i] = (float) i;
            embedding2[i] = (float) i;
        }

        Celebrity celeb1 = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding1);
        Celebrity celeb2 = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding2);

        assertThat(celeb1).isEqualTo(celeb2);
        assertThat(celeb1.hashCode()).isEqualTo(celeb2.hashCode());
    }

    @Test
    void testInequality() {
        float[] embedding = new float[512];
        Celebrity celeb1 = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);
        Celebrity celeb2 = new Celebrity("456", "Tom Cruise", "http://example.com/image2.jpg", embedding);

        assertThat(celeb1).isNotEqualTo(celeb2);
    }

    @Test
    void testToString() {
        float[] embedding = new float[512];
        Celebrity celebrity = new Celebrity("123", "Tom Hanks", "http://example.com/image.jpg", embedding);

        String str = celebrity.toString();
        assertThat(str).contains("123");
        assertThat(str).contains("Tom Hanks");
    }
}
