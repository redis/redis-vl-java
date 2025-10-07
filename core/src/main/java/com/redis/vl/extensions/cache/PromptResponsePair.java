package com.redis.vl.extensions.cache;

import java.util.Collections;
import java.util.HashMap;
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
    // Defensive copy to prevent external modification
    this.metadata = metadata != null ? new HashMap<>(metadata) : null;
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
   * @return An unmodifiable view of the metadata map, or null if no metadata
   */
  public Map<String, Object> getMetadata() {
    return metadata != null ? Collections.unmodifiableMap(metadata) : null;
  }
}
