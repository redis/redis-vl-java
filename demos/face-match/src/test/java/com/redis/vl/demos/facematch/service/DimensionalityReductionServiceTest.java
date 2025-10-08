package com.redis.vl.demos.facematch.service;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.demos.facematch.model.Celebrity3D;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DimensionalityReductionServiceTest {

    @Test
    void testReduceTo3D() {
        DataLoaderService dataLoader = new DataLoaderService();
        DimensionalityReductionService reducer = new DimensionalityReductionService();

        // Generate sample celebrities
        List<Celebrity> celebrities = dataLoader.generateSampleCelebrities(20);

        // Reduce to 3D
        List<Celebrity3D> reduced = reducer.reduceTo3D(celebrities);

        // Verify we got all celebrities back
        assertThat(reduced).hasSize(20);

        // Verify all have valid 3D coordinates
        for (Celebrity3D celeb3D : reduced) {
            assertThat(celeb3D.getCelebrity()).isNotNull();
            assertThat(celeb3D.getX()).isFinite();
            assertThat(celeb3D.getY()).isFinite();
            assertThat(celeb3D.getZ()).isFinite();
        }
    }

    @Test
    void testReduced3DSpaceHasVariation() {
        DataLoaderService dataLoader = new DataLoaderService();
        DimensionalityReductionService reducer = new DimensionalityReductionService();

        List<Celebrity> celebrities = dataLoader.generateSampleCelebrities(10);
        List<Celebrity3D> reduced = reducer.reduceTo3D(celebrities);

        // Calculate variance in each dimension
        double sumX = 0, sumY = 0, sumZ = 0;
        for (Celebrity3D c : reduced) {
            sumX += c.getX();
            sumY += c.getY();
            sumZ += c.getZ();
        }
        double meanX = sumX / reduced.size();
        double meanY = sumY / reduced.size();
        double meanZ = sumZ / reduced.size();

        double varianceX = 0, varianceY = 0, varianceZ = 0;
        for (Celebrity3D c : reduced) {
            varianceX += Math.pow(c.getX() - meanX, 2);
            varianceY += Math.pow(c.getY() - meanY, 2);
            varianceZ += Math.pow(c.getZ() - meanZ, 2);
        }
        varianceX /= reduced.size();
        varianceY /= reduced.size();
        varianceZ /= reduced.size();

        // All dimensions should have some variation
        assertThat(varianceX).isGreaterThan(0.01);
        assertThat(varianceY).isGreaterThan(0.01);
        assertThat(varianceZ).isGreaterThan(0.01);
    }

    @Test
    void testCelebritiesArePreserved() {
        DataLoaderService dataLoader = new DataLoaderService();
        DimensionalityReductionService reducer = new DimensionalityReductionService();

        List<Celebrity> celebrities = dataLoader.generateSampleCelebrities(5);
        List<Celebrity3D> reduced = reducer.reduceTo3D(celebrities);

        // Verify celebrity objects are preserved
        for (int i = 0; i < celebrities.size(); i++) {
            Celebrity original = celebrities.get(i);
            Celebrity preserved = reduced.get(i).getCelebrity();

            assertThat(preserved.getId()).isEqualTo(original.getId());
            assertThat(preserved.getName()).isEqualTo(original.getName());
        }
    }
}
