package com.redis.vl.demos.facematch.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a celebrity with face embedding vector.
 */
public class Celebrity {

    public static final int EMBEDDING_DIMENSION = 512;

    private final String id;
    private final String name;
    private final String imageUrl;
    private final float[] embedding;

    /**
     * Create a Celebrity instance.
     *
     * @param id Unique identifier
     * @param name Celebrity name
     * @param imageUrl URL to celebrity image
     * @param embedding 512-dimensional face embedding vector
     */
    public Celebrity(String id, String name, String imageUrl, float[] embedding) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        if (id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be empty");
        }
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        if (embedding == null) {
            throw new IllegalArgumentException("Embedding cannot be null");
        }
        if (embedding.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(
                String.format("Embedding must be %d-dimensional, got %d", EMBEDDING_DIMENSION, embedding.length)
            );
        }

        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.embedding = embedding.clone(); // Defensive copy
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    /**
     * Get embedding vector (defensive copy).
     *
     * @return Clone of the embedding array
     */
    public float[] getEmbedding() {
        return embedding.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Celebrity celebrity = (Celebrity) o;
        return Objects.equals(id, celebrity.id)
            && Objects.equals(name, celebrity.name)
            && Objects.equals(imageUrl, celebrity.imageUrl)
            && Arrays.equals(embedding, celebrity.embedding);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, name, imageUrl);
        result = 31 * result + Arrays.hashCode(embedding);
        return result;
    }

    @Override
    public String toString() {
        return "Celebrity{" +
            "id='" + id + '\'' +
            ", name='" + name + '\'' +
            ", imageUrl='" + imageUrl + '\'' +
            ", embeddingDim=" + embedding.length +
            '}';
    }
}
