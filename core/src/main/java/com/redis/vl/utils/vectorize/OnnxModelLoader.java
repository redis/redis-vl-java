package com.redis.vl.utils.vectorize;

import ai.onnxruntime.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads and runs ONNX models for generating embeddings. Handles tokenization, inference, and
 * post-processing.
 */
@Slf4j
public class OnnxModelLoader {

  private static final Gson GSON = new Gson();

  @Getter private int embeddingDimension;

  @Getter private int maxSequenceLength;

  private JsonObject tokenizerConfig;
  private Map<String, Integer> vocabulary;
  private OrtEnvironment environment;
  private boolean normalizeEmbeddings = true;

  public OnnxModelLoader() {
    // Environment will be provided when loading model
  }

  /** Load an ONNX model from the specified directory. */
  public OrtSession loadModel(Path modelDir) throws IOException, OrtException {
    return loadModel(modelDir, OrtEnvironment.getEnvironment());
  }

  /** Load an ONNX model from the specified directory with a specific environment. */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "OrtEnvironment is a singleton and immutable")
  public OrtSession loadModel(Path modelDir, OrtEnvironment env) throws IOException, OrtException {
    this.environment = env;
    log.info("Loading ONNX model from {}", modelDir);

    // Load config.json
    Path configPath = modelDir.resolve("config.json");
    if (!Files.exists(configPath)) {
      throw new IOException("config.json not found in " + modelDir);
    }

    String configJson = Files.readString(configPath);
    JsonObject config = GSON.fromJson(configJson, JsonObject.class);

    // Extract model parameters
    this.embeddingDimension = config.get("hidden_size").getAsInt();
    this.maxSequenceLength =
        config.has("max_position_embeddings")
            ? config.get("max_position_embeddings").getAsInt()
            : 512;

    // Load tokenizer
    Path tokenizerPath = modelDir.resolve("tokenizer.json");
    if (!Files.exists(tokenizerPath)) {
      throw new IOException("tokenizer.json not found in " + modelDir);
    }

    String tokenizerJson = Files.readString(tokenizerPath);
    this.tokenizerConfig = GSON.fromJson(tokenizerJson, JsonObject.class);
    this.vocabulary = loadVocabulary(tokenizerConfig);

    // Update max sequence length from tokenizer if available
    if (tokenizerConfig.has("truncation")) {
      JsonObject truncation = tokenizerConfig.getAsJsonObject("truncation");
      if (truncation.has("max_length")) {
        this.maxSequenceLength = truncation.get("max_length").getAsInt();
      }
    }

    // Load ONNX model
    Path modelPath = modelDir.resolve("model.onnx");
    if (!Files.exists(modelPath)) {
      throw new IOException("model.onnx not found in " + modelDir);
    }

    OrtSession.SessionOptions options = new OrtSession.SessionOptions();
    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

    OrtSession session = environment.createSession(modelPath.toString(), options);

    log.info(
        "Model loaded successfully. Embedding dimension: {}, Max sequence length: {}",
        embeddingDimension,
        maxSequenceLength);

    return session;
  }

  /** Get the hidden size (same as embedding dimension). */
  public int getHiddenSize() {
    return embeddingDimension;
  }

  /** Get a copy of the tokenizer configuration to prevent internal representation exposure. */
  public JsonObject getTokenizer() {
    // Return a deep copy to prevent external modification
    return tokenizerConfig.deepCopy();
  }

  /** Tokenize a single text string. */
  public long[][] tokenize(String text) {
    return tokenizeBatch(Collections.singletonList(text));
  }

  /** Tokenize a batch of text strings. */
  public long[][] tokenizeBatch(List<String> texts) {
    long[][] tokenIds = new long[texts.size()][];

    for (int i = 0; i < texts.size(); i++) {
      tokenIds[i] = tokenizeText(texts.get(i));
    }

    return tokenIds;
  }

  /** Run inference on the model with tokenized input. */
  public float[][] runInference(OrtSession session, OnnxTensor inputTensor) throws OrtException {
    // Check what inputs the model expects
    Map<String, OnnxTensor> inputs = new HashMap<>();
    inputs.put("input_ids", inputTensor);

    // Check if model expects token_type_ids (for BERT-style models)
    boolean needsTokenTypeIds = session.getInputInfo().containsKey("token_type_ids");
    if (needsTokenTypeIds) {
      // Create token_type_ids tensor (all zeros for single sentence)
      long[] shape = inputTensor.getInfo().getShape();
      long[][] tokenTypeIds = new long[(int) shape[0]][(int) shape[1]];
      // All zeros for single sentence input
      OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(environment, tokenTypeIds);
      inputs.put("token_type_ids", tokenTypeIdsTensor);
    }

    // Check if model expects attention_mask
    boolean needsAttentionMask = session.getInputInfo().containsKey("attention_mask");
    if (needsAttentionMask) {
      // Create attention_mask tensor (1s for real tokens, 0s for padding)
      long[] shape = inputTensor.getInfo().getShape();
      long[][] attentionMask = new long[(int) shape[0]][(int) shape[1]];
      // Set all to 1 for now (assuming no padding)
      for (int i = 0; i < shape[0]; i++) {
        for (int j = 0; j < shape[1]; j++) {
          attentionMask[i][j] = 1;
        }
      }
      OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(environment, attentionMask);
      inputs.put("attention_mask", attentionMaskTensor);
    }

    try (OrtSession.Result result = session.run(inputs)) {
      // Clean up additional tensors
      for (Map.Entry<String, OnnxTensor> entry : inputs.entrySet()) {
        if (!entry.getKey().equals("input_ids")) {
          entry.getValue().close();
        }
      }
      // Get the output tensor
      OnnxValue output = result.get(0);

      if (output instanceof OnnxTensor) {
        OnnxTensor outputTensor = (OnnxTensor) output;

        // Extract embeddings
        float[][][] tokenEmbeddings = (float[][][]) outputTensor.getValue();

        // Apply pooling (mean pooling by default)
        float[][] pooledEmbeddings = meanPooling(tokenEmbeddings);

        // Normalize if required
        if (normalizeEmbeddings) {
          pooledEmbeddings = normalize(pooledEmbeddings);
        }

        return pooledEmbeddings;
      } else {
        throw new RuntimeException("Unexpected output type: " + output.getClass());
      }
    }
  }

  /** Apply mean pooling to token embeddings. */
  public float[][] meanPooling(float[][][] tokenEmbeddings) {
    int batchSize = tokenEmbeddings.length;
    int hiddenSize = tokenEmbeddings[0][0].length;

    float[][] pooled = new float[batchSize][hiddenSize];

    for (int b = 0; b < batchSize; b++) {
      int sequenceLength = tokenEmbeddings[b].length;

      for (int s = 0; s < sequenceLength; s++) {
        for (int h = 0; h < hiddenSize; h++) {
          pooled[b][h] += tokenEmbeddings[b][s][h];
        }
      }

      // Average
      for (int h = 0; h < hiddenSize; h++) {
        pooled[b][h] /= sequenceLength;
      }
    }

    return pooled;
  }

  /** Normalize embeddings to unit length. */
  public float[][] normalize(float[][] embeddings) {
    for (int i = 0; i < embeddings.length; i++) {
      float magnitude = 0;

      // Calculate magnitude
      for (float value : embeddings[i]) {
        magnitude += value * value;
      }
      magnitude = (float) Math.sqrt(magnitude);

      // Normalize
      if (magnitude > 0) {
        for (int j = 0; j < embeddings[i].length; j++) {
          embeddings[i][j] /= magnitude;
        }
      }
    }

    return embeddings;
  }

  /** Get embedding for a single text. */
  public List<Float> getEmbedding(String text) throws OrtException {
    List<List<Float>> embeddings = getEmbeddings(Collections.singletonList(text));
    return embeddings.get(0);
  }

  /** Get embeddings for multiple texts. */
  public List<List<Float>> getEmbeddings(List<String> texts) throws OrtException {
    // This method would be implemented by SentenceTransformersVectorizer
    // which has access to the OrtSession
    throw new UnsupportedOperationException(
        "This method should be called through SentenceTransformersVectorizer");
  }

  private Map<String, Integer> loadVocabulary(JsonObject tokenizerConfig) {
    Map<String, Integer> vocab = new HashMap<>();

    if (tokenizerConfig.has("model") && tokenizerConfig.getAsJsonObject("model").has("vocab")) {
      JsonObject vocabObj = tokenizerConfig.getAsJsonObject("model").getAsJsonObject("vocab");

      for (Map.Entry<String, JsonElement> entry : vocabObj.entrySet()) {
        vocab.put(entry.getKey(), entry.getValue().getAsInt());
      }
    }

    log.debug("Loaded vocabulary with {} tokens", vocab.size());
    return vocab;
  }

  private long[] tokenizeText(String text) {
    // Simple tokenization - in production, this would use the full tokenizer logic
    // For now, we'll do basic word splitting and lookup

    List<Long> tokens = new ArrayList<>();

    // Add [CLS] token
    tokens.add(getTokenId("[CLS]", 101L));

    // Tokenize text (simplified - real implementation would use WordPiece/SentencePiece)
    String[] words = text.toLowerCase().split("\\s+");

    for (String word : words) {
      // Handle punctuation
      String[] parts = word.split("(?=[.,!?;:])|(?<=[.,!?;:])");
      for (String part : parts) {
        if (!part.isEmpty()) {
          tokens.add(getTokenId(part, 100L)); // 100 is typically [UNK]
        }
      }

      // Stop if we're approaching max length (leave room for [SEP])
      if (tokens.size() >= maxSequenceLength - 1) {
        break;
      }
    }

    // Add [SEP] token
    tokens.add(getTokenId("[SEP]", 102L));

    // Pad or truncate to max sequence length
    while (tokens.size() < maxSequenceLength) {
      tokens.add(getTokenId("[PAD]", 0L));
    }

    if (tokens.size() > maxSequenceLength) {
      tokens = tokens.subList(0, maxSequenceLength);
    }

    // Convert to array
    long[] tokenArray = new long[tokens.size()];
    for (int i = 0; i < tokens.size(); i++) {
      tokenArray[i] = tokens.get(i);
    }

    return tokenArray;
  }

  private long getTokenId(String token, long defaultId) {
    Integer id = vocabulary.get(token);
    return id != null ? id.longValue() : defaultId;
  }
}
