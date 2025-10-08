package com.redis.vl.utils.rerank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Test to validate cross-encoder tokenization matches Python transformers library.
 *
 * <p>Compares Java WordPiece tokenization against Python reference values to ensure embeddings and
 * reranking scores match Python implementation.
 */
@Tag("integration")
class CrossEncoderTokenizationTest {

  @Test
  void testTokenizationMatchesPython() throws Exception {
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

    // Expected token IDs from Python transformers tokenizer
    // Generated with: tokenizer("What is the capital...", "Washington, D.C...")
    long[] expectedTokenIds = {
      0, 4865, 83, 70, 10323, 111, 70, 14098, 46684, 32, 2, 2, 17955, 4, 391, 5, 441, 5, 15, 289
    };

    // Validate token IDs match Python (first 20 tokens)
    long[] actualFirst20 = Arrays.copyOf(inputIds, Math.min(20, inputIds.length));
    assertThat(actualFirst20)
        .as("Token IDs should match Python transformers tokenizer")
        .containsExactly(expectedTokenIds);

    // Validate total token count matches Python
    assertThat(inputIds.length).as("Total tokens should match Python tokenization").isEqualTo(49);

    // Validate attention mask is correct (all 1s for non-padding tokens)
    for (int i = 0; i < attentionMask.length; i++) {
      assertThat(attentionMask[i])
          .as("Attention mask[%d] should be 1 (no padding)", i)
          .isEqualTo(1);
    }

    // XLM-Roberta doesn't use token type IDs, so they should all be 0
    for (int i = 0; i < tokenTypeIds.length; i++) {
      assertThat(tokenTypeIds[i])
          .as("Token type ID[%d] should be 0 for XLM-Roberta", i)
          .isEqualTo(0);
    }

    reranker.close();
  }
}
