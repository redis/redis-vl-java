package com.redis.vl.utils.vectorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.extensions.cache.EmbeddingsCache;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Comprehensive integration tests for all vectorizer providers.
 *
 * <p>Ported from Python test_vectorizers.py (625 lines) to ensure feature parity with Python
 * RedisVL.
 *
 * <p>Tests are organized by provider and skip gracefully if API keys are not set.
 *
 * <p>Python reference: /redis-vl-python/tests/integration/test_vectorizers.py
 */
@DisplayName("Vectorizer Provider Integration Tests")
class VectorizerProviderIntegrationTest extends BaseIntegrationTest {

  private static final String TEST_TEXT = "This is a test sentence.";
  private static final List<String> TEST_TEXTS =
      List.of("This is the first test sentence.", "This is the second test sentence.");

  /** Helper to create OpenAI vectorizer */
  private LangChain4JVectorizer createOpenAIVectorizer() {
    String apiKey = System.getenv("OPENAI_API_KEY");
    assumeTrue(apiKey != null && !apiKey.isEmpty(), "OPENAI_API_KEY not set");

    try {
      Class<?> openAiClass = Class.forName("dev.langchain4j.model.openai.OpenAiEmbeddingModel");
      EmbeddingModel model =
          (EmbeddingModel) openAiClass.getMethod("withApiKey", String.class).invoke(null, apiKey);
      return new LangChain4JVectorizer("text-embedding-ada-002", model, 1536);
    } catch (Exception e) {
      assumeTrue(false, "OpenAI dependencies not available: " + e.getMessage());
      return null;
    }
  }

  /** Helper to create Azure OpenAI vectorizer */
  private LangChain4JVectorizer createAzureOpenAIVectorizer() {
    String apiKey = System.getenv("AZURE_OPENAI_API_KEY");
    String endpoint = System.getenv("AZURE_OPENAI_ENDPOINT");
    String deploymentName = System.getenv("AZURE_OPENAI_DEPLOYMENT_NAME");
    if (deploymentName == null) {
      deploymentName = "text-embedding-ada-002";
    }

    assumeTrue(
        apiKey != null && endpoint != null,
        "AZURE_OPENAI_API_KEY and AZURE_OPENAI_ENDPOINT not set");

    try {
      Class<?> azureClass = Class.forName("dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel");
      Object builder = azureClass.getMethod("builder").invoke(null);
      builder.getClass().getMethod("apiKey", String.class).invoke(builder, apiKey);
      builder.getClass().getMethod("endpoint", String.class).invoke(builder, endpoint);
      builder.getClass().getMethod("deploymentName", String.class).invoke(builder, deploymentName);
      EmbeddingModel model = (EmbeddingModel) builder.getClass().getMethod("build").invoke(builder);
      return new LangChain4JVectorizer(deploymentName, model, 1536);
    } catch (Exception e) {
      assumeTrue(false, "Azure OpenAI dependencies not available: " + e.getMessage());
      return null;
    }
  }

  /** Helper to create Cohere vectorizer */
  private LangChain4JVectorizer createCohereVectorizer() {
    String apiKey = System.getenv("COHERE_API_KEY");
    assumeTrue(apiKey != null && !apiKey.isEmpty(), "COHERE_API_KEY not set");

    try {
      Class<?> cohereClass = Class.forName("dev.langchain4j.model.cohere.CohereEmbeddingModel");
      Object builder = cohereClass.getMethod("builder").invoke(null);
      builder.getClass().getMethod("apiKey", String.class).invoke(builder, apiKey);
      builder.getClass().getMethod("modelName", String.class).invoke(builder, "embed-english-v3.0");
      EmbeddingModel model = (EmbeddingModel) builder.getClass().getMethod("build").invoke(builder);
      return new LangChain4JVectorizer("embed-english-v3.0", model, 1024);
    } catch (Exception e) {
      assumeTrue(false, "Cohere dependencies not available: " + e.getMessage());
      return null;
    }
  }

  /** Helper to create Vertex AI vectorizer */
  private LangChain4JVectorizer createVertexAIVectorizer() {
    String project = System.getenv("GCP_PROJECT_ID");
    String location = System.getenv("GCP_LOCATION");
    if (location == null) {
      location = "us-central1";
    }
    assumeTrue(project != null && !project.isEmpty(), "GCP_PROJECT_ID not set");

    try {
      Class<?> vertexClass = Class.forName("dev.langchain4j.model.vertexai.VertexAiEmbeddingModel");
      Object builder = vertexClass.getMethod("builder").invoke(null);
      builder.getClass().getMethod("project", String.class).invoke(builder, project);
      builder.getClass().getMethod("location", String.class).invoke(builder, location);
      builder
          .getClass()
          .getMethod("modelName", String.class)
          .invoke(builder, "textembedding-gecko@003");
      EmbeddingModel model = (EmbeddingModel) builder.getClass().getMethod("build").invoke(builder);
      return new LangChain4JVectorizer("textembedding-gecko@003", model, 768);
    } catch (Exception e) {
      assumeTrue(false, "Vertex AI dependencies not available: " + e.getMessage());
      return null;
    }
  }

  /** Helper to create Mistral AI vectorizer */
  private LangChain4JVectorizer createMistralAIVectorizer() {
    String apiKey = System.getenv("MISTRAL_API_KEY");
    assumeTrue(apiKey != null && !apiKey.isEmpty(), "MISTRAL_API_KEY not set");

    try {
      Class<?> mistralClass =
          Class.forName("dev.langchain4j.model.mistralai.MistralAiEmbeddingModel");
      Object builder = mistralClass.getMethod("builder").invoke(null);
      builder.getClass().getMethod("apiKey", String.class).invoke(builder, apiKey);
      builder.getClass().getMethod("modelName", String.class).invoke(builder, "mistral-embed");
      EmbeddingModel model = (EmbeddingModel) builder.getClass().getMethod("build").invoke(builder);
      return new LangChain4JVectorizer("mistral-embed", model, 1024);
    } catch (Exception e) {
      assumeTrue(false, "Mistral AI dependencies not available: " + e.getMessage());
      return null;
    }
  }

  /** Helper to create Voyage AI vectorizer */
  private LangChain4JVectorizer createVoyageAIVectorizer() {
    String apiKey = System.getenv("VOYAGE_API_KEY");
    assumeTrue(apiKey != null && !apiKey.isEmpty(), "VOYAGE_API_KEY not set");

    try {
      Class<?> voyageClass = Class.forName("dev.langchain4j.model.voyageai.VoyageAiEmbeddingModel");
      Object builder = voyageClass.getMethod("builder").invoke(null);
      builder.getClass().getMethod("apiKey", String.class).invoke(builder, apiKey);
      builder.getClass().getMethod("modelName", String.class).invoke(builder, "voyage-large-2");
      EmbeddingModel model = (EmbeddingModel) builder.getClass().getMethod("build").invoke(builder);
      return new LangChain4JVectorizer("voyage-large-2", model, 1536);
    } catch (Exception e) {
      assumeTrue(false, "Voyage AI dependencies not available: " + e.getMessage());
      return null;
    }
  }

  /** Helper to create AWS Bedrock vectorizer */
  private LangChain4JVectorizer createBedrockVectorizer() {
    String region = System.getenv("AWS_REGION");
    if (region == null) {
      region = "us-east-1";
    }
    // AWS credentials are typically configured via AWS SDK default credential chain

    try {
      Class<?> bedrockClass = Class.forName("dev.langchain4j.model.bedrock.BedrockEmbeddingModel");
      Object builder = bedrockClass.getMethod("builder").invoke(null);
      builder.getClass().getMethod("region", String.class).invoke(builder, region);
      builder
          .getClass()
          .getMethod("model", String.class)
          .invoke(builder, "amazon.titan-embed-text-v2:0");
      EmbeddingModel model = (EmbeddingModel) builder.getClass().getMethod("build").invoke(builder);
      return new LangChain4JVectorizer("amazon.titan-embed-text-v2:0", model, 1024);
    } catch (Exception e) {
      assumeTrue(false, "AWS Bedrock dependencies not available: " + e.getMessage());
      return null;
    }
  }

  /** Helper to create HuggingFace vectorizer */
  private LangChain4JVectorizer createHuggingFaceVectorizer() {
    String apiKey = System.getenv("HUGGINGFACE_API_KEY");
    assumeTrue(apiKey != null && !apiKey.isEmpty(), "HUGGINGFACE_API_KEY not set");

    try {
      Class<?> hfClass =
          Class.forName("dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel");
      Object builder = hfClass.getMethod("builder").invoke(null);
      builder.getClass().getMethod("accessToken", String.class).invoke(builder, apiKey);
      builder
          .getClass()
          .getMethod("modelId", String.class)
          .invoke(builder, "sentence-transformers/all-MiniLM-L6-v2");
      EmbeddingModel model = (EmbeddingModel) builder.getClass().getMethod("build").invoke(builder);
      return new LangChain4JVectorizer("sentence-transformers/all-MiniLM-L6-v2", model, 384);
    } catch (Exception e) {
      assumeTrue(false, "HuggingFace dependencies not available: " + e.getMessage());
      return null;
    }
  }

  // ======================================================================================
  // OpenAI Tests
  // ======================================================================================

  @Nested
  @DisplayName("OpenAI Vectorizer Tests")
  class OpenAITests {

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    @DisplayName("Should embed single text with OpenAI")
    void testOpenAIEmbedSingle() {
      LangChain4JVectorizer vectorizer = createOpenAIVectorizer();

      float[] embedding = vectorizer.embed(TEST_TEXT);

      assertThat(embedding).isNotNull();
      assertThat(embedding.length).isEqualTo(1536);
      assertThat(embedding).isNotEqualTo(new float[1536]); // Not all zeros
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    @DisplayName("Should embed multiple texts with OpenAI")
    void testOpenAIEmbedMany() {
      LangChain4JVectorizer vectorizer = createOpenAIVectorizer();

      List<float[]> embeddings = vectorizer.embedBatch(TEST_TEXTS);

      assertThat(embeddings).isNotNull();
      assertThat(embeddings).hasSize(2);
      assertThat(embeddings.get(0).length).isEqualTo(1536);
      assertThat(embeddings.get(1).length).isEqualTo(1536);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    @DisplayName("Should handle bad input with OpenAI")
    void testOpenAIBadInput() {
      LangChain4JVectorizer vectorizer = createOpenAIVectorizer();

      assertThatThrownBy(() -> vectorizer.embed(null)).isInstanceOf(IllegalArgumentException.class);

      assertThatThrownBy(() -> vectorizer.embed("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    @DisplayName("Should work with cache for OpenAI")
    void testOpenAIWithCache() {
      EmbeddingsCache cache = new EmbeddingsCache("test-openai-cache", unifiedJedis);
      LangChain4JVectorizer vectorizer = createOpenAIVectorizer();
      vectorizer.setCache(cache);

      try {
        // First call - cache miss
        float[] first = vectorizer.embed(TEST_TEXT);
        assertThat(first).isNotNull();

        // Second call - cache hit
        float[] second = vectorizer.embed(TEST_TEXT);
        assertThat(second).isEqualTo(first);

        // Verify cache entry exists
        Optional<float[]> cached = cache.get(TEST_TEXT, vectorizer.getModelName());
        assertThat(cached).isPresent();
        assertThat(cached.get()).isEqualTo(first);
      } finally {
        cache.clear();
      }
    }
  }

  // ======================================================================================
  // Azure OpenAI Tests
  // ======================================================================================

  @Nested
  @DisplayName("Azure OpenAI Vectorizer Tests")
  class AzureOpenAITests {

    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
    @DisplayName("Should embed single text with Azure OpenAI")
    void testAzureOpenAIEmbedSingle() {
      LangChain4JVectorizer vectorizer = createAzureOpenAIVectorizer();

      float[] embedding = vectorizer.embed(TEST_TEXT);

      assertThat(embedding).isNotNull();
      assertThat(embedding.length).isEqualTo(1536);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
    @DisplayName("Should embed multiple texts with Azure OpenAI")
    void testAzureOpenAIEmbedMany() {
      LangChain4JVectorizer vectorizer = createAzureOpenAIVectorizer();

      List<float[]> embeddings = vectorizer.embedBatch(TEST_TEXTS);

      assertThat(embeddings).hasSize(2);
      assertThat(embeddings.get(0).length).isEqualTo(1536);
    }
  }

  // ======================================================================================
  // Cohere Tests
  // ======================================================================================

  @Nested
  @DisplayName("Cohere Vectorizer Tests")
  class CohereTests {

    @Test
    @EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
    @DisplayName("Should embed single text with Cohere")
    void testCohereEmbedSingle() {
      LangChain4JVectorizer vectorizer = createCohereVectorizer();

      float[] embedding = vectorizer.embed(TEST_TEXT);

      assertThat(embedding).isNotNull();
      assertThat(embedding.length).isEqualTo(1024);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
    @DisplayName("Should embed multiple texts with Cohere")
    void testCohereEmbedMany() {
      LangChain4JVectorizer vectorizer = createCohereVectorizer();

      List<float[]> embeddings = vectorizer.embedBatch(TEST_TEXTS);

      assertThat(embeddings).hasSize(2);
      assertThat(embeddings.get(0).length).isEqualTo(1024);
    }
  }

  // ======================================================================================
  // Vertex AI Tests
  // ======================================================================================

  @Nested
  @DisplayName("Vertex AI Vectorizer Tests")
  class VertexAITests {

    @Test
    @EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
    @DisplayName("Should embed single text with Vertex AI")
    void testVertexAIEmbedSingle() {
      LangChain4JVectorizer vectorizer = createVertexAIVectorizer();

      float[] embedding = vectorizer.embed(TEST_TEXT);

      assertThat(embedding).isNotNull();
      assertThat(embedding.length).isEqualTo(768);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
    @DisplayName("Should embed multiple texts with Vertex AI")
    void testVertexAIEmbedMany() {
      LangChain4JVectorizer vectorizer = createVertexAIVectorizer();

      List<float[]> embeddings = vectorizer.embedBatch(TEST_TEXTS);

      assertThat(embeddings).hasSize(2);
      assertThat(embeddings.get(0).length).isEqualTo(768);
    }
  }

  // ======================================================================================
  // Mistral AI Tests
  // ======================================================================================

  @Nested
  @DisplayName("Mistral AI Vectorizer Tests")
  class MistralAITests {

    @Test
    @EnabledIfEnvironmentVariable(named = "MISTRAL_API_KEY", matches = ".+")
    @DisplayName("Should embed single text with Mistral AI")
    void testMistralAIEmbedSingle() {
      LangChain4JVectorizer vectorizer = createMistralAIVectorizer();

      float[] embedding = vectorizer.embed(TEST_TEXT);

      assertThat(embedding).isNotNull();
      assertThat(embedding.length).isEqualTo(1024);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MISTRAL_API_KEY", matches = ".+")
    @DisplayName("Should embed multiple texts with Mistral AI")
    void testMistralAIEmbedMany() {
      LangChain4JVectorizer vectorizer = createMistralAIVectorizer();

      List<float[]> embeddings = vectorizer.embedBatch(TEST_TEXTS);

      assertThat(embeddings).hasSize(2);
      assertThat(embeddings.get(0).length).isEqualTo(1024);
    }
  }

  // ======================================================================================
  // Voyage AI Tests
  // ======================================================================================

  @Nested
  @DisplayName("Voyage AI Vectorizer Tests")
  class VoyageAITests {

    @Test
    @EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
    @DisplayName("Should embed single text with Voyage AI")
    void testVoyageAIEmbedSingle() {
      LangChain4JVectorizer vectorizer = createVoyageAIVectorizer();

      float[] embedding = vectorizer.embed(TEST_TEXT);

      assertThat(embedding).isNotNull();
      assertThat(embedding.length).isEqualTo(1536);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
    @DisplayName("Should embed multiple texts with Voyage AI")
    void testVoyageAIEmbedMany() {
      LangChain4JVectorizer vectorizer = createVoyageAIVectorizer();

      List<float[]> embeddings = vectorizer.embedBatch(TEST_TEXTS);

      assertThat(embeddings).hasSize(2);
      assertThat(embeddings.get(0).length).isEqualTo(1536);
    }
  }

  // ======================================================================================
  // AWS Bedrock Tests
  // ======================================================================================

  @Nested
  @DisplayName("AWS Bedrock Vectorizer Tests")
  class BedrockTests {

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_REGION", matches = ".+")
    @DisplayName("Should embed single text with AWS Bedrock")
    void testBedrockEmbedSingle() {
      LangChain4JVectorizer vectorizer = createBedrockVectorizer();

      float[] embedding = vectorizer.embed(TEST_TEXT);

      assertThat(embedding).isNotNull();
      assertThat(embedding.length).isEqualTo(1024);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AWS_REGION", matches = ".+")
    @DisplayName("Should embed multiple texts with AWS Bedrock")
    void testBedrockEmbedMany() {
      LangChain4JVectorizer vectorizer = createBedrockVectorizer();

      List<float[]> embeddings = vectorizer.embedBatch(TEST_TEXTS);

      assertThat(embeddings).hasSize(2);
      assertThat(embeddings.get(0).length).isEqualTo(1024);
    }
  }

  // ======================================================================================
  // HuggingFace Tests
  // ======================================================================================

  @Nested
  @DisplayName("HuggingFace Vectorizer Tests")
  class HuggingFaceTests {

    @Test
    @EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
    @DisplayName("Should embed single text with HuggingFace")
    void testHuggingFaceEmbedSingle() {
      LangChain4JVectorizer vectorizer = createHuggingFaceVectorizer();

      float[] embedding = vectorizer.embed(TEST_TEXT);

      assertThat(embedding).isNotNull();
      assertThat(embedding.length).isEqualTo(384);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "HUGGINGFACE_API_KEY", matches = ".+")
    @DisplayName("Should embed multiple texts with HuggingFace")
    void testHuggingFaceEmbedMany() {
      LangChain4JVectorizer vectorizer = createHuggingFaceVectorizer();

      List<float[]> embeddings = vectorizer.embedBatch(TEST_TEXTS);

      assertThat(embeddings).hasSize(2);
      assertThat(embeddings.get(0).length).isEqualTo(384);
    }
  }

  // ======================================================================================
  // Custom Vectorizer Tests
  // ======================================================================================

  @Nested
  @DisplayName("Custom Vectorizer Tests")
  class CustomVectorizerTests {

    @Test
    @DisplayName("Should create custom vectorizer by extending BaseVectorizer")
    void testCustomVectorizer() {
      // Create a custom vectorizer by extending BaseVectorizer
      BaseVectorizer customVectorizer =
          new BaseVectorizer("custom-model", 4, "float32") {
            @Override
            protected float[] generateEmbedding(String text) {
              // Simple custom implementation
              return new float[] {1.1f, 2.2f, 3.3f, 4.4f};
            }

            @Override
            protected List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize) {
              List<float[]> results = new ArrayList<>();
              for (String text : texts) {
                results.add(generateEmbedding(text));
              }
              return results;
            }
          };

      float[] embedding = customVectorizer.embed(TEST_TEXT);

      assertThat(embedding).isEqualTo(new float[] {1.1f, 2.2f, 3.3f, 4.4f});
      assertThat(customVectorizer.getModelName()).isEqualTo("custom-model");
      assertThat(customVectorizer.getDimensions()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should support batch embedding in custom vectorizer")
    void testCustomVectorizerBatch() {
      BaseVectorizer customVectorizer =
          new BaseVectorizer("custom-model", 3, "float32") {
            @Override
            protected float[] generateEmbedding(String text) {
              return new float[] {1.0f, 2.0f, 3.0f};
            }

            @Override
            protected List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize) {
              List<float[]> results = new ArrayList<>();
              for (String text : texts) {
                results.add(generateEmbedding(text));
              }
              return results;
            }
          };

      List<float[]> embeddings = customVectorizer.embedBatch(TEST_TEXTS);

      assertThat(embeddings).hasSize(2);
      assertThat(embeddings.get(0)).isEqualTo(new float[] {1.0f, 2.0f, 3.0f});
      assertThat(embeddings.get(1)).isEqualTo(new float[] {1.0f, 2.0f, 3.0f});
    }

    @Test
    @DisplayName("Should support preprocessing in custom vectorizer")
    void testCustomVectorizerWithPreprocessing() {
      BaseVectorizer customVectorizer =
          new BaseVectorizer("custom-model", 2, "float32") {
            @Override
            protected float[] generateEmbedding(String text) {
              // Simple embedding based on text length
              return new float[] {(float) text.length(), (float) text.split(" ").length};
            }

            @Override
            protected List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize) {
              List<float[]> results = new ArrayList<>();
              for (String text : texts) {
                results.add(generateEmbedding(text));
              }
              return results;
            }
          };

      // Use preprocess parameter in embed method
      float[] embedding =
          customVectorizer.embed("hello world", text -> text.toUpperCase(), false, false);

      assertThat(embedding[0]).isEqualTo(11.0f); // "HELLO WORLD".length()
      assertThat(embedding[1]).isEqualTo(2.0f); // 2 words
    }
  }
}
