package com.redis.vl.extensions.cache;

import java.util.Map;

/** Represents a prompt-response pair for batch operations. */
public class PromptResponsePair {

  private final String prompt;
  private final String response;
  private final Map<String, Object> metadata;

  /**
   * Creates a new PromptResponsePair without metadata.
   *
   * @param prompt The prompt text
   * @param response The response text
   */
  public PromptResponsePair(String prompt, String response) {
    this(prompt, response, null);
  }

  /**
   * Creates a new PromptResponsePair with metadata.
   *
   * @param prompt The prompt text
   * @param response The response text
   * @param metadata Additional metadata
   */
  public PromptResponsePair(String prompt, String response, Map<String, Object> metadata) {
    this.prompt = prompt;
    this.response = response;
    this.metadata = metadata;
  }

  /**
   * Get the prompt.
   *
   * @return The prompt text
   */
  public String getPrompt() {
    return prompt;
  }

  /**
   * Get the response.
   *
   * @return The response text
   */
  public String getResponse() {
    return response;
  }

  /**
   * Get the metadata.
   *
   * @return The metadata map (may be null)
   */
  public Map<String, Object> getMetadata() {
    return metadata;
  }
}
