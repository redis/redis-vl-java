package com.redis.vl.extensions.messagehistory;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for role filtering functionality (#349).
 *
 * <p>Ported from Python: tests/integration/test_role_filter_get_recent.py (TestRoleValidation)
 *
 * <p>Tests the validateRoles() method that validates role parameter for filtering messages.
 *
 * <p>Python reference: PR #387 - Role filtering for message history
 */
@DisplayName("Role Filtering Validation Tests")
class RoleFilteringTest {

  /**
   * Concrete implementation of BaseMessageHistory for testing validation logic. This allows us to
   * test the protected validateRoles method without requiring Redis.
   */
  private static class TestableMessageHistory extends BaseMessageHistory {
    public TestableMessageHistory() {
      super("test", null);
    }

    // Expose validateRoles for testing
    public List<String> testValidateRoles(Object role) {
      return validateRoles(role);
    }

    @Override
    public void clear() {}

    @Override
    public void delete() {}

    @Override
    public void drop(String id) {}

    @Override
    public List<java.util.Map<String, Object>> getMessages() {
      return null;
    }

    @Override
    public <T> List<T> getRecent(
        int topK, boolean asText, boolean raw, String sessionTag, Object role) {
      return null;
    }

    @Override
    public void store(String prompt, String response, String sessionTag) {}

    @Override
    public void addMessages(List<java.util.Map<String, String>> messages, String sessionTag) {}

    @Override
    public void addMessage(java.util.Map<String, String> message, String sessionTag) {}
  }

  @Test
  @DisplayName("Should accept null role (no filtering)")
  void testNullRoleReturnsNull() {
    TestableMessageHistory history = new TestableMessageHistory();

    List<String> result = history.testValidateRoles(null);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("Should accept valid single role: system")
  void testValidSingleRoleSystem() {
    TestableMessageHistory history = new TestableMessageHistory();

    List<String> result = history.testValidateRoles("system");

    assertThat(result).isNotNull().containsExactly("system");
  }

  @Test
  @DisplayName("Should accept valid single role: user")
  void testValidSingleRoleUser() {
    TestableMessageHistory history = new TestableMessageHistory();

    List<String> result = history.testValidateRoles("user");

    assertThat(result).isNotNull().containsExactly("user");
  }

  @Test
  @DisplayName("Should accept valid single role: llm")
  void testValidSingleRoleLlm() {
    TestableMessageHistory history = new TestableMessageHistory();

    List<String> result = history.testValidateRoles("llm");

    assertThat(result).isNotNull().containsExactly("llm");
  }

  @Test
  @DisplayName("Should accept valid single role: tool")
  void testValidSingleRoleTool() {
    TestableMessageHistory history = new TestableMessageHistory();

    List<String> result = history.testValidateRoles("tool");

    assertThat(result).isNotNull().containsExactly("tool");
  }

  @Test
  @DisplayName("Should reject invalid single role")
  void testInvalidSingleRole() {
    TestableMessageHistory history = new TestableMessageHistory();

    assertThatThrownBy(() -> history.testValidateRoles("invalid_role"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid role 'invalid_role'")
        .hasMessageContaining("system")
        .hasMessageContaining("user")
        .hasMessageContaining("llm")
        .hasMessageContaining("tool");
  }

  @Test
  @DisplayName("Should reject uppercase role (case-sensitive)")
  void testCaseSensitiveUppercase() {
    TestableMessageHistory history = new TestableMessageHistory();

    assertThatThrownBy(() -> history.testValidateRoles("SYSTEM"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid role 'SYSTEM'");
  }

  @Test
  @DisplayName("Should reject mixed-case role (case-sensitive)")
  void testCaseSensitiveMixedCase() {
    TestableMessageHistory history = new TestableMessageHistory();

    assertThatThrownBy(() -> history.testValidateRoles("User"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid role 'User'");
  }

  @Test
  @DisplayName("Should accept list of valid roles")
  void testValidListOfRoles() {
    TestableMessageHistory history = new TestableMessageHistory();

    List<String> input = Arrays.asList("system", "user");
    List<String> result = history.testValidateRoles(input);

    assertThat(result).isNotNull().containsExactly("system", "user");
  }

  @Test
  @DisplayName("Should accept list with all valid roles")
  void testValidListAllRoles() {
    TestableMessageHistory history = new TestableMessageHistory();

    List<String> input = Arrays.asList("system", "user", "llm", "tool");
    List<String> result = history.testValidateRoles(input);

    assertThat(result).isNotNull().containsExactly("system", "user", "llm", "tool");
  }

  @Test
  @DisplayName("Should reject empty role list")
  void testEmptyRoleList() {
    TestableMessageHistory history = new TestableMessageHistory();

    assertThatThrownBy(() -> history.testValidateRoles(Collections.emptyList()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roles cannot be empty");
  }

  @Test
  @DisplayName("Should reject list containing invalid role")
  void testListWithInvalidRole() {
    TestableMessageHistory history = new TestableMessageHistory();

    List<String> input = Arrays.asList("system", "invalid_role");

    assertThatThrownBy(() -> history.testValidateRoles(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid role 'invalid_role'");
  }

  @Test
  @DisplayName("Should reject list containing uppercase role")
  void testListWithUppercaseRole() {
    TestableMessageHistory history = new TestableMessageHistory();

    List<String> input = Arrays.asList("system", "USER");

    assertThatThrownBy(() -> history.testValidateRoles(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid role 'USER'");
  }

  @Test
  @DisplayName("Should reject non-string, non-list input")
  void testInvalidInputType() {
    TestableMessageHistory history = new TestableMessageHistory();

    assertThatThrownBy(() -> history.testValidateRoles(Integer.valueOf(42)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("role must be a String, List<String>, or null");
  }

  @Test
  @DisplayName("Should handle list with single role")
  void testListWithSingleRole() {
    TestableMessageHistory history = new TestableMessageHistory();

    List<String> input = Arrays.asList("system");
    List<String> result = history.testValidateRoles(input);

    assertThat(result).isNotNull().containsExactly("system");
  }
}
