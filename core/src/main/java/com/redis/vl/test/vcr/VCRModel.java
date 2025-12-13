package com.redis.vl.test.vcr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be automatically wrapped with VCR recording/playback functionality.
 *
 * <p>When applied to an {@code EmbeddingModel} or {@code ChatModel} field, the VCR extension will
 * automatically wrap the model with the appropriate VCR wrapper after it's initialized.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @VCRTest(mode = VCRMode.PLAYBACK_OR_RECORD)
 * class MyLLMTest {
 *
 *     @VCRModel
 *     private EmbeddingModel embeddingModel;
 *
 *     @VCRModel
 *     private ChatModel chatModel;
 *
 *     @BeforeEach
 *     void setup() {
 *         // Initialize your models normally - VCR will wrap them automatically
 *         embeddingModel = new OpenAiEmbeddingModel(...);
 *         chatModel = new OpenAiChatModel(...);
 *     }
 *
 *     @Test
 *     void testEmbedding() {
 *         // Use the model - calls are recorded/replayed transparently
 *         float[] embedding = embeddingModel.embed("Hello").content();
 *     }
 * }
 * }</pre>
 *
 * <p>Supported model types:
 *
 * <ul>
 *   <li>LangChain4J: {@code dev.langchain4j.model.embedding.EmbeddingModel}, {@code
 *       dev.langchain4j.model.chat.ChatLanguageModel}
 *   <li>Spring AI: {@code org.springframework.ai.embedding.EmbeddingModel}, {@code
 *       org.springframework.ai.chat.model.ChatModel}
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface VCRModel {

  /**
   * Optional model name for embedding cache key generation. If not specified, the field name will
   * be used.
   */
  String modelName() default "";
}
