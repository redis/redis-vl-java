package com.redis.vl.extensions.messagehistory;

import static org.assertj.core.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
public class MessageHistoryQueryDebugTest extends BaseIntegrationTest {

  private MessageHistory chatHistory;

  @AfterEach
  public void cleanup() {
    if (chatHistory != null) {
      try {
        chatHistory.delete();
      } catch (Exception e) {
        // Ignore
      }
    }
  }

  @Test
  public void debugAddMessages() {
    chatHistory = new MessageHistory("query_debug", unifiedJedis);

    // Add messages like the failing test
    System.out.println("=== Adding 6 messages ===");
    chatHistory.addMessages(
        List.of(
            Map.of("role", "user", "content", "What is the capital of France?"),
            Map.of("role", "llm", "content", "The capital is Paris."),
            Map.of("role", "user", "content", "And what is the capital of Spain?"),
            Map.of("role", "llm", "content", "The capital is Madrid."),
            Map.of("role", "user", "content", "What is the population of Great Britain?"),
            Map.of(
                "role",
                "llm",
                "content",
                "As of 2023 the population of Great Britain is approximately 67 million people.")));

    System.out.println("\n=== Getting raw data (all fields) ===");
    List<Map<String, Object>> rawData = chatHistory.getRecent(10, false, true, null);
    System.out.println("Raw size: " + rawData.size());
    for (int i = 0; i < rawData.size(); i++) {
      System.out.println("  [" + i + "] " + rawData.get(i));
    }

    System.out.println("\n=== Getting formatted data (role/content only) ===");
    List<Map<String, Object>> formatted = chatHistory.getRecent(10, false, false, null);
    System.out.println("Formatted size: " + formatted.size());
    for (int i = 0; i < formatted.size(); i++) {
      System.out.println("  [" + i + "] " + formatted.get(i));
    }

    System.out.println("\n=== Getting as text ===");
    List<String> text = chatHistory.getRecent(10, true, false, null);
    System.out.println("Text size: " + text.size());
    for (int i = 0; i < text.size(); i++) {
      System.out.println("  [" + i + "] " + text.get(i));
    }

    assertThat(rawData).hasSize(6);
    assertThat(formatted).hasSize(6);
    assertThat(text).hasSize(6);
  }
}
