package com.redis.vl.extensions.messagehistory;

import static com.redis.vl.extensions.Constants.*;
import static org.assertj.core.api.Assertions.*;

import com.redis.vl.BaseIntegrationTest;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Integration tests for role filtering in getRecent() method (#349).
 *
 * <p>Ported from Python: tests/integration/test_role_filter_get_recent.py
 *
 * <p>Tests role filtering functionality with real Redis operations.
 *
 * <p>Python reference: PR #387 - Role filtering for message history
 */
@Tag("integration")
@DisplayName("Role Filtering Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RoleFilteringIntegrationTest extends BaseIntegrationTest {

  private MessageHistory history;

  @BeforeEach
  void setUp() {
    // Each test gets a unique MessageHistory instance
  }

  @AfterEach
  void tearDown() {
    if (history != null) {
      try {
        history.delete();
      } catch (Exception e) {
        // Ignore cleanup errors
      }
    }
  }

  @Test
  @Order(1)
  @DisplayName("Should filter by single role: system")
  void testGetRecentSingleRoleSystem() {
    history = new MessageHistory("test_role_system", unifiedJedis);
    history.clear();

    // Add various messages with different roles
    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "System initialization"));
    messages.add(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "Hello"));
    messages.add(Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "Hi there"));
    messages.add(
        Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "System configuration updated"));
    messages.add(
        Map.of(
            ROLE_FIELD_NAME,
            "tool",
            CONTENT_FIELD_NAME,
            "Function executed",
            TOOL_FIELD_NAME,
            "call1"));

    history.addMessages(messages);

    // Get only system messages
    List<Map<String, Object>> result = history.getRecent(10, false, false, null, "system");

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(msg -> "system".equals(msg.get(ROLE_FIELD_NAME)));
    assertThat(result.get(0).get(CONTENT_FIELD_NAME)).isEqualTo("System initialization");
    assertThat(result.get(1).get(CONTENT_FIELD_NAME)).isEqualTo("System configuration updated");
  }

  @Test
  @Order(2)
  @DisplayName("Should filter by single role: user")
  void testGetRecentSingleRoleUser() {
    history = new MessageHistory("test_role_user", unifiedJedis);
    history.clear();

    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "Welcome"));
    messages.add(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "First question"));
    messages.add(Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "First answer"));
    messages.add(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "Second question"));
    messages.add(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "Third question"));

    history.addMessages(messages);

    List<Map<String, Object>> result = history.getRecent(10, false, false, null, "user");

    assertThat(result).hasSize(3);
    assertThat(result).allMatch(msg -> "user".equals(msg.get(ROLE_FIELD_NAME)));
    assertThat(result.get(0).get(CONTENT_FIELD_NAME)).isEqualTo("First question");
    assertThat(result.get(2).get(CONTENT_FIELD_NAME)).isEqualTo("Third question");
  }

  @Test
  @Order(3)
  @DisplayName("Should filter by single role: llm")
  void testGetRecentSingleRoleLlm() {
    history = new MessageHistory("test_role_llm", unifiedJedis);
    history.clear();

    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "Question 1"));
    messages.add(Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "Answer 1"));
    messages.add(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "Question 2"));
    messages.add(Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "Answer 2"));
    messages.add(Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "System note"));

    history.addMessages(messages);

    List<Map<String, Object>> result = history.getRecent(10, false, false, null, "llm");

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(msg -> "llm".equals(msg.get(ROLE_FIELD_NAME)));
    assertThat(result.get(0).get(CONTENT_FIELD_NAME)).isEqualTo("Answer 1");
    assertThat(result.get(1).get(CONTENT_FIELD_NAME)).isEqualTo("Answer 2");
  }

  @Test
  @Order(4)
  @DisplayName("Should filter by single role: tool")
  void testGetRecentSingleRoleTool() {
    history = new MessageHistory("test_role_tool", unifiedJedis);
    history.clear();

    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "Run function"));
    messages.add(
        Map.of(
            ROLE_FIELD_NAME,
            "tool",
            CONTENT_FIELD_NAME,
            "Function result 1",
            TOOL_FIELD_NAME,
            "call1"));
    messages.add(Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "Processing"));
    messages.add(
        Map.of(
            ROLE_FIELD_NAME,
            "tool",
            CONTENT_FIELD_NAME,
            "Function result 2",
            TOOL_FIELD_NAME,
            "call2"));

    history.addMessages(messages);

    List<Map<String, Object>> result = history.getRecent(10, false, false, null, "tool");

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(msg -> "tool".equals(msg.get(ROLE_FIELD_NAME)));
    assertThat(result).allMatch(msg -> msg.containsKey(TOOL_FIELD_NAME));
  }

  @Test
  @Order(5)
  @DisplayName("Should filter by multiple roles")
  void testGetRecentMultipleRoles() {
    history = new MessageHistory("test_multi_roles", unifiedJedis);
    history.clear();

    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "System message"));
    messages.add(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "User message"));
    messages.add(Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "LLM message"));
    messages.add(
        Map.of(
            ROLE_FIELD_NAME, "tool", CONTENT_FIELD_NAME, "Tool message", TOOL_FIELD_NAME, "call1"));

    history.addMessages(messages);

    // Get system and user messages only
    List<Map<String, Object>> result =
        history.getRecent(10, false, false, null, Arrays.asList("system", "user"));

    assertThat(result).hasSize(2);
    assertThat(result)
        .allMatch(msg -> List.of("system", "user").contains(msg.get(ROLE_FIELD_NAME)));
    assertThat(result.get(0).get(CONTENT_FIELD_NAME)).isEqualTo("System message");
    assertThat(result.get(1).get(CONTENT_FIELD_NAME)).isEqualTo("User message");
  }

  @Test
  @Order(6)
  @DisplayName("Should return all messages when role=null (backward compatibility)")
  void testGetRecentNoRoleFilterBackwardCompatibility() {
    history = new MessageHistory("test_no_filter", unifiedJedis);
    history.clear();

    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "System"));
    messages.add(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "User"));
    messages.add(Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "LLM"));
    messages.add(
        Map.of(ROLE_FIELD_NAME, "tool", CONTENT_FIELD_NAME, "Tool", TOOL_FIELD_NAME, "call1"));

    history.addMessages(messages);

    // No role filter - should return all messages
    List<Map<String, Object>> result = history.getRecent(10, false, false, null, null);

    assertThat(result).hasSize(4);
    Set<String> roles =
        result.stream()
            .map(msg -> (String) msg.get(ROLE_FIELD_NAME))
            .collect(java.util.stream.Collectors.toSet());
    assertThat(roles).containsExactlyInAnyOrder("system", "user", "llm", "tool");
  }

  @Test
  @Order(7)
  @DisplayName("Should throw exception for invalid role")
  void testGetRecentInvalidRoleRaisesError() {
    history = new MessageHistory("test_invalid", unifiedJedis);

    assertThatThrownBy(() -> history.getRecent(10, false, false, null, "invalid_role"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid role");
  }

  @Test
  @Order(8)
  @DisplayName("Should throw exception for invalid role in list")
  void testGetRecentInvalidRoleInListRaisesError() {
    history = new MessageHistory("test_invalid_list", unifiedJedis);

    assertThatThrownBy(
            () ->
                history.getRecent(10, false, false, null, Arrays.asList("system", "invalid_role")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid role");
  }

  @Test
  @Order(9)
  @DisplayName("Should throw exception for empty role list")
  void testGetRecentEmptyRoleListRaisesError() {
    history = new MessageHistory("test_empty_list", unifiedJedis);

    assertThatThrownBy(() -> history.getRecent(10, false, false, null, Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roles cannot be empty");
  }

  @Test
  @Order(10)
  @DisplayName("Should work with top_k parameter")
  void testGetRecentRoleWithTopKParameter() {
    history = new MessageHistory("test_with_params", unifiedJedis);
    history.clear();

    // Add many system messages
    for (int i = 0; i < 5; i++) {
      history.addMessage(
          Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "System message " + i));
    }

    // Add other messages
    history.addMessage(Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "User message"));
    history.addMessage(Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "LLM message"));

    // Get only 2 most recent system messages (should get last 2: index 3 and 4)
    List<Map<String, Object>> result = history.getRecent(2, false, false, null, "system");

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(msg -> "system".equals(msg.get(ROLE_FIELD_NAME)));
    // Should get 2 most recent ones in chronological order
    // Since we added 0,1,2,3,4 and query gets the last 2, we expect 3 and 4
    String first = (String) result.get(0).get(CONTENT_FIELD_NAME);
    String second = (String) result.get(1).get(CONTENT_FIELD_NAME);

    // Verify we got 2 of the system messages
    assertThat(first).startsWith("System message");
    assertThat(second).startsWith("System message");

    // The second should be later than the first (chronological order after reversal)
    int firstNum = Integer.parseInt(first.replace("System message ", ""));
    int secondNum = Integer.parseInt(second.replace("System message ", ""));
    assertThat(secondNum).isGreaterThan(firstNum);
  }

  @Test
  @Order(11)
  @DisplayName("Should work with session_tag parameter")
  void testGetRecentRoleWithSessionTag() {
    history = new MessageHistory("test_session", unifiedJedis);
    history.clear();

    // Add messages with different session tags
    history.addMessages(
        Arrays.asList(
            Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "System for session1"),
            Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "User for session1")),
        "session1");

    history.addMessages(
        Arrays.asList(
            Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "System for session2"),
            Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "LLM for session2")),
        "session2");

    // Get system messages from session2 only
    List<Map<String, Object>> result = history.getRecent(10, false, false, "session2", "system");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).get(ROLE_FIELD_NAME)).isEqualTo("system");
    assertThat(result.get(0).get(CONTENT_FIELD_NAME)).isEqualTo("System for session2");
  }

  @Test
  @Order(12)
  @DisplayName("Should work with raw=true parameter")
  void testGetRecentRoleWithRawOutput() {
    history = new MessageHistory("test_raw", unifiedJedis);
    history.clear();

    history.addMessage(Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "System message"));

    List<Map<String, Object>> result = history.getRecent(10, false, true, null, "system");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).get(ROLE_FIELD_NAME)).isEqualTo("system");
    // Raw should include additional metadata
    assertThat(result.get(0)).containsKeys(ID_FIELD_NAME, TIMESTAMP_FIELD_NAME, SESSION_FIELD_NAME);
  }

  @Test
  @Order(13)
  @DisplayName("Should accept all valid roles")
  void testValidRolesAccepted() {
    String[] validRoles = {"system", "user", "llm", "tool"};
    history = new MessageHistory("test_valid_roles", unifiedJedis);
    history.clear();

    // Add messages with all valid roles
    for (String role : validRoles) {
      if ("tool".equals(role)) {
        history.addMessage(
            Map.of(
                ROLE_FIELD_NAME,
                role,
                CONTENT_FIELD_NAME,
                role + " message",
                TOOL_FIELD_NAME,
                "call1"));
      } else {
        history.addMessage(Map.of(ROLE_FIELD_NAME, role, CONTENT_FIELD_NAME, role + " message"));
      }
    }

    // Test each valid role works
    for (String role : validRoles) {
      List<Map<String, Object>> result = history.getRecent(10, false, false, null, role);
      assertThat(result).hasSizeGreaterThanOrEqualTo(1);
      assertThat(result).allMatch(msg -> role.equals(msg.get(ROLE_FIELD_NAME)));
    }
  }

  @Test
  @Order(14)
  @DisplayName("Should be case-sensitive for role validation")
  void testCaseSensitiveRoles() {
    history = new MessageHistory("test_case", unifiedJedis);

    // Uppercase should fail
    assertThatThrownBy(() -> history.getRecent(10, false, false, null, "SYSTEM"))
        .isInstanceOf(IllegalArgumentException.class);

    // Mixed case should fail
    assertThatThrownBy(() -> history.getRecent(10, false, false, null, "User"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Order(15)
  @DisplayName("Should work with asText=true parameter")
  void testGetRecentRoleWithAsText() {
    history = new MessageHistory("test_as_text", unifiedJedis);
    history.clear();

    history.addMessages(
        Arrays.asList(
            Map.of(ROLE_FIELD_NAME, "system", CONTENT_FIELD_NAME, "System message"),
            Map.of(ROLE_FIELD_NAME, "user", CONTENT_FIELD_NAME, "User message"),
            Map.of(ROLE_FIELD_NAME, "llm", CONTENT_FIELD_NAME, "LLM message")));

    // Get only user messages as text
    List<String> result = history.getRecent(10, true, false, null, "user");

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo("User message");
  }
}
