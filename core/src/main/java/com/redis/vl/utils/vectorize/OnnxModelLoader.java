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

  // Special token IDs (loaded from tokenizer config)
  private long clsTokenId;
  private long sepTokenId;
  private long padTokenId;
  private long unkTokenId;

  /**
   * Creates a new OnnxModelLoader. The ONNX runtime environment will be provided when loading the
   * model.
   */
  public OnnxModelLoader() {
    // Environment will be provided when loading model
  }

  /**
   * Load an ONNX model from the specified directory.
   *
   * @param modelDir Path to the directory containing the ONNX model files
   * @return The loaded ONNX runtime session
   * @throws IOException if model files cannot be read
   * @throws OrtException if the ONNX runtime fails to load the model
   */
  public OrtSession loadModel(Path modelDir) throws IOException, OrtException {
    return loadModel(modelDir, OrtEnvironment.getEnvironment());
  }

  /**
   * Load an ONNX model from the specified directory with a specific environment.
   *
   * @param modelDir Path to the directory containing the ONNX model files
   * @param env The ONNX runtime environment to use
   * @return The loaded ONNX runtime session
   * @throws IOException if model files cannot be read
   * @throws OrtException if the ONNX runtime fails to load the model
   */
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

    // Load special token IDs from vocabulary
    loadSpecialTokenIds();

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

  /**
   * Get the hidden size (same as embedding dimension).
   *
   * @return The hidden size of the model
   */
  public int getHiddenSize() {
    return embeddingDimension;
  }

  /**
   * Get a copy of the tokenizer configuration to prevent internal representation exposure.
   *
   * @return A deep copy of the tokenizer configuration
   */
  public JsonObject getTokenizer() {
    // Return a deep copy to prevent external modification
    return tokenizerConfig.deepCopy();
  }

  /**
   * Tokenize a single text string.
   *
   * @param text The text to tokenize
   * @return A 2D array of token IDs (batch size 1)
   */
  public long[][] tokenize(String text) {
    return tokenizeBatch(Collections.singletonList(text));
  }

  /**
   * Tokenize a batch of text strings.
   *
   * @param texts List of texts to tokenize
   * @return A 2D array of token IDs, one row per input text
   */
  public long[][] tokenizeBatch(List<String> texts) {
    long[][] tokenIds = new long[texts.size()][];

    for (int i = 0; i < texts.size(); i++) {
      tokenIds[i] = tokenizeText(texts.get(i));
    }

    return tokenIds;
  }

  /**
   * Run inference on the model with tokenized input.
   *
   * @param session The ONNX runtime session
   * @param inputTensor The input tensor containing token IDs
   * @return 2D array of embeddings, one row per input
   * @throws OrtException if inference fails
   */
  public float[][] runInference(OrtSession session, OnnxTensor inputTensor) throws OrtException {
    // Check what inputs the model expects
    Map<String, OnnxTensor> inputs = new HashMap<>();
    inputs.put("input_ids", inputTensor);

    long[] shape = inputTensor.getInfo().getShape();
    long[][] attentionMask = null;

    // Check if model expects token_type_ids (for BERT-style models)
    boolean needsTokenTypeIds = session.getInputInfo().containsKey("token_type_ids");
    if (needsTokenTypeIds) {
      // Create token_type_ids tensor (all zeros for single sentence)
      long[][] tokenTypeIds = new long[(int) shape[0]][(int) shape[1]];
      // All zeros for single sentence input
      OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(environment, tokenTypeIds);
      inputs.put("token_type_ids", tokenTypeIdsTensor);
    }

    // Check if model expects attention_mask
    boolean needsAttentionMask = session.getInputInfo().containsKey("attention_mask");
    if (needsAttentionMask) {
      // Create attention_mask tensor (1s for real tokens, 0s for padding)
      // Extract token IDs from input tensor to detect padding
      long[][] tokenIds = (long[][]) inputTensor.getValue();
      attentionMask = new long[(int) shape[0]][(int) shape[1]];

      for (int i = 0; i < shape[0]; i++) {
        for (int j = 0; j < shape[1]; j++) {
          // Set to 1 for non-padding tokens, 0 for padding
          // Use padTokenId (e.g., 1 for MPNet, 0 for BERT) to detect padding
          attentionMask[i][j] = tokenIds[i][j] != padTokenId ? 1 : 0;
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

        // Apply attention-masked mean pooling (Sentence Transformers style)
        float[][] pooledEmbeddings = meanPoolingWithAttention(tokenEmbeddings, attentionMask);

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

  /**
   * Apply attention-masked mean pooling to token embeddings (Sentence Transformers style). Only
   * averages over non-padding tokens where attention_mask == 1.
   *
   * @param tokenEmbeddings 3D array of token embeddings [batch, sequence, hidden]
   * @param attentionMask 2D array of attention mask [batch, sequence] (1=real token, 0=padding)
   * @return 2D array of pooled embeddings [batch, hidden]
   */
  public float[][] meanPoolingWithAttention(float[][][] tokenEmbeddings, long[][] attentionMask) {
    int batchSize = tokenEmbeddings.length;
    int hiddenSize = tokenEmbeddings[0][0].length;

    float[][] pooled = new float[batchSize][hiddenSize];

    for (int b = 0; b < batchSize; b++) {
      int sequenceLength = tokenEmbeddings[b].length;
      int validTokenCount = 0;

      // Sum only non-masked tokens
      for (int s = 0; s < sequenceLength; s++) {
        if (attentionMask != null && attentionMask[b][s] == 1) {
          validTokenCount++;
          for (int h = 0; h < hiddenSize; h++) {
            pooled[b][h] += tokenEmbeddings[b][s][h];
          }
        } else if (attentionMask == null) {
          // No mask provided, count all tokens
          validTokenCount++;
          for (int h = 0; h < hiddenSize; h++) {
            pooled[b][h] += tokenEmbeddings[b][s][h];
          }
        }
      }

      // Average over valid tokens only
      if (validTokenCount > 0) {
        for (int h = 0; h < hiddenSize; h++) {
          pooled[b][h] /= validTokenCount;
        }
      }
    }

    return pooled;
  }

  /**
   * Apply mean pooling to token embeddings (legacy method).
   *
   * @param tokenEmbeddings 3D array of token embeddings [batch, sequence, hidden]
   * @return 2D array of pooled embeddings [batch, hidden]
   * @deprecated Use meanPoolingWithAttention for correct sentence-transformers behavior
   */
  @Deprecated
  public float[][] meanPooling(float[][][] tokenEmbeddings) {
    return meanPoolingWithAttention(tokenEmbeddings, null);
  }

  /**
   * Normalize embeddings to unit length.
   *
   * @param embeddings 2D array of embeddings to normalize
   * @return Normalized embeddings with unit length
   */
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

  /**
   * Get embedding for a single text.
   *
   * @param text The text to generate an embedding for
   * @return List of floats representing the embedding vector
   * @throws OrtException if inference fails
   */
  public List<Float> getEmbedding(String text) throws OrtException {
    List<List<Float>> embeddings = getEmbeddings(Collections.singletonList(text));
    return embeddings.get(0);
  }

  /**
   * Get embeddings for multiple texts.
   *
   * @param texts List of texts to generate embeddings for
   * @return List of embedding vectors
   * @throws OrtException if inference fails
   */
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

  /**
   * Load special token IDs from the vocabulary. Uses common default values if tokens are not found
   * in the vocabulary.
   */
  private void loadSpecialTokenIds() {
    // MPNet-style tokens (used by all-mpnet-base-v2)
    this.clsTokenId = getTokenId("<s>", 0L);
    this.sepTokenId = getTokenId("</s>", 2L);
    this.padTokenId = getTokenId("<pad>", 1L);
    this.unkTokenId = getTokenId("<unk>", 104L);

    log.debug(
        "Special tokens: CLS={}, SEP={}, PAD={}, UNK={}",
        clsTokenId,
        sepTokenId,
        padTokenId,
        unkTokenId);
  }

  private long[] tokenizeText(String text) {
    List<Long> tokens = new ArrayList<>();
    List<String> tokenStrings = new ArrayList<>(); // For debugging

    // Add [CLS] token (or <s> for MPNet)
    tokens.add(clsTokenId);
    tokenStrings.add("<CLS>:" + clsTokenId);

    // Basic tokenization: split on whitespace and punctuation
    List<String> basicTokens = basicTokenize(text);

    // Apply WordPiece tokenization to each basic token
    for (String token : basicTokens) {
      if (token.isEmpty()) {
        continue;
      }

      List<String> wordPieceTokens = wordPieceTokenize(token);
      for (String subToken : wordPieceTokens) {
        long tokenId = getTokenId(subToken, unkTokenId);
        tokens.add(tokenId);
        tokenStrings.add(subToken + ":" + tokenId);

        // Stop if we're approaching max length (leave room for [SEP])
        if (tokens.size() >= maxSequenceLength - 1) {
          break;
        }
      }

      if (tokens.size() >= maxSequenceLength - 1) {
        break;
      }
    }

    // Add [SEP] token (or </s> for MPNet)
    tokens.add(sepTokenId);
    tokenStrings.add("<SEP>:" + sepTokenId);

    // Debug logging
    log.debug("Tokenized '{}' -> {}", text, String.join(" ", tokenStrings));

    // Pad or truncate to max sequence length
    while (tokens.size() < maxSequenceLength) {
      tokens.add(padTokenId);
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

  /**
   * Basic tokenization: lowercase, split on whitespace and punctuation.
   *
   * @param text Input text
   * @return List of basic tokens
   */
  private List<String> basicTokenize(String text) {
    List<String> tokens = new ArrayList<>();

    // Lowercase the text
    text = text.toLowerCase();

    // Split on whitespace
    String[] words = text.split("\\s+");

    for (String word : words) {
      if (word.isEmpty()) {
        continue;
      }

      // Split punctuation into separate tokens
      // This regex splits before/after punctuation characters
      String[] parts = word.split("((?<=[\\p{Punct}])|(?=[\\p{Punct}]))");

      for (String part : parts) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          tokens.add(trimmed);
        }
      }
    }

    return tokens;
  }

  /**
   * Apply WordPiece tokenization to a single word. Implements the greedy longest-match-first
   * algorithm used by BERT and similar models.
   *
   * @param word The word to tokenize
   * @return List of WordPiece tokens (may include "##" prefixes for subwords)
   */
  private List<String> wordPieceTokenize(String word) {
    List<String> tokens = new ArrayList<>();

    if (word.isEmpty()) {
      return tokens;
    }

    // If the whole word is in vocabulary, return it
    if (vocabulary.containsKey(word)) {
      tokens.add(word);
      return tokens;
    }

    // Apply greedy longest-match-first WordPiece tokenization
    int start = 0;
    boolean isBad = false;

    while (start < word.length()) {
      int end = word.length();
      String currentSubToken = null;

      // Find the longest substring starting at 'start' that exists in vocabulary
      while (start < end) {
        String substr = word.substring(start, end);

        // Add "##" prefix for continuation tokens (not the first subtoken)
        if (start > 0) {
          substr = "##" + substr;
        }

        if (vocabulary.containsKey(substr)) {
          currentSubToken = substr;
          break;
        }

        end--;
      }

      // If no valid subtoken found, mark as bad and use [UNK]
      if (currentSubToken == null) {
        isBad = true;
        break;
      }

      tokens.add(currentSubToken);
      start = end;
    }

    // If tokenization failed, return [UNK]
    if (isBad || tokens.isEmpty()) {
      tokens.clear();
      tokens.add("[UNK]");
    }

    return tokens;
  }

  private long getTokenId(String token, long defaultId) {
    Integer id = vocabulary.get(token);
    return id != null ? id.longValue() : defaultId;
  }
}
