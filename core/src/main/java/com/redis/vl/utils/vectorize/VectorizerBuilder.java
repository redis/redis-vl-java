package com.redis.vl.utils.vectorize;

import com.redis.vl.extensions.cache.EmbeddingsCache;

/**
 * Factory class for creating vectorizers with common providers. This builder provides convenience
 * methods to create vectorizers for popular embedding providers supported by LangChain4J.
 *
 * <p>Example usage:
 *
 * <pre>
 * // OpenAI vectorizer
 * var vectorizer = VectorizerBuilder.openAI("your-api-key")
 *     .withCache(cache)
 *     .build();
 *
 * // Local model vectorizer
 * var vectorizer = VectorizerBuilder.local("all-minilm-l6-v2")
 *     .withCache(cache)
 *     .build();
 *
 * // Custom LangChain4J model
 * EmbeddingModel customModel = new MyCustomEmbeddingModel();
 * var vectorizer = VectorizerBuilder.custom("my-model", customModel)
 *     .build();
 * </pre>
 */
public class VectorizerBuilder {

  private VectorizerBuilder() {
    // Private constructor to prevent instantiation
  }

  /**
   * Create a builder for OpenAI embeddings. Requires: dev.langchain4j:langchain4j-open-ai
   *
   * @param apiKey The OpenAI API key
   * @return Builder for OpenAI vectorizer
   */
  public static OpenAIVectorizerBuilder openAI(String apiKey) {
    return new OpenAIVectorizerBuilder(apiKey);
  }

  /**
   * Create a builder for Azure OpenAI embeddings. Requires:
   * dev.langchain4j:langchain4j-azure-open-ai
   *
   * @param endpoint The Azure OpenAI endpoint
   * @param apiKey The Azure OpenAI API key
   * @return Builder for Azure OpenAI vectorizer
   */
  public static AzureOpenAIVectorizerBuilder azure(String endpoint, String apiKey) {
    return new AzureOpenAIVectorizerBuilder(endpoint, apiKey);
  }

  /**
   * Create a builder for HuggingFace embeddings. Requires: dev.langchain4j:langchain4j-hugging-face
   *
   * @param apiKey The HuggingFace API key
   * @return Builder for HuggingFace vectorizer
   */
  public static HuggingFaceVectorizerBuilder huggingFace(String apiKey) {
    return new HuggingFaceVectorizerBuilder(apiKey);
  }

  /**
   * Create a builder for Ollama embeddings. Requires: dev.langchain4j:langchain4j-ollama
   *
   * @param baseUrl The Ollama base URL (e.g., "http://localhost:11434")
   * @return Builder for Ollama vectorizer
   */
  public static OllamaVectorizerBuilder ollama(String baseUrl) {
    return new OllamaVectorizerBuilder(baseUrl);
  }

  /**
   * Create a builder for local embedding models. Available models: - "all-minilm-l6-v2" (requires:
   * langchain4j-embeddings-all-minilm-l6-v2) - "bge-small-en-v15" (requires:
   * langchain4j-embeddings-bge-small-en-v15) - "e5-small-v2" (requires:
   * langchain4j-embeddings-e5-small-v2)
   *
   * @param modelName The name of the local model
   * @return Builder for local model vectorizer
   */
  public static LocalVectorizerBuilder local(String modelName) {
    return new LocalVectorizerBuilder(modelName);
  }

  /**
   * Create a builder for a custom LangChain4J EmbeddingModel.
   *
   * @param modelName The name to use for this model
   * @param embeddingModel The LangChain4J embedding model instance
   * @return Builder for custom vectorizer
   */
  public static CustomVectorizerBuilder custom(String modelName, Object embeddingModel) {
    return new CustomVectorizerBuilder(modelName, embeddingModel);
  }

  // Abstract base builder
  public abstract static class AbstractVectorizerBuilder<T extends AbstractVectorizerBuilder<T>> {
    protected String modelName;
    protected EmbeddingsCache cache;
    protected Integer dimensions;
    protected String dtype = "float32";

    protected AbstractVectorizerBuilder(String modelName) {
      this.modelName = modelName;
    }

    /**
     * Set the embeddings cache to use.
     *
     * @param cache The embeddings cache
     * @return This builder
     */
    @SuppressWarnings("unchecked")
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "EmbeddingsCache is designed to be shared and is thread-safe")
    public T withCache(EmbeddingsCache cache) {
      this.cache = cache;
      return (T) this;
    }

    /**
     * Set the expected dimensions (optional - will auto-detect if not specified).
     *
     * @param dimensions The number of dimensions
     * @return This builder
     */
    @SuppressWarnings("unchecked")
    public T withDimensions(int dimensions) {
      this.dimensions = dimensions;
      return (T) this;
    }

    /**
     * Set the data type for embeddings.
     *
     * @param dtype The data type (default: "float32")
     * @return This builder
     */
    @SuppressWarnings("unchecked")
    public T withDataType(String dtype) {
      this.dtype = dtype;
      return (T) this;
    }

    /**
     * Build the vectorizer.
     *
     * @return The configured vectorizer
     * @throws RuntimeException if required dependencies are missing
     */
    public abstract BaseVectorizer build();
  }

  // Specific builders for different providers
  public static class OpenAIVectorizerBuilder
      extends AbstractVectorizerBuilder<OpenAIVectorizerBuilder> {
    private final String apiKey;
    private String model = "text-embedding-ada-002";
    private String baseUrl;
    private String organizationId;

    private OpenAIVectorizerBuilder(String apiKey) {
      super("openai");
      this.apiKey = apiKey;
    }

    public OpenAIVectorizerBuilder model(String model) {
      this.model = model;
      this.modelName = model;
      return this;
    }

    public OpenAIVectorizerBuilder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public OpenAIVectorizerBuilder organizationId(String organizationId) {
      this.organizationId = organizationId;
      return this;
    }

    @Override
    public BaseVectorizer build() {
      try {
        // Use reflection to create OpenAI model to avoid hard dependency
        Class<?> builderClass =
            Class.forName(
                "dev.langchain4j.model.openai.OpenAiEmbeddingModel$OpenAiEmbeddingModelBuilder");
        Object builder =
            Class.forName("dev.langchain4j.model.openai.OpenAiEmbeddingModel")
                .getMethod("builder")
                .invoke(null);

        // Set API key
        builderClass.getMethod("apiKey", String.class).invoke(builder, apiKey);

        // Set model name
        builderClass.getMethod("modelName", String.class).invoke(builder, model);

        // Set optional parameters
        if (baseUrl != null) {
          builderClass.getMethod("baseUrl", String.class).invoke(builder, baseUrl);
        }
        if (organizationId != null) {
          builderClass.getMethod("organizationId", String.class).invoke(builder, organizationId);
        }

        Object embeddingModel = builderClass.getMethod("build").invoke(builder);

        LangChain4JVectorizer vectorizer;
        if (dimensions != null) {
          vectorizer =
              new LangChain4JVectorizer(
                  modelName,
                  (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel,
                  dimensions,
                  dtype);
        } else {
          vectorizer =
              new LangChain4JVectorizer(
                  modelName, (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel);
        }

        if (cache != null) {
          vectorizer.setCache(cache);
        }

        return vectorizer;
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to create OpenAI vectorizer. Make sure 'dev.langchain4j:langchain4j-open-ai' is on the classpath.",
            e);
      }
    }
  }

  public static class AzureOpenAIVectorizerBuilder
      extends AbstractVectorizerBuilder<AzureOpenAIVectorizerBuilder> {
    private final String endpoint;
    private final String apiKey;
    private String deploymentName = "text-embedding-ada-002";

    private AzureOpenAIVectorizerBuilder(String endpoint, String apiKey) {
      super("azure-openai");
      this.endpoint = endpoint;
      this.apiKey = apiKey;
    }

    public AzureOpenAIVectorizerBuilder deploymentName(String deploymentName) {
      this.deploymentName = deploymentName;
      this.modelName = deploymentName;
      return this;
    }

    @Override
    public BaseVectorizer build() {
      try {
        Class<?> builderClass =
            Class.forName(
                "dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel$AzureOpenAiEmbeddingModelBuilder");
        Object builder =
            Class.forName("dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel")
                .getMethod("builder")
                .invoke(null);

        builderClass.getMethod("endpoint", String.class).invoke(builder, endpoint);
        builderClass.getMethod("apiKey", String.class).invoke(builder, apiKey);
        builderClass.getMethod("deploymentName", String.class).invoke(builder, deploymentName);

        Object embeddingModel = builderClass.getMethod("build").invoke(builder);

        LangChain4JVectorizer vectorizer;
        if (dimensions != null) {
          vectorizer =
              new LangChain4JVectorizer(
                  modelName,
                  (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel,
                  dimensions,
                  dtype);
        } else {
          vectorizer =
              new LangChain4JVectorizer(
                  modelName, (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel);
        }

        if (cache != null) {
          vectorizer.setCache(cache);
        }

        return vectorizer;
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to create Azure OpenAI vectorizer. Make sure 'dev.langchain4j:langchain4j-azure-open-ai' is on the classpath.",
            e);
      }
    }
  }

  public static class HuggingFaceVectorizerBuilder
      extends AbstractVectorizerBuilder<HuggingFaceVectorizerBuilder> {
    private final String apiKey;
    private String model = "sentence-transformers/all-mpnet-base-v2";

    private HuggingFaceVectorizerBuilder(String apiKey) {
      super("huggingface");
      this.apiKey = apiKey;
    }

    public HuggingFaceVectorizerBuilder model(String model) {
      this.model = model;
      this.modelName = model;
      return this;
    }

    @Override
    public BaseVectorizer build() {
      try {
        Class<?> builderClass =
            Class.forName(
                "dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel$HuggingFaceEmbeddingModelBuilder");
        Object builder =
            Class.forName("dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel")
                .getMethod("builder")
                .invoke(null);

        builderClass.getMethod("accessToken", String.class).invoke(builder, apiKey);
        builderClass.getMethod("modelId", String.class).invoke(builder, model);

        Object embeddingModel = builderClass.getMethod("build").invoke(builder);

        LangChain4JVectorizer vectorizer;
        if (dimensions != null) {
          vectorizer =
              new LangChain4JVectorizer(
                  modelName,
                  (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel,
                  dimensions,
                  dtype);
        } else {
          vectorizer =
              new LangChain4JVectorizer(
                  modelName, (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel);
        }

        if (cache != null) {
          vectorizer.setCache(cache);
        }

        return vectorizer;
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to create HuggingFace vectorizer. Make sure 'dev.langchain4j:langchain4j-hugging-face' is on the classpath.",
            e);
      }
    }
  }

  public static class OllamaVectorizerBuilder
      extends AbstractVectorizerBuilder<OllamaVectorizerBuilder> {
    private final String baseUrl;
    private String model = "nomic-embed-text";

    private OllamaVectorizerBuilder(String baseUrl) {
      super("ollama");
      this.baseUrl = baseUrl;
    }

    public OllamaVectorizerBuilder model(String model) {
      this.model = model;
      this.modelName = model;
      return this;
    }

    @Override
    public BaseVectorizer build() {
      try {
        Class<?> builderClass =
            Class.forName(
                "dev.langchain4j.model.ollama.OllamaEmbeddingModel$OllamaEmbeddingModelBuilder");
        Object builder =
            Class.forName("dev.langchain4j.model.ollama.OllamaEmbeddingModel")
                .getMethod("builder")
                .invoke(null);

        builderClass.getMethod("baseUrl", String.class).invoke(builder, baseUrl);
        builderClass.getMethod("modelName", String.class).invoke(builder, model);

        Object embeddingModel = builderClass.getMethod("build").invoke(builder);

        LangChain4JVectorizer vectorizer;
        if (dimensions != null) {
          vectorizer =
              new LangChain4JVectorizer(
                  modelName,
                  (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel,
                  dimensions,
                  dtype);
        } else {
          vectorizer =
              new LangChain4JVectorizer(
                  modelName, (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel);
        }

        if (cache != null) {
          vectorizer.setCache(cache);
        }

        return vectorizer;
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to create Ollama vectorizer. Make sure 'dev.langchain4j:langchain4j-ollama' is on the classpath.",
            e);
      }
    }
  }

  public static class LocalVectorizerBuilder
      extends AbstractVectorizerBuilder<LocalVectorizerBuilder> {
    private final String modelName;

    private LocalVectorizerBuilder(String modelName) {
      super(modelName);
      this.modelName = modelName;
    }

    @Override
    public BaseVectorizer build() {
      try {
        Object embeddingModel;
        int defaultDimensions;

        switch (modelName.toLowerCase()) {
          case "all-minilm-l6-v2":
            embeddingModel =
                Class.forName(
                        "dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel")
                    .getDeclaredConstructor()
                    .newInstance();
            defaultDimensions = 384;
            break;
          case "bge-small-en-v15":
            embeddingModel =
                Class.forName(
                        "dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel")
                    .getDeclaredConstructor()
                    .newInstance();
            defaultDimensions = 384;
            break;
          case "e5-small-v2":
            embeddingModel =
                Class.forName(
                        "dev.langchain4j.model.embedding.onnx.e5smallv2.E5SmallV2EmbeddingModel")
                    .getDeclaredConstructor()
                    .newInstance();
            defaultDimensions = 384;
            break;
          default:
            throw new IllegalArgumentException(
                "Unsupported local model: "
                    + modelName
                    + ". Available models: all-minilm-l6-v2, bge-small-en-v15, e5-small-v2");
        }

        int finalDimensions = dimensions != null ? dimensions : defaultDimensions;
        LangChain4JVectorizer vectorizer =
            new LangChain4JVectorizer(
                modelName,
                (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel,
                finalDimensions,
                dtype);

        if (cache != null) {
          vectorizer.setCache(cache);
        }

        return vectorizer;
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(
            "Failed to create local vectorizer for model '"
                + modelName
                + "'. Make sure the corresponding langchain4j-embeddings dependency is on the classpath.",
            e);
      } catch (Exception e) {
        throw new RuntimeException("Failed to create local vectorizer for model: " + modelName, e);
      }
    }
  }

  public static class CustomVectorizerBuilder
      extends AbstractVectorizerBuilder<CustomVectorizerBuilder> {
    private final Object embeddingModel;

    private CustomVectorizerBuilder(String modelName, Object embeddingModel) {
      super(modelName);
      this.embeddingModel = embeddingModel;
    }

    @Override
    public BaseVectorizer build() {
      if (!(embeddingModel instanceof dev.langchain4j.model.embedding.EmbeddingModel)) {
        throw new IllegalArgumentException(
            "embeddingModel must implement dev.langchain4j.model.embedding.EmbeddingModel");
      }

      LangChain4JVectorizer vectorizer;
      if (dimensions != null) {
        vectorizer =
            new LangChain4JVectorizer(
                modelName,
                (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel,
                dimensions,
                dtype);
      } else {
        vectorizer =
            new LangChain4JVectorizer(
                modelName, (dev.langchain4j.model.embedding.EmbeddingModel) embeddingModel);
      }

      if (cache != null) {
        vectorizer.setCache(cache);
      }

      return vectorizer;
    }
  }
}
