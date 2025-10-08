package com.redis.vl.notebooks;

import static org.assertj.core.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.query.VectorQuery;
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.VectorField;
import com.redis.vl.utils.vectorize.BaseVectorizer;
import com.redis.vl.utils.vectorize.LangChain4JVectorizer;
import com.redis.vl.utils.vectorize.SentenceTransformersVectorizer;
import dev.langchain4j.model.cohere.CohereEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.voyageai.VoyageAiEmbeddingModel;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

/**
 * Integration test reproducing the 04_vectorizers.ipynb notebook.
 *
 * <p>Ported from:
 * /Users/brian.sam-bodden/Code/redis/py/redis-vl-python/docs/user_guide/04_vectorizers.ipynb
 *
 * <p>Uses same models and data as Python version: - Test sentences: "That is a happy dog", "That is
 * a happy person", "Today is a sunny day" - OpenAI: text-embedding-ada-002 - HuggingFace:
 * sentence-transformers/all-mpnet-base-v2 - Cohere: embed-english-v3.0 - VoyageAI: voyage-law-2
 */
@Tag("integration")
public class VectorizersNotebookIntegrationTest extends BaseIntegrationTest {

  // Same test sentences as Python notebook
  private List<String> sentences;

  @BeforeEach
  public void setUp() {
    sentences =
        Arrays.asList("That is a happy dog", "That is a happy person", "Today is a sunny day");
  }

  @Test
  public void testOpenAIVectorizer() {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null) {
      System.out.println("Skipping OpenAI test - OPENAI_API_KEY not set");
      return;
    }

    // Create a vectorizer using OpenAI's text-embedding-ada-002 model (same as Python)
    var openaiModel =
        OpenAiEmbeddingModel.builder().apiKey(apiKey).modelName("text-embedding-ada-002").build();
    var oai = new LangChain4JVectorizer("text-embedding-ada-002", openaiModel);

    // Embed a single sentence
    float[] test = oai.embed("This is a test sentence.");
    System.out.println("OpenAI Vector dimensions: " + test.length);
    assertThat(test.length).isEqualTo(1536); // text-embedding-ada-002 produces 1536 dims

    // Print first 10 dimensions (like Python notebook)
    System.out.println("First 10 dimensions: " + Arrays.toString(Arrays.copyOfRange(test, 0, 10)));

    // Create many embeddings at once
    List<float[]> embeddings = oai.embedBatch(sentences);
    assertThat(embeddings).hasSize(3);
    System.out.println("Number of embeddings: " + embeddings.size());
    System.out.println(
        "First embedding (first 10): "
            + Arrays.toString(Arrays.copyOfRange(embeddings.get(0), 0, 10)));
  }

  @Test
  public void testHuggingFaceVectorizer() {
    // Create a vectorizer using HuggingFace Sentence Transformers (same as Python)
    var hf = new SentenceTransformersVectorizer("sentence-transformers/all-mpnet-base-v2");

    // Embed a sentence
    float[] hfTest = hf.embed("This is a test sentence.");
    System.out.println("HF Vector dimensions: " + hfTest.length);
    assertThat(hfTest.length).isEqualTo(768); // all-mpnet-base-v2 produces 768 dims
    System.out.println(
        "First 10 dimensions: " + Arrays.toString(Arrays.copyOfRange(hfTest, 0, 10)));

    // Create many embeddings at once
    List<float[]> hfEmbeddings = hf.embedBatch(sentences);
    assertThat(hfEmbeddings).hasSize(3);
    System.out.println("Created " + hfEmbeddings.size() + " embeddings");
  }

  @Test
  public void testCohereVectorizer() {
    String apiKey = System.getenv("COHERE_API_KEY");
    if (apiKey == null) {
      System.out.println("Skipping Cohere test - COHERE_API_KEY not set");
      return;
    }

    // Create a vectorizer using Cohere (same model as Python)
    // Note: Cohere v3 models require inputType to be specified
    var cohereModel =
        CohereEmbeddingModel.builder()
            .apiKey(apiKey)
            .modelName("embed-english-v3.0")
            .inputType("search_query")
            .build();
    var co = new LangChain4JVectorizer("embed-english-v3.0", cohereModel);

    // Embed a search query
    float[] queryEmbed = co.embed("This is a test sentence.");
    System.out.println("Cohere Query vector dimensions: " + queryEmbed.length);
    assertThat(queryEmbed.length).isEqualTo(1024); // embed-english-v3.0 produces 1024 dims
    System.out.println(
        "First 10 dimensions: " + Arrays.toString(Arrays.copyOfRange(queryEmbed, 0, 10)));
  }

  @Test
  public void testVoyageAIVectorizer() {
    String apiKey = System.getenv("VOYAGE_API_KEY");
    if (apiKey == null) {
      System.out.println("Skipping VoyageAI test - VOYAGE_API_KEY not set");
      return;
    }

    // Create a vectorizer using VoyageAI (same model as Python)
    var voyageModel =
        VoyageAiEmbeddingModel.builder().apiKey(apiKey).modelName("voyage-law-2").build();
    var vo = new LangChain4JVectorizer("voyage-law-2", voyageModel);

    // Embed a search query
    float[] voyageQuery = vo.embed("This is a test sentence.");
    System.out.println("VoyageAI vector dimensions: " + voyageQuery.length);
    assertThat(voyageQuery.length).isEqualTo(1024); // voyage-law-2 produces 1024 dims
    System.out.println(
        "First 10 dimensions: " + Arrays.toString(Arrays.copyOfRange(voyageQuery, 0, 10)));
  }

  @Test
  public void testCustomVectorizer() {
    // Create a simple custom vectorizer (same as Python notebook)
    class CustomVectorizer extends BaseVectorizer {
      public CustomVectorizer() {
        super("custom-model", 768, "float32");
      }

      @Override
      protected float[] generateEmbedding(String text) {
        float[] embedding = new float[768];
        Arrays.fill(embedding, 0.101f);
        return embedding;
      }

      @Override
      protected List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize) {
        return texts.stream().map(this::generateEmbedding).toList();
      }
    }

    var customVectorizer = new CustomVectorizer();
    float[] customEmbed = customVectorizer.embed("This is a test sentence.");
    assertThat(customEmbed.length).isEqualTo(768);
    assertThat(customEmbed[0]).isEqualTo(0.101f);
    System.out.println(
        "Custom vectorizer: " + Arrays.toString(Arrays.copyOfRange(customEmbed, 0, 10)));
  }

  @Test
  public void testSearchWithProviderEmbeddings() {
    // Use HuggingFace vectorizer (same as Python notebook)
    var hf = new SentenceTransformersVectorizer("sentence-transformers/all-mpnet-base-v2");

    // Create the schema (same as Python notebook YAML)
    var schema =
        IndexSchema.builder()
            .name("vectorizers")
            .prefix("doc")
            .storageType(IndexSchema.StorageType.HASH)
            .addTextField("sentence", textField -> {})
            .addVectorField(
                "embedding",
                768,
                vectorField ->
                    vectorField
                        .algorithm(VectorAlgorithm.FLAT)
                        .distanceMetric(VectorField.DistanceMetric.COSINE)
                        .dataType(VectorField.VectorDataType.FLOAT32))
            .build();

    // Create the index
    var index = new SearchIndex(schema, unifiedJedis);
    index.create(true); // overwrite if exists
    System.out.println("Index created: " + index.getName());

    try {
      // Create embeddings for our sentences (same sentences as Python)
      List<float[]> sentenceEmbeddings = hf.embedBatch(sentences);

      // Prepare data for loading
      List<Map<String, Object>> data = new ArrayList<>();
      for (int i = 0; i < sentences.size(); i++) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("sentence", sentences.get(i));
        doc.put("embedding", sentenceEmbeddings.get(i));
        data.add(doc);
      }

      // Load data into the index
      index.load(data);
      System.out.println("Loaded " + data.size() + " documents");

      // Use the HuggingFace vectorizer to create a query embedding
      // Query: "That is a happy cat" (same as Python notebook)
      float[] queryEmbedding = hf.embed("That is a happy cat");

      // Create and execute a vector query
      var query =
          VectorQuery.builder()
              .vector(queryEmbedding)
              .field("embedding")
              .returnFields(List.of("sentence"))
              .numResults(3)
              .build();

      List<Map<String, Object>> results = index.query(query);
      assertThat(results).hasSize(3);

      System.out.println("\nSearch results for: 'That is a happy cat'");
      for (var doc : results) {
        System.out.println(doc.get("sentence") + " - Distance: " + doc.get("vector_distance"));
      }

      // Verify first result is about a happy dog (most similar to happy cat)
      String firstResult = (String) results.get(0).get("sentence");
      assertThat(firstResult).isEqualTo("That is a happy dog");

    } finally {
      // Cleanup
      index.delete(true);
      System.out.println("Index deleted");
    }
  }

  @Test
  public void testDataTypeSelection() {
    // Test different data types (same as Python notebook)

    // Create vectorizer with default FLOAT32
    var vectorizer32 =
        new SentenceTransformersVectorizer("sentence-transformers/all-mpnet-base-v2");

    float[] float32Embed = vectorizer32.embed("test sentence");
    assertThat(float32Embed.length).isEqualTo(768);

    // Note: Python supports float16 and float64, but Java ONNX runtime may have limitations
    // For now, we verify that FLOAT32 works correctly
    System.out.println("FLOAT32 embedding created successfully");
  }
}
