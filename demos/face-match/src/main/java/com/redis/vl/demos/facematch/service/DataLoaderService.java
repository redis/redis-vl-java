package com.redis.vl.demos.facematch.service;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.utils.vectorize.DJLFaceVectorizer;
import javafx.scene.image.Image;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Service for loading and generating celebrity data.
 */
public class DataLoaderService {

    private Map<String, String> celebrityNames = null;

    private static final String[] SAMPLE_NAMES = {
        "Tom Hanks", "Meryl Streep", "Denzel Washington", "Cate Blanchett",
        "Leonardo DiCaprio", "Viola Davis", "Brad Pitt", "Emma Stone",
        "Morgan Freeman", "Charlize Theron", "Robert De Niro", "Nicole Kidman",
        "Al Pacino", "Julianne Moore", "Tom Cruise", "Jennifer Lawrence",
        "Samuel L. Jackson", "Natalie Portman", "Anthony Hopkins", "Scarlett Johansson",
        "Jack Nicholson", "Kate Winslet", "Daniel Day-Lewis", "Amy Adams",
        "Gary Oldman", "Frances McDormand", "Christian Bale", "Saoirse Ronan",
        "Joaquin Phoenix", "Jessica Chastain", "Russell Crowe", "Marion Cotillard",
        "Colin Firth", "Tilda Swinton", "Matthew McConaughey", "Rooney Mara",
        "Hugh Jackman", "Emily Blunt", "Javier Bardem", "Lupita Nyong'o",
        "Benedict Cumberbatch", "Rachel Weisz", "Ryan Gosling", "Michelle Williams",
        "Idris Elba", "Octavia Spencer", "Chris Hemsworth", "Margot Robbie",
        "Mark Ruffalo", "Laura Dern", "Jake Gyllenhaal", "Saoirse Ronan",
        "Willem Dafoe", "Toni Collette", "Oscar Isaac", "Olivia Colman",
        "Michael Fassbender", "Helen Mirren", "Adam Driver", "Frances McDormand",
        "Eddie Redmayne", "Allison Janney", "Rami Malek", "Regina King",
        "Timothée Chalamet", "Florence Pugh", "Dev Patel", "Yalitza Aparicio",
        "Mahershala Ali", "Glenn Close", "Viggo Mortensen", "Lady Gaga",
        "Bradley Cooper", "Melissa McCarthy", "Rami Malek", "Olivia Colman",
        "Christian Bale", "Amy Adams", "Ethan Hawke", "Toni Collette",
        "John David Washington", "Claire Foy", "Sam Rockwell", "Allison Janney",
        "Joaquin Phoenix", "Margot Robbie", "Antonio Banderas", "Cynthia Erivo",
        "Adam Sandler", "Jennifer Lopez", "Robert Pattinson", "Lupita Nyong'o",
        "Eddie Murphy", "Awkwafina", "Tom Hanks", "Renée Zellweger",
        "Al Pacino", "Scarlett Johansson", "Joe Pesci", "Laura Dern"
    };

    // Available celebrity image IDs (100 images we copied, sorted numerically)
    private static final String[] AVAILABLE_IDS = {
        "1", "100", "101", "1003", "1010", "10017", "10018", "10020", "10023", "10029",
        "10051", "10080", "10084", "10086", "10091", "10099", "10112", "10127",
        "10128", "10131", "10132", "10134", "10158", "100057", "100060", "100099",
        "100107", "100127", "100415", "100460", "100528", "100564", "100602", "100634",
        "100653", "100669", "100765", "100794", "100870", "100945", "100989", "101014",
        "101017", "101028", "101060", "101130", "101219", "101250", "101336", "101377",
        "101378", "101396", "101519", "101565", "101576", "1001536", "1001657",
        "1001946", "1003086", "1003260", "1003801", "1003843", "1003944", "1004565",
        "1004624", "1004806", "1004890", "1005944", "1006594", "1006731", "1006733",
        "1007541", "1007561", "1007683", "1008384", "1008515", "1009999", "1010001",
        "1010135", "1010189", "1010799", "1010877", "1011019", "1011103", "1011107",
        "1011160", "1011210", "1011904", "1012752", "1012982", "1013048", "1013156",
        "1013973", "1014784", "1014849", "1014921", "1014931", "1015727", "1015809",
        "1015824"
    };

    /**
     * Generate sample celebrities with real ArcFace embeddings from images.
     *
     * @param count Number of celebrities to generate
     * @return List of celebrities with embeddings
     */
    public List<Celebrity> generateSampleCelebrities(int count) {
        List<Celebrity> celebrities = new ArrayList<>();

        // Load celebrity names from CSV
        if (celebrityNames == null) {
            celebrityNames = loadCelebrityNames();
        }

        // Limit count to available images
        int actualCount = Math.min(count, AVAILABLE_IDS.length);

        System.out.println("Generating embeddings for " + actualCount + " celebrities using DJL Face Recognition (RedisVL vectorizer)...");

        // Initialize DJL Face vectorizer (RedisVL infrastructure)
        try (DJLFaceVectorizer vectorizer = new DJLFaceVectorizer()) {
            for (int i = 0; i < actualCount; i++) {
                String celebId = AVAILABLE_IDS[i];
                String id = "celeb_" + celebId;
                // Get name from CSV, fallback to synthetic if not found
                String name = celebrityNames.getOrDefault(celebId, SAMPLE_NAMES[i % SAMPLE_NAMES.length]);
                String imageUrl = "http://example.com/images/" + id + ".jpg";

                // Load celebrity image and generate real embedding
                try {
                    String resourcePath = "/static/images/celebs/img_" + celebId + ".jpg";
                    InputStream inputStream = getClass().getResourceAsStream(resourcePath);

                    if (inputStream != null) {
                        float[] embedding = vectorizer.embedImage(inputStream);

                        celebrities.add(new Celebrity(id, name, imageUrl, embedding));

                        if ((i + 1) % 10 == 0) {
                            System.out.println("Generated " + (i + 1) + "/" + actualCount + " embeddings");
                        }
                    } else {
                        System.err.println("Warning: Image not found for " + id);
                        // Fall back to synthetic embedding for missing images
                        float[] syntheticEmbedding = generateSyntheticEmbedding(celebId);
                        celebrities.add(new Celebrity(id, name, imageUrl, syntheticEmbedding));
                    }
                } catch (Exception e) {
                    System.err.println("Error generating embedding for " + id + ": " + e.getMessage());
                    // Fall back to synthetic embedding
                    float[] syntheticEmbedding = generateSyntheticEmbedding(celebId);
                    celebrities.add(new Celebrity(id, name, imageUrl, syntheticEmbedding));
                }
            }
        } catch (Exception e) {
            System.err.println("Error initializing DJL Face vectorizer: " + e.getMessage());
            throw new RuntimeException("Failed to generate embeddings", e);
        }

        System.out.println("Completed generating " + celebrities.size() + " embeddings");
        return celebrities;
    }

    /**
     * Generate synthetic embedding as fallback.
     */
    private float[] generateSyntheticEmbedding(String celebId) {
        Random random = new Random(celebId.hashCode());
        float[] embedding = new float[512];
        for (int i = 0; i < 512; i++) {
            embedding[i] = (float) random.nextGaussian();
        }
        normalizeVector(embedding);
        return embedding;
    }

    /**
     * Normalize a vector to unit length (L2 norm = 1).
     */
    private void normalizeVector(float[] vector) {
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += v * v;
        }
        double norm = Math.sqrt(sumSquares);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    /**
     * Load celebrity names from CSV file.
     * CSV format: id,imdb_id,name,popularity,image_resource
     */
    private Map<String, String> loadCelebrityNames() {
        Map<String, String> names = new HashMap<>();
        try (InputStream csvStream = getClass().getResourceAsStream("/data/celeb_faces.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(csvStream))) {

            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false; // Skip header
                    continue;
                }

                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String id = parts[0].trim();
                    String name = parts[2].trim();
                    names.put(id, name);
                }
            }

            System.out.println("Loaded " + names.size() + " celebrity names from CSV");
        } catch (Exception e) {
            System.err.println("Error loading celebrity names: " + e.getMessage());
        }
        return names;
    }
}
