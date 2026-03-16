package com.redis.vl.extensions.messagehistory;

import java.util.Set;

/**
 * Enumeration of valid chat message roles.
 *
 * <p>Ported from Python: redisvl/extensions/message_history/schema.py (commit 23ecc77)
 *
 * <p>This enum serves as the single source of truth for valid roles in chat message history. The
 * deprecated "llm" role is accepted for backward compatibility but is not a member of this enum.
 */
public enum ChatRole {
  USER("user"),
  ASSISTANT("assistant"),
  SYSTEM("system"),
  TOOL("tool");

  /** Deprecated role values that are accepted for backward compatibility. */
  private static final Set<String> DEPRECATED_ROLES = Set.of("llm");

  private final String value;

  ChatRole(String value) {
    this.value = value;
  }

  /**
   * Get the string value of this role.
   *
   * @return The role string value
   */
  public String getValue() {
    return value;
  }

  /**
   * Coerce a string to a ChatRole enum value.
   *
   * @param role The role string
   * @return The matching ChatRole, or null if not a standard role
   */
  public static ChatRole fromString(String role) {
    if (role == null || role.isEmpty()) {
      return null;
    }
    for (ChatRole chatRole : values()) {
      if (chatRole.value.equals(role)) {
        return chatRole;
      }
    }
    return null;
  }

  /**
   * Check if a string is a valid role (including deprecated roles).
   *
   * @param role The role string to check
   * @return true if the role is valid (either standard or deprecated)
   */
  public static boolean isValidRole(String role) {
    return fromString(role) != null || isDeprecatedRole(role);
  }

  /**
   * Check if a string is a deprecated role value.
   *
   * @param role The role string to check
   * @return true if the role is deprecated
   */
  public static boolean isDeprecatedRole(String role) {
    return DEPRECATED_ROLES.contains(role);
  }

  @Override
  public String toString() {
    return value;
  }
}
