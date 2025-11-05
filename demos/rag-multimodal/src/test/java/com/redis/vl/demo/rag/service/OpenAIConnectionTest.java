package com.redis.vl.demo.rag.service;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Simple test to verify OpenAI API key is valid before running full integration tests.
 *
 * <p>Run with: OPENAI_API_KEY=your-key ./gradlew test --tests "OpenAIConnectionTest"
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAIConnectionTest {

  @Test
  void testOpenAIConnectionWorks() {
    // Given: API key from environment
    String apiKey = System.getenv("OPENAI_API_KEY");
    assertNotNull(apiKey, "OPENAI_API_KEY must be set");
    assertFalse(apiKey.trim().isEmpty(), "OPENAI_API_KEY must not be empty");

    System.out.println("Testing OpenAI connection with API key: " + apiKey.substring(0, 10) + "...");

    // When: Create a simple chat model and send a test message
    ChatLanguageModel chatModel =
        OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName("gpt-3.5-turbo") // Use cheaper model for connection test
            .temperature(0.7)
            .maxTokens(50)
            .build();

    // Then: Should get a response
    try {
      String response = chatModel.generate("Say 'Connection successful' if you can read this.");
      assertNotNull(response, "Should receive a response from OpenAI");
      assertFalse(response.trim().isEmpty(), "Response should not be empty");

      System.out.println("✓ OpenAI API connection successful!");
      System.out.println("✓ Response received: " + response);
    } catch (Exception e) {
      fail("Failed to connect to OpenAI API: " + e.getMessage() + "\n" +
          "Please verify your API key is valid and has sufficient credits.");
    }
  }

  @Test
  void testGPT4VisionModelAvailable() {
    // Given: API key from environment
    String apiKey = System.getenv("OPENAI_API_KEY");

    System.out.println("Testing GPT-4o (vision) model availability...");

    // When: Create GPT-4o model (supports vision)
    ChatLanguageModel chatModel =
        OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName("gpt-4o")  // Current vision-capable model
            .temperature(0.7)
            .maxTokens(50)
            .build();

    // Then: Should be able to use the model
    try {
      String response = chatModel.generate("Say 'GPT-4 Vision ready' if you can read this.");
      assertNotNull(response, "Should receive a response from GPT-4 Vision");
      assertFalse(response.trim().isEmpty(), "Response should not be empty");

      System.out.println("✓ GPT-4o (vision) model is available!");
      System.out.println("✓ Response: " + response);
    } catch (Exception e) {
      fail(
          "Failed to use GPT-4o model: "
              + e.getMessage()
              + "\n"
              + "Your API key may not have access to GPT-4o. "
              + "Check your OpenAI account tier and model access.");
    }
  }
}
