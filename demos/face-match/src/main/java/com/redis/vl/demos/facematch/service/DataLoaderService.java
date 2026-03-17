package com.redis.vl.demos.facematch.service;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.utils.vectorize.DJLFaceVectorizer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Service for loading and generating celebrity data. */
public class DataLoaderService {

  private Map<String, String> celebrityNames = null;
  private List<String> availableImageIds = null;

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

  /**
   * Discover all available celebrity image IDs from the resources directory.
   *
   * @return List of celebrity image IDs
   */
  private List<String> discoverAvailableImageIds() {
    if (availableImageIds != null) {
      return availableImageIds;
    }

    List<String> imageIds = new ArrayList<>();

    try {
      // Get the images directory from resources
      URI uri = getClass().getResource("/static/images/celebs/").toURI();
      Path imagesPath;

      // Handle both file system and JAR resources
      if (uri.getScheme().equals("jar")) {
        // Running from JAR - need to list resources differently
        // For now, fall back to scanning the resource stream
        System.err.println(
            "WARNING: Running from JAR - cannot dynamically discover images. Using resource"
                + " listing.");
        // This would require additional logic to list JAR entries
        imageIds = listImagesFromJar();
      } else {
        // Running from file system (development mode)
        imagesPath = Paths.get(uri);
        try (Stream<Path> paths = Files.walk(imagesPath, 1)) {
          imageIds =
              paths
                  .filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().startsWith("img_"))
                  .filter(p -> p.getFileName().toString().endsWith(".jpg"))
                  .map(
                      p -> {
                        String filename = p.getFileName().toString();
                        // Extract ID from img_123.jpg -> 123
                        return filename.substring(4, filename.length() - 4);
                      })
                  .sorted()
                  .collect(Collectors.toList());
        }
      }

      System.out.println("Discovered " + imageIds.size() + " celebrity images");
    } catch (IOException | URISyntaxException e) {
      System.err.println("Error discovering image files: " + e.getMessage());
      // Fall back to empty list
    }

    availableImageIds = imageIds;
    return imageIds;
  }

  /** Fall back for listing images when running from JAR. */
  private List<String> listImagesFromJar() {
    // For JAR mode, we'd need to parse the JAR entries
    // For now, return empty list (most users will run from gradle/IDE in dev mode)
    System.err.println("JAR mode not yet implemented for image discovery");
    return new ArrayList<>();
  }

  /**
   * Generate sample celebrities with real ArcFace embeddings from images.
   *
   * @param count Number of celebrities to generate (0 or negative means all)
   * @return List of celebrities with embeddings
   */
  public List<Celebrity> generateSampleCelebrities(int count) {
    List<Celebrity> celebrities = new ArrayList<>();

    // Load celebrity names from CSV
    if (celebrityNames == null) {
      celebrityNames = loadCelebrityNames();
    }

    // Discover available images
    List<String> availableIds = discoverAvailableImageIds();

    // Limit count to available images (0 or negative means all)
    int actualCount = (count <= 0) ? availableIds.size() : Math.min(count, availableIds.size());

    System.out.println(
        "Generating embeddings for "
            + actualCount
            + " celebrities using DJL Face Recognition (RedisVL vectorizer)...");

    // Initialize DJL Face vectorizer (RedisVL infrastructure)
    try (DJLFaceVectorizer vectorizer = new DJLFaceVectorizer()) {
      for (int i = 0; i < actualCount; i++) {
        String celebId = availableIds.get(i);
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

  /** Generate synthetic embedding as fallback. */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "DMI_RANDOM_USED_ONLY_ONCE",
      justification = "Seeded Random for deterministic embeddings per celebrity")
  private float[] generateSyntheticEmbedding(String celebId) {
    Random random = new Random(celebId.hashCode());
    float[] embedding = new float[512];
    for (int i = 0; i < 512; i++) {
      embedding[i] = (float) random.nextGaussian();
    }
    normalizeVector(embedding);
    return embedding;
  }

  /** Normalize a vector to unit length (L2 norm = 1). */
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

  /** Load celebrity names from CSV file. CSV format: id,imdb_id,name,popularity,image_resource */
  private Map<String, String> loadCelebrityNames() {
    Map<String, String> names = new HashMap<>();
    try (InputStream csvStream = getClass().getResourceAsStream("/data/celeb_faces.csv");
        BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(csvStream, java.nio.charset.StandardCharsets.UTF_8))) {

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
