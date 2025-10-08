package com.redis.vl.demos.facematch.service;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.demos.facematch.model.Celebrity3D;
import org.apache.commons.math3.linear.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for reducing high-dimensional embeddings to 3D for visualization.
 * Uses PCA (Principal Component Analysis) via SVD for dimensionality reduction.
 */
public class DimensionalityReductionService {

    /**
     * Reduce celebrity embeddings from 512D to 3D using PCA (via SVD).
     * SVD is more numerically stable than eigendecomposition for PCA.
     *
     * @param celebrities List of celebrities with 512-dim embeddings
     * @return List of celebrities with 3D coordinates
     */
    public List<Celebrity3D> reduceTo3D(List<Celebrity> celebrities) {
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
        double scale = 100.0; // Scale factor for 3D visualization

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
