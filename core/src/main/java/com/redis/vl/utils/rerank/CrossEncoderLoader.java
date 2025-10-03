package com.redis.vl.utils.rerank;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads and runs ONNX cross-encoder models for document reranking.
 *
 * <p>Cross-encoders are different from embedding models: - Input: Query + Document pair (tokenized
 * together with token_type_ids) - Output: Single relevance score (not an embedding vector)
 *
 * <p>This loader handles BertForSequenceClassification models exported to ONNX.
 */
@Slf4j
public class CrossEncoderLoader {

  private static final Gson GSON = new Gson();

  @Getter private int maxSequenceLength;

  private HuggingFaceTokenizer tokenizer;
  private boolean usesTokenTypeIds = true; // BERT uses token_type_ids, XLMRoberta doesn't
  private String modelType; // "bert", "xlm-roberta", etc.

  /** Creates a new CrossEncoderLoader. */
  public CrossEncoderLoader() {
    // Configuration loaded when loading model
  }

  /**
   * Load an ONNX cross-encoder model from the specified directory.
   *
   * @param modelDir Path to the directory containing the ONNX model files
   * @param env The ONNX runtime environment to use
   * @return The loaded ONNX runtime session
   * @throws IOException if model files cannot be read
   * @throws OrtException if the ONNX runtime fails to load the model
   */
  public OrtSession loadModel(Path modelDir, OrtEnvironment env) throws IOException, OrtException {
    log.info("Loading ONNX cross-encoder model from {}", modelDir);

    // Load config.json
    Path configPath = modelDir.resolve("config.json");
    if (!Files.exists(configPath)) {
      throw new IOException("config.json not found in " + modelDir);
    }

    String configJson = Files.readString(configPath);
    JsonObject config = GSON.fromJson(configJson, JsonObject.class);

    // Detect model architecture to determine if token_type_ids are used
    if (config.has("model_type")) {
      this.modelType = config.get("model_type").getAsString();
      // XLMRoberta and RoBERTa models don't use token_type_ids
      if (modelType.contains("xlm-roberta") || modelType.contains("roberta")) {
        this.usesTokenTypeIds = false;
        log.info("Detected {} architecture - will not use token_type_ids", modelType);
      } else {
        log.info("Detected {} architecture - will use token_type_ids", modelType);
      }
    } else {
      this.modelType = "bert"; // Default to BERT
    }

    // Extract model parameters
    this.maxSequenceLength =
        config.has("max_position_embeddings")
            ? config.get("max_position_embeddings").getAsInt()
            : 512;

    // Load HuggingFace tokenizer from tokenizer.json
    Path tokenizerPath = modelDir.resolve("tokenizer.json");
    if (!Files.exists(tokenizerPath)) {
      throw new IOException("tokenizer.json not found in " + modelDir);
    }

    this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath, null);
    log.info("Loaded HuggingFace tokenizer from {}", tokenizerPath);

    // Load ONNX model
    Path onnxPath = modelDir.resolve("model.onnx");
    if (!Files.exists(onnxPath)) {
      throw new IOException("model.onnx not found in " + modelDir);
    }

    OrtSession.SessionOptions options = new OrtSession.SessionOptions();
    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

    OrtSession session = env.createSession(onnxPath.toString(), options);

    log.info("Cross-encoder model loaded successfully. Max sequence length: {}", maxSequenceLength);

    return session;
  }

  /**
   * Tokenize a query-document pair for cross-encoder input.
   *
   * <p>Uses HuggingFace tokenizer to properly encode the query-document pair with special tokens,
   * attention masks, and token type IDs.
   *
   * @param query The query text
   * @param document The document text
   * @return Map containing input_ids, token_type_ids, attention_mask
   */
  public Map<String, long[][]> tokenizePair(String query, String document) {
    // Use HuggingFace tokenizer to encode the pair
    // It automatically handles [CLS], [SEP], token_type_ids, and attention_mask
    Encoding encoding = tokenizer.encode(query, document);

    // Get token IDs
    long[] inputIds = encoding.getIds();

    // Get attention mask
    long[] attentionMask = encoding.getAttentionMask();

    // Get token type IDs (0 for query, 1 for document)
    long[] tokenTypeIds = encoding.getTypeIds();

    // Debug logging
    if (log.isDebugEnabled()) {
      log.debug("Query: {}", query.substring(0, Math.min(50, query.length())));
      log.debug("Document: {}", document.substring(0, Math.min(50, document.length())));
      log.debug(
          "Token IDs (first 20): {}",
          Arrays.toString(Arrays.copyOf(inputIds, Math.min(20, inputIds.length))));
      log.debug(
          "Token type IDs (first 20): {}",
          Arrays.toString(Arrays.copyOf(tokenTypeIds, Math.min(20, tokenTypeIds.length))));
      log.debug(
          "Attention mask (first 20): {}",
          Arrays.toString(Arrays.copyOf(attentionMask, Math.min(20, attentionMask.length))));
    }

    // Convert to 2D arrays (batch size 1)
    long[][] inputIdsArray = new long[1][inputIds.length];
    long[][] attentionMaskArray = new long[1][attentionMask.length];
    long[][] tokenTypeIdsArray = new long[1][tokenTypeIds.length];

    System.arraycopy(inputIds, 0, inputIdsArray[0], 0, inputIds.length);
    System.arraycopy(attentionMask, 0, attentionMaskArray[0], 0, attentionMask.length);
    System.arraycopy(tokenTypeIds, 0, tokenTypeIdsArray[0], 0, tokenTypeIds.length);

    Map<String, long[][]> result = new HashMap<>();
    result.put("input_ids", inputIdsArray);
    result.put("token_type_ids", tokenTypeIdsArray);
    result.put("attention_mask", attentionMaskArray);

    return result;
  }

  /**
   * Run inference to get relevance score.
   *
   * @param session The ONNX session
   * @param inputIds Input token IDs
   * @param tokenTypeIds Token type IDs (0 for query, 1 for document) - only used for BERT models
   * @param attentionMask Attention mask
   * @return Relevance score
   * @throws OrtException if inference fails
   */
  public float runInference(
      OrtSession session, long[][] inputIds, long[][] tokenTypeIds, long[][] attentionMask)
      throws OrtException {

    OrtEnvironment env = OrtEnvironment.getEnvironment();

    try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, inputIds);
        OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, attentionMask)) {

      Map<String, OnnxTensor> inputs = new HashMap<>();
      inputs.put("input_ids", inputIdsTensor);
      inputs.put("attention_mask", attentionMaskTensor);

      // Only add token_type_ids for BERT models (not for XLMRoberta/RoBERTa)
      if (usesTokenTypeIds) {
        try (OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(env, tokenTypeIds)) {
          inputs.put("token_type_ids", tokenTypeIdsTensor);

          try (OrtSession.Result results = session.run(inputs)) {
            float[][] logits = (float[][]) results.get(0).getValue();
            // Apply sigmoid activation to match sentence-transformers behavior
            return sigmoid(logits[0][0]);
          }
        }
      } else {
        // XLMRoberta doesn't use token_type_ids
        try (OrtSession.Result results = session.run(inputs)) {
          float[][] logits = (float[][]) results.get(0).getValue();
          // Apply sigmoid activation to match sentence-transformers behavior
          return sigmoid(logits[0][0]);
        }
      }
    }
  }

  /**
   * Apply sigmoid activation function to convert logits to probability scores [0, 1].
   *
   * @param logit Raw logit value from the model
   * @return Sigmoid-activated score in range [0, 1]
   */
  private float sigmoid(float logit) {
    return (float) (1.0 / (1.0 + Math.exp(-logit)));
  }
}
