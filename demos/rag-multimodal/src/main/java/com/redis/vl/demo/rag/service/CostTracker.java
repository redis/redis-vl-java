package com.redis.vl.demo.rag.service;

import com.redis.vl.demo.rag.model.LLMConfig;

/** Interface for tracking token counts and costs. */
public interface CostTracker {

  /**
   * Counts tokens in text.
   *
   * @param text Text to count tokens
   * @return Token count
   */
  int countTokens(String text);

  /**
   * Calculates cost for a given number of tokens.
   *
   * @param provider LLM provider
   * @param model Model name
   * @param tokens Number of tokens
   * @return Cost in USD
   */
  double calculateCost(LLMConfig.Provider provider, String model, int tokens);

  /**
   * Gets the cost per 1K tokens for a model.
   *
   * @param provider LLM provider
   * @param model Model name
   * @return Cost per 1K tokens in USD
   */
  double getCostPer1KTokens(LLMConfig.Provider provider, String model);
}
