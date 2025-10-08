package com.redis.vl.extensions.messagehistory;

import static org.assertj.core.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
public class MessageHistoryDebugTest extends BaseIntegrationTest {

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
  public void debugSimpleStore() {
    chatHistory = new MessageHistory("debug_test", unifiedJedis);

    System.out.println("=== Adding first message ===");
    chatHistory.addMessage(Map.of("role", "user", "content", "first"));

    System.out.println("=== Getting recent (should be 1) ===");
    List<Map<String, Object>> result1 = chatHistory.getRecent(10, false, true, null);
    System.out.println("Result size: " + result1.size());
    for (Map<String, Object> msg : result1) {
      System.out.println("  Message: " + msg);
    }
    assertThat(result1).hasSize(1);

    System.out.println("\n=== Adding second message ===");
    chatHistory.addMessage(Map.of("role", "llm", "content", "second"));

    System.out.println("=== Getting recent (should be 2) ===");
    List<Map<String, Object>> result2 = chatHistory.getRecent(10, false, true, null);
    System.out.println("Result size: " + result2.size());
    for (Map<String, Object> msg : result2) {
      System.out.println("  Message: " + msg);
    }
    assertThat(result2).hasSize(2);

    System.out.println("\n=== Adding third message ===");
    chatHistory.addMessage(Map.of("role", "user", "content", "third"));

    System.out.println("=== Getting recent (should be 3) ===");
    List<Map<String, Object>> result3 = chatHistory.getRecent(10, false, true, null);
    System.out.println("Result size: " + result3.size());
    for (Map<String, Object> msg : result3) {
      System.out.println("  Message: " + msg);
    }
    assertThat(result3).hasSize(3);

    System.out.println("\n=== Getting recent with topK=2 ===");
    List<Map<String, Object>> result4 = chatHistory.getRecent(2, false, true, null);
    System.out.println("Result size: " + result4.size());
    for (Map<String, Object> msg : result4) {
      System.out.println("  Message: " + msg);
    }
    assertThat(result4).hasSize(2);
  }
}
