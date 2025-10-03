package com.redis.vl.utils.rerank;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Debug test to compare tokenization with Python. */
@Tag("integration")
class TokenizationDebugTest {

  @Test
  void testTokenizationOutput() throws Exception {
    HFCrossEncoderReranker reranker =
        HFCrossEncoderReranker.builder().model("BAAI/bge-reranker-base").build();

    String query = "What is the capital of the United States?";
    String doc =
        "Washington, D.C. (also known as simply Washington or D.C., and officially as the District of Columbia) is the capital of the United States.";

    // Access the internal tokenizer through reflection
    java.lang.reflect.Field loaderField =
        HFCrossEncoderReranker.class.getDeclaredField("modelLoader");
    loaderField.setAccessible(true);
    CrossEncoderLoader loader = (CrossEncoderLoader) loaderField.get(reranker);

    Map<String, long[][]> tokens = loader.tokenizePair(query, doc);

    long[] inputIds = tokens.get("input_ids")[0];
    long[] tokenTypeIds = tokens.get("token_type_ids")[0];
    long[] attentionMask = tokens.get("attention_mask")[0];

    System.out.println("\n=== JAVA TOKENIZATION ===");
    System.out.println("Query: " + query.substring(0, Math.min(50, query.length())));
    System.out.println("Document: " + doc.substring(0, Math.min(50, doc.length())));
    System.out.println(
        "Token IDs (first 20): "
            + Arrays.toString(Arrays.copyOf(inputIds, Math.min(20, inputIds.length))));
    System.out.println(
        "Token type IDs (first 20): "
            + Arrays.toString(Arrays.copyOf(tokenTypeIds, Math.min(20, tokenTypeIds.length))));
    System.out.println(
        "Attention mask (first 20): "
            + Arrays.toString(Arrays.copyOf(attentionMask, Math.min(20, attentionMask.length))));
    System.out.println("Total tokens: " + inputIds.length);

    System.out.println("\n=== EXPECTED PYTHON TOKENIZATION ===");
    System.out.println(
        "Token IDs (first 20): [0, 4865, 83, 70, 10323, 111, 70, 14098, 46684, 32, 2, 2, 17955, 4, 391, 5, 441, 5, 15, 289]");
    System.out.println("Token type IDs: N/A (XLMRoberta doesn't use them)");
    System.out.println("Total tokens: 49");

    reranker.close();
  }
}
