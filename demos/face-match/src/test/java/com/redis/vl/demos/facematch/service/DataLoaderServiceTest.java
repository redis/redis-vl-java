package com.redis.vl.demos.facematch.service;

import com.redis.vl.demos.facematch.model.Celebrity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DataLoaderServiceTest {

    @Test
    void testGenerateSampleCelebrities() {
        DataLoaderService service = new DataLoaderService();

        List<Celebrity> celebrities = service.generateSampleCelebrities(10);

        assertThat(celebrities).hasSize(10);

        // Verify all have valid data
        for (Celebrity celeb : celebrities) {
            assertThat(celeb.getId()).isNotNull();
            assertThat(celeb.getName()).isNotEmpty();
            assertThat(celeb.getImageUrl()).startsWith("http");
            assertThat(celeb.getEmbedding()).hasSize(512);
        }
    }

    @Test
    void testEmbeddingsAreNormalized() {
        DataLoaderService service = new DataLoaderService();

        List<Celebrity> celebrities = service.generateSampleCelebrities(5);

        for (Celebrity celeb : celebrities) {
            float[] embedding = celeb.getEmbedding();

            // Calculate L2 norm
            double sum = 0.0;
            for (float v : embedding) {
                sum += v * v;
            }
            double norm = Math.sqrt(sum);

            // Should be approximately 1.0 (normalized)
            assertThat(norm).isBetween(0.99, 1.01);
        }
    }

    @Test
    void testEmbeddingsHaveVariation() {
        DataLoaderService service = new DataLoaderService();

        List<Celebrity> celebrities = service.generateSampleCelebrities(3);

        // Different celebrities should have different embeddings
        float[] emb1 = celebrities.get(0).getEmbedding();
        float[] emb2 = celebrities.get(1).getEmbedding();

        boolean different = false;
        for (int i = 0; i < emb1.length; i++) {
            if (Math.abs(emb1[i] - emb2[i]) > 0.01) {
                different = true;
                break;
            }
        }

        assertThat(different).isTrue();
    }
}
