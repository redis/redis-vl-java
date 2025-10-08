package com.redis.vl.notebooks;

import static org.assertj.core.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import com.redis.vl.extensions.messagehistory.MessageHistory;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration test reproducing the 07_message_history.ipynb notebook.
 *
 * <p>Ported from:
 * /Users/brian.sam-bodden/Code/redis/py/redis-vl-python/docs/user_guide/07_message_history.ipynb
 *
 * <p>Tests basic MessageHistory functionality: - Adding messages with roles (system, user, llm) -
 * Storing prompt/response pairs - Retrieving recent messages - Managing multiple sessions with
 * session tags - Dropping specific messages - Clearing history
 */
@Tag("integration")
public class MessageHistoryNotebookIntegrationTest extends BaseIntegrationTest {

  private MessageHistory chatHistory;

  @BeforeEach
  public void setup() {
    // Clean up any leftover data from previous test runs
    // Each test uses a unique index name, but we need to ensure clean state
  }

  @AfterEach
  public void cleanup() {
    if (chatHistory != null) {
      try {
        chatHistory.delete();
      } catch (Exception e) {
        // Ignore cleanup errors
      }
    }
  }

  @Test
  public void testBasicMessageHistory() {
    // Create message history (same as Python notebook)
    chatHistory = new MessageHistory("test_basic", unifiedJedis);

    // Add a system message
    chatHistory.addMessage(
        Map.of(
            "role",
            "system",
            "content",
            "You are a helpful geography tutor, giving simple and short answers to questions about European countries."));

    // Add multiple messages at once
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

    // Get recent messages (default top_k=5)
    List<Map<String, Object>> context = chatHistory.getRecent(5, false, false, null);

    System.out.println("Recent messages:");
    for (Map<String, Object> message : context) {
      System.out.println(message);
    }

    // Verify we got 5 messages (excluding the system message)
    assertThat(context).hasSize(5);
    assertThat(context.get(0))
        .containsEntry("role", "llm")
        .containsEntry("content", "The capital is Paris.");
    assertThat(context.get(4))
        .containsEntry("role", "llm")
        .containsEntry(
            "content",
            "As of 2023 the population of Great Britain is approximately 67 million people.");
  }

  @Test
  public void testStorePromptResponse() {
    chatHistory = new MessageHistory("test_store", unifiedJedis);

    // Add initial messages
    chatHistory.addMessages(
        List.of(
            Map.of("role", "user", "content", "And what is the capital of Spain?"),
            Map.of("role", "llm", "content", "The capital is Madrid."),
            Map.of("role", "user", "content", "What is the population of Great Britain?"),
            Map.of(
                "role",
                "llm",
                "content",
                "As of 2023 the population of Great Britain is approximately 67 million people.")));

    // Use store() convenience method for prompt/response pairs
    String prompt = "what is the size of England compared to Portugal?";
    String response = "England is larger in land area than Portal by about 15000 square miles.";
    chatHistory.store(prompt, response);

    // Get recent 6 messages
    List<Map<String, Object>> context = chatHistory.getRecent(6, false, false, null);

    System.out.println("After store():");
    for (Map<String, Object> message : context) {
      System.out.println(message);
    }

    assertThat(context).hasSize(6);
    assertThat(context.get(4)).containsEntry("role", "user").containsEntry("content", prompt);
    assertThat(context.get(5)).containsEntry("role", "llm").containsEntry("content", response);
  }

  @Test
  public void testMultipleSessionTags() {
    // Clean up any leftover index from previous runs
    try {
      MessageHistory temp = new MessageHistory("test_sessions", unifiedJedis);
      temp.delete();
    } catch (Exception e) {
      // Ignore if index doesn't exist
    }

    chatHistory = new MessageHistory("test_sessions", unifiedJedis);

    // Add geography messages to default session
    chatHistory.addMessages(
        List.of(
            Map.of("role", "user", "content", "What is the capital of France?"),
            Map.of("role", "llm", "content", "The capital is Paris.")));

    // Add math messages to a different session
    chatHistory.addMessage(
        Map.of(
            "role",
            "system",
            "content",
            "You are a helpful algebra tutor, giving simple answers to math problems."),
        "student two");

    chatHistory.addMessages(
        List.of(
            Map.of("role", "user", "content", "What is the value of x in the equation 2x + 3 = 7?"),
            Map.of("role", "llm", "content", "The value of x is 2."),
            Map.of("role", "user", "content", "What is the value of y in the equation 3y - 5 = 7?"),
            Map.of("role", "llm", "content", "The value of y is 4.")),
        "student two");

    // Retrieve messages for "student two" session
    List<Map<String, Object>> mathMessages = chatHistory.getRecent(10, false, false, "student two");

    System.out.println("Math messages for 'student two':");
    for (Map<String, Object> message : mathMessages) {
      System.out.println(message);
    }

    assertThat(mathMessages).hasSize(5);
    assertThat(mathMessages.get(0))
        .containsEntry("role", "system")
        .containsEntry(
            "content", "You are a helpful algebra tutor, giving simple answers to math problems.");
    assertThat(mathMessages.get(4))
        .containsEntry("role", "llm")
        .containsEntry("content", "The value of y is 4.");

    // Verify default session still has geography messages
    List<Map<String, Object>> geoMessages = chatHistory.getRecent(10, false, false, null);
    assertThat(geoMessages).hasSize(2);
    assertThat(geoMessages.get(0)).containsEntry("content", "What is the capital of France?");
  }

  @Test
  public void testDropMessage() {
    chatHistory = new MessageHistory("test_drop", unifiedJedis);

    // Add messages
    chatHistory.addMessages(
        List.of(
            Map.of("role", "user", "content", "What is the capital of France?"),
            Map.of("role", "llm", "content", "The capital is Paris."),
            Map.of("role", "user", "content", "What is the population of Great Britain?"),
            Map.of(
                "role",
                "llm",
                "content",
                "As of 2023 the population of Great Britain is approximately 67 million people.")));

    // Store an incorrect response
    chatHistory.store(
        "what is the smallest country in Europe?",
        "Monaco is the smallest country in Europe at 0.78 square miles.");

    // Get the key of the incorrect message
    List<Map<String, Object>> rawContext = chatHistory.getRecent(1, false, true, null);
    assertThat(rawContext).hasSize(1);
    String badKey = (String) rawContext.get(0).get("entry_id");

    // Drop the incorrect message
    chatHistory.drop(badKey);

    // Verify the incorrect response is gone but the question remains
    List<Map<String, Object>> correctedContext = chatHistory.getRecent(5, false, false, null);

    System.out.println("After dropping incorrect message:");
    for (Map<String, Object> message : correctedContext) {
      System.out.println(message);
    }

    // Should have 5 messages: 4 from initial + 1 question (response was dropped)
    assertThat(correctedContext).hasSize(5);
    assertThat(correctedContext.get(4))
        .containsEntry("role", "user")
        .containsEntry("content", "what is the smallest country in Europe?");
  }

  @Test
  public void testClear() {
    chatHistory = new MessageHistory("test_clear", unifiedJedis);

    // Add messages
    chatHistory.addMessages(
        List.of(
            Map.of("role", "user", "content", "What is the capital of France?"),
            Map.of("role", "llm", "content", "The capital is Paris.")));

    // Verify messages exist
    List<Map<String, Object>> beforeClear = chatHistory.getRecent(10, false, false, null);
    assertThat(beforeClear).hasSize(2);

    // Clear all messages
    chatHistory.clear();

    // Verify messages are gone
    List<Map<String, Object>> afterClear = chatHistory.getRecent(10, false, false, null);
    assertThat(afterClear).isEmpty();
  }

  @Test
  public void testMessagesProperty() {
    chatHistory = new MessageHistory("test_messages", unifiedJedis);

    // Add messages
    chatHistory.addMessages(
        List.of(
            Map.of("role", "user", "content", "What is the capital of France?"),
            Map.of("role", "llm", "content", "The capital is Paris."),
            Map.of("role", "user", "content", "And what is the capital of Spain?"),
            Map.of("role", "llm", "content", "The capital is Madrid.")));

    // Get all messages using the messages property
    List<Map<String, Object>> allMessages = chatHistory.getMessages();

    System.out.println("All messages:");
    for (Map<String, Object> message : allMessages) {
      System.out.println(message);
    }

    assertThat(allMessages).hasSize(4);
    assertThat(allMessages.get(0))
        .containsEntry("role", "user")
        .containsEntry("content", "What is the capital of France?");
    assertThat(allMessages.get(3))
        .containsEntry("role", "llm")
        .containsEntry("content", "The capital is Madrid.");
  }

  @Test
  public void testAsTextFormatting() {
    chatHistory = new MessageHistory("test_text", unifiedJedis);

    // Add messages
    chatHistory.addMessages(
        List.of(
            Map.of("role", "user", "content", "What is the capital of France?"),
            Map.of("role", "llm", "content", "The capital is Paris.")));

    // Get messages as text (just content strings)
    List<String> textMessages = chatHistory.getRecent(5, true, false, null);

    System.out.println("Messages as text:");
    for (String message : textMessages) {
      System.out.println(message);
    }

    assertThat(textMessages).hasSize(2);
    assertThat(textMessages.get(0)).isEqualTo("What is the capital of France?");
    assertThat(textMessages.get(1)).isEqualTo("The capital is Paris.");
  }
}
