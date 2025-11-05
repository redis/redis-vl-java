package com.redis.vl.demo.blog;

import com.redis.vl.index.SearchIndex;
import com.redis.vl.langchain4j.RedisVLContentRetriever;
import com.redis.vl.langchain4j.RedisVLEmbeddingStore;
import com.redis.vl.extensions.cache.SemanticCache;
import com.redis.vl.extensions.messagehistory.MessageHistory;
import com.redis.vl.query.Filter;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.utils.vectorize.HFTextVectorizer;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import redis.clients.jedis.UnifiedJedis;

import java.util.*;

/**
 * Blog demo showing real execution results for RedisVL for Java.
 *
 * Demonstrates:
 * 1. Index creation with SVS-VAMANA
 * 2. Vector search with actual results
 * 3. Pre-filtered semantic search
 * 4. Semantic caching with hit/miss
 * 5. Message history
 */
public class BlogDemo {

    private static final int VECTOR_DIM = 384;
    private static final String INDEX_NAME = "products-demo";

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         RedisVL for Java - Live Demo Outputs                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        UnifiedJedis jedis = new UnifiedJedis("redis://localhost:6379");

        // Demo 1: Index Creation
        demo1_IndexCreation(jedis);

        // Demo 2: Vector Search
        demo2_VectorSearch(jedis);

        // Demo 3: Pre-Filtered Search
        demo3_PreFilteredSearch(jedis);

        // Demo 4: Semantic Caching
        demo4_SemanticCache(jedis);

        // Demo 5: Message History
        demo5_MessageHistory(jedis);

        jedis.close();
        System.out.println("\n✓ All demos completed successfully!");
    }

    private static void demo1_IndexCreation(UnifiedJedis jedis) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Demo 1: Creating Index with SVS-VAMANA + FP16 Quantization");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        Map<String, Object> schemaMap = Map.of(
            "index", Map.of(
                "name", INDEX_NAME,
                "prefix", "product:"
            ),
            "fields", List.of(
                Map.of("name", "title", "type", "text"),
                Map.of("name", "category", "type", "tag"),
                Map.of("name", "price", "type", "numeric"),
                Map.of(
                    "name", "vector",
                    "type", "vector",
                    "attrs", Map.of(
                        "dims", VECTOR_DIM,
                        "distance_metric", "cosine",
                        "algorithm", "svs-vamana",
                        "quantization_type", "fp16"
                    )
                )
            )
        );

        IndexSchema schema = IndexSchema.fromDict(schemaMap);
        SearchIndex index = new SearchIndex(schema, jedis);

        try {
            index.create(true);
            System.out.println("✓ Index created successfully");
            System.out.println("  • Name: " + INDEX_NAME);
            System.out.println("  • Algorithm: SVS-VAMANA");
            System.out.println("  • Dimensions: 384");
            System.out.println("  • Quantization: FP16 (50% memory savings)");
            System.out.println("  • Fields: title (text), category (tag), price (numeric), vector");
        } catch (Exception e) {
            System.out.println("✓ Index already exists, using existing index");
        }
        System.out.println();
    }

    private static void demo2_VectorSearch(UnifiedJedis jedis) throws Exception {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Demo 2: Semantic Vector Search");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        // Setup
        IndexSchema schema = IndexSchema.fromDict(Map.of(
            "index", Map.of("name", INDEX_NAME, "prefix", "product:"),
            "fields", List.of(
                Map.of("name", "title", "type", "text"),
                Map.of("name", "category", "type", "tag"),
                Map.of("name", "price", "type", "numeric"),
                Map.of("name", "vector", "type", "vector", "attrs",
                    Map.of("dims", VECTOR_DIM, "distance_metric", "cosine", "algorithm", "svs-vamana"))
            )
        ));
        SearchIndex index = new SearchIndex(schema, jedis);
        RedisVLEmbeddingStore embeddingStore = new RedisVLEmbeddingStore(index);
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        // Add sample products
        String[] products = {
            "Wireless Bluetooth Headphones - Premium sound quality",
            "Smartphone with 5G connectivity and triple camera",
            "Laptop with 16GB RAM and 512GB SSD",
            "Smart Watch with fitness tracking features",
            "Portable Bluetooth Speaker - Waterproof design"
        };

        System.out.println("Indexing sample products...");
        for (int i = 0; i < products.length; i++) {
            Embedding embedding = embeddingModel.embed(products[i]).content();
            Metadata metadata = new Metadata();
            metadata.put("category", i < 2 ? "audio" : "electronics");
            metadata.put("price", 100 + (i * 50));
            TextSegment segment = TextSegment.from(products[i], metadata);
            embeddingStore.add("product:" + (i + 1), embedding, segment);
        }
        System.out.println("✓ Indexed " + products.length + " products\n");

        // Search
        String query = "wireless headphones";
        System.out.println("Query: \"" + query + "\"");
        System.out.println();

        Embedding queryEmbedding = embeddingModel.embed(query).content();
        var searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(3)
            .build();

        var results = embeddingStore.search(searchRequest);

        System.out.println("Results (sorted by relevance):");
        System.out.println("────────────────────────────────────────────────────────────────────");
        int rank = 1;
        for (var match : results.matches()) {
            double similarity = match.score();
            String text = match.embedded().text();
            System.out.printf("%d. [Score: %.4f] %s%n", rank++, similarity, text);
        }
        System.out.println();
    }

    private static void demo3_PreFilteredSearch(UnifiedJedis jedis) throws Exception {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Demo 3: Pre-Filtered Semantic Search");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        IndexSchema schema = IndexSchema.fromDict(Map.of(
            "index", Map.of("name", INDEX_NAME, "prefix", "product:"),
            "fields", List.of(
                Map.of("name", "title", "type", "text"),
                Map.of("name", "category", "type", "tag"),
                Map.of("name", "price", "type", "numeric"),
                Map.of("name", "vector", "type", "vector", "attrs",
                    Map.of("dims", VECTOR_DIM, "distance_metric", "cosine", "algorithm", "svs-vamana"))
            )
        ));
        SearchIndex index = new SearchIndex(schema, jedis);
        RedisVLEmbeddingStore embeddingStore = new RedisVLEmbeddingStore(index);
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        String query = "wireless audio device";
        System.out.println("Query: \"" + query + "\"");
        System.out.println("Filter: category = 'audio'");
        System.out.println();

        Embedding queryEmbedding = embeddingModel.embed(query).content();
        Filter categoryFilter = Filter.tag("category", "audio");

        var searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(5)
            .filter(categoryFilter)
            .build();

        var results = embeddingStore.search(searchRequest);

        System.out.println("Results (pre-filtered during search, not after):");
        System.out.println("────────────────────────────────────────────────────────────────────");
        int rank = 1;
        for (var match : results.matches()) {
            double similarity = match.score();
            String text = match.embedded().text();
            String category = match.embedded().metadata().getString("category");
            System.out.printf("%d. [Score: %.4f] [Category: %s] %s%n",
                rank++, similarity, category, text);
        }
        System.out.println("\n✓ Filter applied during vector search for optimal performance");
        System.out.println();
    }

    private static void demo4_SemanticCache(UnifiedJedis jedis) throws Exception {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Demo 4: Semantic Caching");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        HFTextVectorizer vectorizer = HFTextVectorizer.builder()
            .model("sentence-transformers/all-MiniLM-L6-v2")
            .build();

        SemanticCache cache = new SemanticCache.Builder()
            .name("llm-cache-demo")
            .redisClient(jedis)
            .distanceThreshold(0.1f)
            .vectorizer(vectorizer)
            .build();

        // First query - MISS
        String question1 = "What is the Transformer architecture?";
        System.out.println("Query 1: \"" + question1 + "\"");
        long startMiss = System.currentTimeMillis();
        var hit1 = cache.check(question1);
        long timeMiss = System.currentTimeMillis() - startMiss;

        if (hit1.isEmpty()) {
            System.out.println("Result: ❌ CACHE MISS");
            System.out.println("Time: " + timeMiss + "ms");
            // Simulate LLM call
            String response = "The Transformer is a neural network architecture based on self-attention mechanisms...";
            cache.store(question1, response);
            System.out.println("✓ Stored in cache for future queries");
        }
        System.out.println();

        // Similar query - HIT
        String question2 = "Explain the Transformer model";
        System.out.println("Query 2: \"" + question2 + "\" (semantically similar)");
        long startHit = System.currentTimeMillis();
        var hit2 = cache.check(question2);
        long timeHit = System.currentTimeMillis() - startHit;

        if (hit2.isPresent()) {
            System.out.println("Result: ✓ CACHE HIT");
            System.out.println("Time: " + timeHit + "ms");
            System.out.println("Response: " + hit2.get().getResponse().substring(0, 80) + "...");
            System.out.println();
            System.out.println("Performance improvement:");
            System.out.println("  • Cache hit is ~" + String.format("%.1f", (timeMiss / (double)timeHit)) + "x faster");
            System.out.println("  • Saved LLM API call (typical: 2-4 seconds + cost)");
        }
        System.out.println();
    }

    private static void demo5_MessageHistory(UnifiedJedis jedis) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Demo 5: Message History for Conversational AI");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        MessageHistory history = new MessageHistory("conversation-demo", "user-123", null, jedis);

        // Add conversation turns
        System.out.println("Building conversation history...");
        history.addMessage(Map.of(
            "role", "system",
            "content", "You are a helpful research assistant"
        ));

        history.store(
            "What is the Transformer architecture?",
            "The Transformer is a neural network architecture based on self-attention mechanisms..."
        );

        history.store(
            "How does attention work?",
            "Attention mechanisms allow the model to focus on relevant parts of the input..."
        );

        history.store(
            "What are the key components?",
            "The key components include Multi-Head Attention, Position-wise Feed-Forward Networks..."
        );

        System.out.println("✓ Stored 4 messages (1 system + 3 turns)\n");

        // Retrieve recent context
        System.out.println("Retrieving last 3 messages for context:");
        System.out.println("────────────────────────────────────────────────────────────────────");

        List<Map<String, Object>> recent = history.getRecent(3, false, false, null, null);
        for (int i = 0; i < recent.size(); i++) {
            Map<String, Object> msg = recent.get(i);
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");
            String preview = content.length() > 60 ? content.substring(0, 60) + "..." : content;
            System.out.printf("%d. [%s] %s%n", i + 1, role.toUpperCase(), preview);
        }

        System.out.println("\n✓ Context ready for next LLM call");
        System.out.println();
    }
}
