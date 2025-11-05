package com.redis.vl.demo.rag.model;

import java.time.Instant;

/**
 * Represents a chat message with cost tracking.
 *
 * @param id Unique message identifier
 * @param role Message role (USER or ASSISTANT)
 * @param content Message text content
 * @param timestamp When the message was created
 * @param tokenCount Number of tokens in the message
 * @param costUsd Cost in USD for this message (for AI responses)
 * @param model LLM model used (for AI responses)
 * @param fromCache Whether the response came from cache
 */
public record ChatMessage(
    String id,
    Role role,
    String content,
    Instant timestamp,
    int tokenCount,
    double costUsd,
    String model,
    boolean fromCache) {

  public enum Role {
    USER,
    ASSISTANT
  }

  /**
   * Creates a user message.
   *
   * @param content Message content
   * @return User chat message
   */
  public static ChatMessage user(String content) {
    return new ChatMessage(
        generateId(), Role.USER, content, Instant.now(), 0, 0.0, null, false);
  }

  /**
   * Creates an assistant message.
   *
   * @param content Message content
   * @param tokenCount Token count
   * @param costUsd Cost in USD
   * @param model Model name
   * @param fromCache Whether from cache
   * @return Assistant chat message
   */
  public static ChatMessage assistant(
      String content, int tokenCount, double costUsd, String model, boolean fromCache) {
    return new ChatMessage(
        generateId(), Role.ASSISTANT, content, Instant.now(), tokenCount, costUsd, model, fromCache);
  }

  private static String generateId() {
    return java.util.UUID.randomUUID().toString();
  }
}
