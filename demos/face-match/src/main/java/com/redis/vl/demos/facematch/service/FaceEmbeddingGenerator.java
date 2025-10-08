package com.redis.vl.demos.facematch.service;

import com.redis.vl.demos.facematch.model.Celebrity;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates synthetic face embeddings for celebrity images.
 * This can be run as a standalone application via Gradle task.
 *
 * NOTE: This generates realistic synthetic embeddings (clustered with noise).
 * For real face embeddings, use ONNX model with face detection.
 */
public class FaceEmbeddingGenerator {

    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    private static final Random RANDOM = new Random(42);

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: FaceEmbeddingGenerator <csv-file>");
            System.err.println("  csv-file: Path to celeb_faces.csv");
            System.exit(1);
        }

        String csvFile = args[0];

        System.out.println("Loading celebrities from CSV: " + csvFile);

        // Load CSV
        List<CsvEntry> entries = loadCsv(csvFile);
        System.out.println("Loaded " + entries.size() + " celebrities from CSV");

        // Connect to Redis
        HostAndPort hostAndPort = new HostAndPort(REDIS_HOST, REDIS_PORT);
        UnifiedJedis jedis = new UnifiedJedis(hostAndPort);
        CelebrityIndexService indexService = new CelebrityIndexService(jedis);

        // Create index if doesn't exist
        try {
            indexService.createIndex();
            System.out.println("Created Redis index");
        } catch (Exception e) {
            System.out.println("Index already exists or error creating: " + e.getMessage());
        }

        // Generate cluster centers for synthetic embeddings
        System.out.println("Generating synthetic face embeddings...");
        int numClusters = 10;
        float[][] clusterCenters = new float[numClusters][512];
        for (int c = 0; c < numClusters; c++) {
            for (int i = 0; i < 512; i++) {
                clusterCenters[c][i] = (float) RANDOM.nextGaussian();
            }
            clusterCenters[c] = normalizeVector(clusterCenters[c]);
        }

        // Process each celebrity
        int processed = 0;
        List<Celebrity> batch = new ArrayList<>();
        int batchSize = 100;

        for (CsvEntry entry : entries) {
            try {
                // Generate synthetic embedding near a random cluster
                int cluster = RANDOM.nextInt(numClusters);
                float[] clusterCenter = clusterCenters[cluster];
                float[] embedding = new float[512];
                float noise = 0.3f;

                for (int j = 0; j < 512; j++) {
                    embedding[j] = clusterCenter[j] + (float) RANDOM.nextGaussian() * noise;
                }
                embedding = normalizeVector(embedding);

                // Create Celebrity object
                Celebrity celebrity = new Celebrity(
                        "celeb_" + entry.id,
                        entry.name,
                        entry.imageResource,
                        embedding
                );

                batch.add(celebrity);

                // Index in batches
                if (batch.size() >= batchSize) {
                    indexService.indexCelebrities(batch);
                    processed += batch.size();
                    batch.clear();
                    System.out.println("Processed: " + processed + " celebrities");
                }

            } catch (Exception e) {
                System.err.println("Error processing " + entry.name + ": " + e.getMessage());
            }
        }

        // Index remaining
        if (!batch.isEmpty()) {
            indexService.indexCelebrities(batch);
            processed += batch.size();
        }

        System.out.println("\nFinished!");
        System.out.println("Indexed " + processed + " celebrities with synthetic embeddings");
        System.out.println("Note: These are synthetic embeddings for demo purposes.");
        System.out.println("For real face embeddings, use ONNX face detection model.");

        jedis.close();
    }

    private static List<CsvEntry> loadCsv(String csvFile) throws IOException {
        List<CsvEntry> entries = new ArrayList<>();

        // Try file path first, then classpath
        InputStream is;
        Path filePath = Paths.get(csvFile);
        if (Files.exists(filePath)) {
            is = Files.newInputStream(filePath);
        } else {
            is = FaceEmbeddingGenerator.class.getResourceAsStream(csvFile);
        }

        if (is == null) {
            throw new IOException("CSV file not found: " + csvFile);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            // Skip header
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 5);
                if (parts.length >= 5) {
                    entries.add(new CsvEntry(
                            parts[0],  // id
                            parts[1],  // imdb_id
                            parts[2],  // name
                            Double.parseDouble(parts[3]),  // popularity
                            parts[4]   // image_resource
                    ));
                }
            }
        }

        return entries;
    }

    private static float[] normalizeVector(float[] vector) {
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += v * v;
        }
        double norm = Math.sqrt(sumSquares);

        if (norm > 0) {
            float[] normalized = new float[vector.length];
            for (int i = 0; i < vector.length; i++) {
                normalized[i] = (float) (vector[i] / norm);
            }
            return normalized;
        }
        return vector;
    }

    private static class CsvEntry {
        final String id;
        final String imdbId;
        final String name;
        final double popularity;
        final String imageResource;

        CsvEntry(String id, String imdbId, String name, double popularity, String imageResource) {
            this.id = id;
            this.imdbId = imdbId;
            this.name = name;
            this.popularity = popularity;
            this.imageResource = imageResource;
        }
    }
}
