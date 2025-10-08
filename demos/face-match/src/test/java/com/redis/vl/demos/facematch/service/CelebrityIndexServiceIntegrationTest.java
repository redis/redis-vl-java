package com.redis.vl.demos.facematch.service;

import com.redis.vl.demos.facematch.model.Celebrity;
import com.redis.vl.demos.facematch.model.FaceMatch;
import com.redis.vl.index.SearchIndex;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import redis.clients.jedis.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for CelebrityIndexService using RedisVL SearchIndex.
 */
@Testcontainers
class CelebrityIndexServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
        DockerImageName.parse("redis/redis-stack:latest")
    ).withExposedPorts(6379);

    private UnifiedJedis jedis;
    private CelebrityIndexService service;

    @BeforeEach
    void setUp() {
        String host = redis.getHost();
        int port = redis.getMappedPort(6379);
        HostAndPort hostAndPort = new HostAndPort(host, port);
        jedis = new UnifiedJedis(hostAndPort);
        service = new CelebrityIndexService(jedis);
        service.createIndex();
    }

    @AfterEach
    void tearDown() {
        try {
            service.deleteIndex();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        if (jedis != null) {
            jedis.close();
        }
    }

    @Test
    void testCreateIndex() {
        SearchIndex index = service.getIndex();
        assertThat(index).isNotNull();
        assertThat(index.getSchema().getName()).isEqualTo("celebrity_faces");
    }

    @Test
    void testIndexCelebrity() {
        float[] embedding = new float[512];
        for (int i = 0; i < 512; i++) {
            embedding[i] = (float) Math.random();
        }
        Celebrity celebrity = new Celebrity("1", "Tom Hanks", "http://example.com/tom.jpg", embedding);

        service.indexCelebrity(celebrity);

        // Verify it was indexed
        Celebrity retrieved = service.getCelebrityById("1");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getName()).isEqualTo("Tom Hanks");
    }

    @Test
    void testIndexMultipleCelebrities() {
        Celebrity celeb1 = createCelebrity("1", "Tom Hanks");
        Celebrity celeb2 = createCelebrity("2", "Tom Cruise");
        Celebrity celeb3 = createCelebrity("3", "Brad Pitt");

        service.indexCelebrities(List.of(celeb1, celeb2, celeb3));

        assertThat(service.count()).isEqualTo(3);
    }

    @Test
    void testFindSimilarFaces() {
        // Index 3 celebrities with known embeddings
        Celebrity celeb1 = createCelebrityWithEmbedding("1", "Tom Hanks", 1.0f);
        Celebrity celeb2 = createCelebrityWithEmbedding("2", "Tom Cruise", 0.5f);
        Celebrity celeb3 = createCelebrityWithEmbedding("3", "Brad Pitt", -1.0f);

        service.indexCelebrities(List.of(celeb1, celeb2, celeb3));

        // Query with embedding similar to celeb1
        float[] queryEmbedding = new float[512];
        for (int i = 0; i < 512; i++) {
            queryEmbedding[i] = 1.0f;
        }

        List<FaceMatch> matches = service.findSimilarFaces(queryEmbedding, 2);

        assertThat(matches).hasSize(2);
        // First match should be Tom Hanks (most similar)
        assertThat(matches.get(0).getCelebrity().getName()).isEqualTo("Tom Hanks");
        assertThat(matches.get(0).getRank()).isEqualTo(1);
    }

    @Test
    void testGetAllCelebrities() {
        Celebrity celeb1 = createCelebrity("1", "Tom Hanks");
        Celebrity celeb2 = createCelebrity("2", "Tom Cruise");

        service.indexCelebrities(List.of(celeb1, celeb2));

        List<Celebrity> all = service.getAllCelebrities();
        assertThat(all).hasSize(2);
    }

    @Test
    void testDeleteIndex() {
        service.deleteIndex();
        // After deleting, create a new index to verify it was deleted
        service.createIndex();
        // If we can create it again, it was successfully deleted
        assertThat(service.count()).isEqualTo(0);
    }

    private Celebrity createCelebrity(String id, String name) {
        float[] embedding = new float[512];
        for (int i = 0; i < 512; i++) {
            embedding[i] = (float) Math.random();
        }
        return new Celebrity(id, name, "http://example.com/" + id + ".jpg", embedding);
    }

    private Celebrity createCelebrityWithEmbedding(String id, String name, float value) {
        float[] embedding = new float[512];
        for (int i = 0; i < 512; i++) {
            embedding[i] = value;
        }
        return new Celebrity(id, name, "http://example.com/" + id + ".jpg", embedding);
    }
}
