package com.redis.vl.extensions.messagehistory;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ChatRole enum.
 *
 * <p>Ported from Python: commit 23ecc77 - Add ChatRole enum for message history role validation
 *
 * <p>Python reference: tests/unit/test_message_history_schema.py
 */
@DisplayName("ChatRole Enum Tests")
class ChatRoleTest {

  @Test
  @DisplayName("Should have all required role values")
  void testAllRolesExist() {
    assertThat(ChatRole.values()).hasSize(4);
    assertThat(ChatRole.USER.getValue()).isEqualTo("user");
    assertThat(ChatRole.ASSISTANT.getValue()).isEqualTo("assistant");
    assertThat(ChatRole.SYSTEM.getValue()).isEqualTo("system");
    assertThat(ChatRole.TOOL.getValue()).isEqualTo("tool");
  }

  @Test
  @DisplayName("Should coerce valid string to ChatRole")
  void testCoerceValidString() {
    assertThat(ChatRole.fromString("user")).isEqualTo(ChatRole.USER);
    assertThat(ChatRole.fromString("assistant")).isEqualTo(ChatRole.ASSISTANT);
    assertThat(ChatRole.fromString("system")).isEqualTo(ChatRole.SYSTEM);
    assertThat(ChatRole.fromString("tool")).isEqualTo(ChatRole.TOOL);
  }

  @Test
  @DisplayName("Should accept deprecated 'llm' role with warning")
  void testDeprecatedLlmRole() {
    // In Python, 'llm' is accepted with a deprecation warning
    // In Java, we accept it and map it to the string value
    assertThat(ChatRole.fromString("llm")).isNull();
    assertThat(ChatRole.isDeprecatedRole("llm")).isTrue();
  }

  @Test
  @DisplayName("Should return null for unrecognized role")
  void testUnrecognizedRole() {
    assertThat(ChatRole.fromString("potato")).isNull();
    assertThat(ChatRole.fromString("admin")).isNull();
    assertThat(ChatRole.fromString("")).isNull();
  }

  @Test
  @DisplayName("Should be case-sensitive")
  void testCaseSensitive() {
    assertThat(ChatRole.fromString("User")).isNull();
    assertThat(ChatRole.fromString("SYSTEM")).isNull();
    assertThat(ChatRole.fromString("TOOL")).isNull();
  }

  @Test
  @DisplayName("Should check validity including deprecated roles")
  void testIsValidRole() {
    assertThat(ChatRole.isValidRole("user")).isTrue();
    assertThat(ChatRole.isValidRole("assistant")).isTrue();
    assertThat(ChatRole.isValidRole("system")).isTrue();
    assertThat(ChatRole.isValidRole("tool")).isTrue();
    assertThat(ChatRole.isValidRole("llm")).isTrue(); // deprecated but valid
    assertThat(ChatRole.isValidRole("potato")).isFalse();
    assertThat(ChatRole.isValidRole("User")).isFalse();
  }

  @Test
  @DisplayName("toString should return the string value")
  void testToString() {
    assertThat(ChatRole.USER.toString()).isEqualTo("user");
    assertThat(ChatRole.ASSISTANT.toString()).isEqualTo("assistant");
    assertThat(ChatRole.SYSTEM.toString()).isEqualTo("system");
    assertThat(ChatRole.TOOL.toString()).isEqualTo("tool");
  }
}
