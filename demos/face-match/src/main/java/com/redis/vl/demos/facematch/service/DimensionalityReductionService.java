package com.redis.vl.demos.facematch.service;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.demos.facematch.model.Celebrity3D;
import java.util.ArrayList;
import java.util.List;
import smile.manifold.TSNE;

/**
 * Service for reducing high-dimensional face embeddings to 3D for visualization using t-SNE. t-SNE
 * (t-Distributed Stochastic Neighbor Embedding) preserves local neighborhoods, meaning similar
 * faces will cluster together in the 3D visualization.
 */
public class DimensionalityReductionService {

  /**
   * Reduce celebrity embeddings from 512D to 3D using t-SNE. t-SNE preserves local neighborhoods,
   * so similar faces will be physically close in the 3D space.
   *
   * @param celebrities List of celebrities with 512-dim embeddings
   * @return List of celebrities with 3D coordinates
   */
  public List<Celebrity3D> reduceTo3D(List<Celebrity> celebrities) {
    return reduceTo3DTSNE(celebrities);
  }

  /**
   * Reduce celebrity embeddings from 512D to 3D using t-SNE. t-SNE (t-Distributed Stochastic
   * Neighbor Embedding) preserves local structure, meaning similar faces in high-dimensional space
   * will appear physically close in 3D.
   *
   * @param celebrities List of celebrities with 512-dim embeddings
   * @return List of celebrities with 3D coordinates
   */
  private List<Celebrity3D> reduceTo3DTSNE(List<Celebrity> celebrities) {
    if (celebrities.isEmpty()) {
      return new ArrayList<>();
    }

    int numSamples = celebrities.size();
    int numFeatures = 512;

    // Convert embeddings to double[][] matrix for t-SNE
    double[][] dataMatrix = new double[numSamples][numFeatures];
    for (int i = 0; i < numSamples; i++) {
      float[] embedding = celebrities.get(i).getEmbedding();
      for (int j = 0; j < numFeatures; j++) {
        dataMatrix[i][j] = embedding[j];
      }
    }

    System.out.println("Running t-SNE dimensionality reduction (this may take a minute)...");

    // Run t-SNE: 3D output with scale-aware perplexity
    // Perplexity should scale with dataset size for optimal results
    // Small datasets (50-100): perplexity 5-50
    // Medium datasets (1000-5000): perplexity 50-100
    // Large datasets (10000+): perplexity 100-300
    double perplexity =
        Math.max(2.0, Math.min(50.0 + Math.log10(numSamples) * 50.0, Math.min(numSamples - 1, 300.0)));
    System.out.println(
        String.format("t-SNE parameters: samples=%d, perplexity=%.1f", numSamples, perplexity));

    // Create t-SNE instance and run
    // TSNE(data, dimensions, perplexity, iterations, momentum)
    TSNE tsne = new TSNE(dataMatrix, 3, perplexity, 1000, 1);
    double[][] tsneResult = tsne.coordinates;

    // Create Celebrity3D objects with scale-aware coordinates
    // Apply NON-LINEAR REPULSION to fix t-SNE's dense core problem
    // t-SNE creates tight clusters in center, spread outliers on edges
    // Solution: push points away from center using power transformation
    List<Celebrity3D> result = new ArrayList<>();
    double baseScale = 6000.0;
    double scalingExponent = 1.0;
    double scale = baseScale * Math.pow(numSamples / 100.0, scalingExponent);

    // REPULSION TRANSFORMATION: spread dense core outward for better navigation
    // Points near center get pushed out more aggressively than edge points
    // Exponent < 1.0 pushes core OUT and pulls edges IN (redistributes density)
    // 0.75 provides moderate spreading - balances navigability with compactness
    double repulsionExponent = 0.75; // Moderate repulsion for balanced distribution
    System.out.println(
        String.format(
            "3D visualization: scale=%.1f, repulsion exponent=%.2f (spreads dense core)",
            scale, repulsionExponent));

    for (int i = 0; i < numSamples; i++) {
      // Get normalized t-SNE coordinates
      double x = tsneResult[i][0];
      double y = tsneResult[i][1];
      double z = tsneResult[i][2];

      // Calculate distance from center (origin)
      double distanceFromCenter = Math.sqrt(x * x + y * y + z * z);

      if (distanceFromCenter > 0) {
        // Apply repulsion: new_distance = distance^repulsionExponent
        // This pushes dense core outward more than sparse edges
        double newDistance = Math.pow(distanceFromCenter, repulsionExponent);

        // Scale factor based on repulsion (how much to push out)
        double repulsionFactor = newDistance / distanceFromCenter;

        // Apply repulsion + scale
        x = x * repulsionFactor * scale;
        y = y * repulsionFactor * scale;
        z = z * repulsionFactor * scale;
      } else {
        // Point exactly at origin (rare) - just apply scale
        x = x * scale;
        y = y * scale;
        z = z * scale;
      }

      result.add(new Celebrity3D(celebrities.get(i), x, y, z));
    }

    System.out.println("t-SNE complete!");
    return result;
  }
}
