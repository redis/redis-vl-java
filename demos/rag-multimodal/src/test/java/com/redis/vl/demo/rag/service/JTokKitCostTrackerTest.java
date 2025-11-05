package com.redis.vl.demo.rag.service;

import static org.junit.jupiter.api.Assertions.*;

import com.redis.vl.demo.rag.model.LLMConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Test for JTokKitCostTracker - token counting and cost calculation. */
class JTokKitCostTrackerTest {

  private JTokKitCostTracker costTracker;

  @BeforeEach
  void setUp() {
    costTracker = new JTokKitCostTracker();
  }

  @Test
  void testCountTokensBasic() {
    // Given
    String text = "Hello, world!";

    // When
    int tokenCount = costTracker.countTokens(text);

    // Then
    assertTrue(tokenCount > 0);
    assertTrue(tokenCount < 10); // "Hello, world!" should be ~3-4 tokens
  }

  @Test
  void testCountTokensLongerText() {
    // Given
    String text =
        "Redis is an in-memory data structure store, used as a database, cache, and message broker.";

    // When
    int tokenCount = costTracker.countTokens(text);

    // Then
    assertTrue(tokenCount > 10);
    assertTrue(tokenCount < 30);
  }

  @Test
  void testCountTokensEmptyString() {
    // Given
    String text = "";

    // When
    int tokenCount = costTracker.countTokens(text);

    // Then
    assertEquals(0, tokenCount);
  }

  @Test
  void testCountTokensNull() {
    // When
    int tokenCount = costTracker.countTokens(null);

    // Then
    assertEquals(0, tokenCount);
  }

  @Test
  void testCalculateCostOpenAI() {
    // Given - GPT-4o costs approximately $2.50 per 1M input tokens
    LLMConfig.Provider provider = LLMConfig.Provider.OPENAI;
    String model = "gpt-4o";
    int tokens = 1000;

    // When
    double cost = costTracker.calculateCost(provider, model, tokens);

    // Then
    assertTrue(cost > 0);
    assertTrue(cost < 0.01); // 1000 tokens should be ~$0.0025
  }

  @Test
  void testCalculateCostAnthropic() {
    // Given - Claude 3.5 Sonnet costs approximately $3.00 per 1M input tokens
    LLMConfig.Provider provider = LLMConfig.Provider.ANTHROPIC;
    String model = "claude-3-5-sonnet-20241022";
    int tokens = 1000;

    // When
    double cost = costTracker.calculateCost(provider, model, tokens);

    // Then
    assertTrue(cost > 0);
    assertTrue(cost < 0.01);
  }

  @Test
  void testCalculateCostOllama() {
    // Given - Ollama is free
    LLMConfig.Provider provider = LLMConfig.Provider.OLLAMA;
    String model = "llama3.2-vision";
    int tokens = 1000;

    // When
    double cost = costTracker.calculateCost(provider, model, tokens);

    // Then
    assertEquals(0.0, cost);
  }

  @Test
  void testCalculateCostZeroTokens() {
    // Given
    LLMConfig.Provider provider = LLMConfig.Provider.OPENAI;
    String model = "gpt-4o";
    int tokens = 0;

    // When
    double cost = costTracker.calculateCost(provider, model, tokens);

    // Then
    assertEquals(0.0, cost);
  }

  @Test
  void testGetCostPer1KTokensOpenAI() {
    // Given
    LLMConfig.Provider provider = LLMConfig.Provider.OPENAI;
    String model = "gpt-4o";

    // When
    double costPer1K = costTracker.getCostPer1KTokens(provider, model);

    // Then
    assertTrue(costPer1K > 0);
    assertTrue(costPer1K < 0.01); // Should be around $0.0025 per 1K tokens
  }

  @Test
  void testGetCostPer1KTokensUnknownModel() {
    // Given - Unknown model should use default pricing
    LLMConfig.Provider provider = LLMConfig.Provider.OPENAI;
    String model = "unknown-model";

    // When
    double costPer1K = costTracker.getCostPer1KTokens(provider, model);

    // Then
    assertTrue(costPer1K > 0); // Should return default pricing
  }

  @Test
  void testCountTokensMultipleMessages() {
    // Given
    String message1 = "What is Redis?";
    String message2 = "Redis is an in-memory database.";
    String combined = message1 + " " + message2;

    // When
    int count1 = costTracker.countTokens(message1);
    int count2 = costTracker.countTokens(message2);
    int countCombined = costTracker.countTokens(combined);

    // Then
    assertTrue(count1 > 0);
    assertTrue(count2 > 0);
    // Combined count should be approximately equal (may differ by 1 due to spacing)
    assertTrue(Math.abs(countCombined - (count1 + count2)) <= 2);
  }

  @Test
  void testCalculateCostLargeTokenCount() {
    // Given - Test with 100K tokens
    LLMConfig.Provider provider = LLMConfig.Provider.OPENAI;
    String model = "gpt-4o";
    int tokens = 100_000;

    // When
    double cost = costTracker.calculateCost(provider, model, tokens);

    // Then
    assertTrue(cost > 0.1); // 100K tokens should cost more than $0.10
    assertTrue(cost < 1.0); // but less than $1.00
  }

  @Test
  void testCostCalculationPrecision() {
    // Given
    LLMConfig.Provider provider = LLMConfig.Provider.OPENAI;
    String model = "gpt-4o";

    // When - Calculate cost for different token counts
    double cost100 = costTracker.calculateCost(provider, model, 100);
    double cost200 = costTracker.calculateCost(provider, model, 200);

    // Then - Cost should scale linearly
    assertTrue(cost200 > cost100);
    assertTrue(Math.abs(cost200 - (2 * cost100)) < 0.0001); // Should be roughly double
  }
}
