package com.redis.vl.demos.facematch.service;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.demos.facematch.model.Celebrity3D;
import org.apache.commons.math3.linear.*;
import smile.manifold.TSNE;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for reducing high-dimensional embeddings to 3D for visualization.
 * Supports both PCA (fast, preserves global variance) and t-SNE (slower, preserves local neighborhoods).
 */
public class DimensionalityReductionService {

    public enum Method {
        PCA,    // Fast, preserves global structure
        TSNE    // Slower, preserves local neighborhoods and semantic similarity
    }

    /**
     * Reduce celebrity embeddings from 512D to 3D using t-SNE (default).
     * t-SNE preserves local neighborhoods, so similar faces will be physically close.
     *
     * @param celebrities List of celebrities with 512-dim embeddings
     * @return List of celebrities with 3D coordinates
     */
    public List<Celebrity3D> reduceTo3D(List<Celebrity> celebrities) {
        return reduceTo3D(celebrities, Method.TSNE);
    }

    /**
     * Reduce celebrity embeddings from 512D to 3D using specified method.
     *
     * @param celebrities List of celebrities with 512-dim embeddings
     * @param method Dimensionality reduction method (PCA or TSNE)
     * @return List of celebrities with 3D coordinates
     */
    public List<Celebrity3D> reduceTo3D(List<Celebrity> celebrities, Method method) {
        if (method == Method.TSNE) {
            return reduceTo3DTSNE(celebrities);
        } else {
            return reduceTo3DPCA(celebrities);
        }
    }

    /**
     * Reduce celebrity embeddings from 512D to 3D using t-SNE.
     * t-SNE (t-Distributed Stochastic Neighbor Embedding) preserves local structure,
     * meaning similar faces in high-dimensional space will appear physically close in 3D.
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

        // Run t-SNE: 3D output, perplexity=30 (good for ~100 samples)
        // Higher perplexity = more global structure preserved
        // Lower perplexity = more focus on local neighbors
        double perplexity = Math.min(30.0, (numSamples - 1) / 3.0);

        // Create t-SNE instance and run
        // TSNE(data, dimensions, perplexity, iterations, momentum)
        TSNE tsne = new TSNE(dataMatrix, 3, perplexity, 1000, 1);
        double[][] tsneResult = tsne.coordinates;

        // Create Celebrity3D objects with scaled coordinates
        List<Celebrity3D> result = new ArrayList<>();
        // Much larger scale to spread out the t-SNE clustering (t-SNE tends to bunch)
        double scale = 4000.0; // Increased dramatically to spread out the visualization

        for (int i = 0; i < numSamples; i++) {
            double x = tsneResult[i][0] * scale;
            double y = tsneResult[i][1] * scale;
            double z = tsneResult[i][2] * scale;
            result.add(new Celebrity3D(celebrities.get(i), x, y, z));
        }

        System.out.println("t-SNE complete!");
        return result;
    }

    /**
     * Reduce celebrity embeddings from 512D to 3D using PCA (via SVD).
     * SVD is more numerically stable than eigendecomposition for PCA.
     * PCA preserves global variance but NOT local similarity.
     *
     * @param celebrities List of celebrities with 512-dim embeddings
     * @return List of celebrities with 3D coordinates
     */
    private List<Celebrity3D> reduceTo3DPCA(List<Celebrity> celebrities) {
        if (celebrities.isEmpty()) {
            return new ArrayList<>();
        }

        // Convert embeddings to matrix (rows = samples, columns = features)
        int numSamples = celebrities.size();
        int numFeatures = 512;

        double[][] dataMatrix = new double[numSamples][numFeatures];
        for (int i = 0; i < numSamples; i++) {
            float[] embedding = celebrities.get(i).getEmbedding();
            for (int j = 0; j < numFeatures; j++) {
                dataMatrix[i][j] = embedding[j];
            }
        }

        // Center the data (subtract mean from each feature)
        double[] means = new double[numFeatures];
        for (int j = 0; j < numFeatures; j++) {
            double sum = 0;
            for (int i = 0; i < numSamples; i++) {
                sum += dataMatrix[i][j];
            }
            means[j] = sum / numSamples;
        }

        for (int i = 0; i < numSamples; i++) {
            for (int j = 0; j < numFeatures; j++) {
                dataMatrix[i][j] -= means[j];
            }
        }

        // Create centered matrix
        RealMatrix centeredMatrix = new Array2DRowRealMatrix(dataMatrix);

        // Perform SVD: X = U * S * V^T
        // The first 3 columns of U*S give us the PCA projection
        SingularValueDecomposition svd = new SingularValueDecomposition(centeredMatrix);

        // Get U matrix (left singular vectors) and singular values
        RealMatrix U = svd.getU();
        double[] singularValues = svd.getSingularValues();

        // Create Celebrity3D objects with scaled coordinates for better visualization
        List<Celebrity3D> result = new ArrayList<>();
        // Larger scale factor to spread out normalized embeddings in 3D space
        // Normalized vectors have small variance, so we need aggressive scaling
        double scale = 1000.0; // Scale factor for 3D visualization

        for (int i = 0; i < numSamples; i++) {
            // Project onto first 3 principal components
            // PC_i = U_i * S_i (for first 3 components)
            double x = U.getEntry(i, 0) * singularValues[0] * scale;
            double y = U.getEntry(i, 1) * singularValues[1] * scale;
            double z = U.getEntry(i, 2) * singularValues[2] * scale;

            result.add(new Celebrity3D(celebrities.get(i), x, y, z));
        }

        return result;
    }
}
