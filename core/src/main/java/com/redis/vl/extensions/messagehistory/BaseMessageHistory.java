package com.redis.vl.extensions.messagehistory;

import static com.redis.vl.extensions.Constants.*;

import com.github.f4b6a3.ulid.UlidCreator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for message history implementations.
 *
 * <p>Matches the Python BaseMessageHistory from redisvl.extensions.message_history.base_history
 */
public abstract class BaseMessageHistory {

  /** Valid role values for message filtering. */
  private static final Set<String> VALID_ROLES = Set.of("system", "user", "llm", "tool");

  protected final String name;
  protected final String sessionTag;

  /**
   * Initialize message history.
   *
   * @param name The name of the message history index
   * @param sessionTag Tag to be added to entries to link to a specific conversation session.
   *     Defaults to instance ULID.
   */
  protected BaseMessageHistory(String name, String sessionTag) {
    this.name = name;
    this.sessionTag = (sessionTag != null) ? sessionTag : UlidCreator.getUlid().toString();
  }

  /** Clears the chat message history. */
  public abstract void clear();

  /** Clear all conversation history and remove any search indices. */
  public abstract void delete();

  /**
   * Remove a specific exchange from the conversation history.
   *
   * @param id The id of the entry to delete. If null then the last entry is deleted.
   */
  public abstract void drop(String id);

  /** Returns the full chat history. */
  public abstract List<Map<String, Object>> getMessages();

  /**
   * Retrieve the recent conversation history in sequential order.
   *
   * @param topK The number of previous messages to return. Default is 5.
   * @param asText Whether to return the conversation as a list of content strings, or list of
   *     message maps.
   * @param raw Whether to return the full Redis hash entry or just the role/content/tool_call_id.
   * @param sessionTag Tag of the entries linked to a specific conversation session. Defaults to
   *     instance ULID.
   * @param role Filter messages by role(s). Can be a single role string ("system", "user", "llm",
   *     "tool"), a List of role strings, or null for no filtering.
   * @return List of messages (either as text strings or maps depending on asText parameter)
   * @throws IllegalArgumentException if topK is not an integer greater than or equal to 0, or if
   *     role contains invalid values
   */
  public abstract <T> List<T> getRecent(
      int topK, boolean asText, boolean raw, String sessionTag, Object role);

  /**
   * Insert a prompt:response pair into the message history.
   *
   * @param prompt The user prompt to the LLM
   * @param response The corresponding LLM response
   * @param sessionTag The tag to mark the messages with. Defaults to instance session tag.
   */
  public abstract void store(String prompt, String response, String sessionTag);

  /**
   * Insert a list of prompts and responses into the message history.
   *
   * @param messages The list of user prompts and LLM responses
   * @param sessionTag The tag to mark the messages with. Defaults to instance session tag.
   */
  public abstract void addMessages(List<Map<String, String>> messages, String sessionTag);

  /**
   * Insert a single prompt or response into the message history.
   *
   * @param message The user prompt or LLM response
   * @param sessionTag The tag to mark the message with. Defaults to instance session tag.
   */
  public abstract void addMessage(Map<String, String> message, String sessionTag);

  /**
   * Formats messages from Redis into either text strings or structured maps.
   *
   * @param messages The messages from the message history index
   * @param asText Whether to return as text strings or maps
   * @return Formatted messages
   */
  @SuppressWarnings("unchecked")
  protected <T> List<T> formatContext(List<Map<String, Object>> messages, boolean asText) {
    List<T> context = new ArrayList<>();

    for (Map<String, Object> message : messages) {
      ChatMessage chatMessage = ChatMessage.fromDict(message);

      if (asText) {
        context.add((T) chatMessage.getContent());
      } else {
        Map<String, Object> chatMessageDict = new HashMap<>();
        chatMessageDict.put(ROLE_FIELD_NAME, chatMessage.getRole());
        chatMessageDict.put(CONTENT_FIELD_NAME, chatMessage.getContent());

        if (chatMessage.getToolCallId() != null) {
          chatMessageDict.put(TOOL_FIELD_NAME, chatMessage.getToolCallId());
        }

        context.add((T) chatMessageDict);
      }
    }

    return context;
  }

  /**
   * Validate and normalize role parameter for filtering messages.
   *
   * <p>Matches Python _validate_roles from base_history.py (lines 90-128)
   *
   * @param role A single role string, List of roles, or null
   * @return List of valid role strings if role is provided, null otherwise
   * @throws IllegalArgumentException if role contains invalid values or is the wrong type
   */
  @SuppressWarnings("unchecked")
  protected List<String> validateRoles(Object role) {
    if (role == null) {
      return null;
    }

    // Handle single role string
    if (role instanceof String) {
      String roleStr = (String) role;
      if (!VALID_ROLES.contains(roleStr)) {
        throw new IllegalArgumentException(
            String.format("Invalid role '%s'. Valid roles are: %s", roleStr, VALID_ROLES));
      }
      return List.of(roleStr);
    }

    // Handle list of roles
    if (role instanceof List) {
      List<?> roleList = (List<?>) role;

      if (roleList.isEmpty()) {
        throw new IllegalArgumentException("roles cannot be empty");
      }

      // Validate all roles in the list
      List<String> validatedRoles = new ArrayList<>();
      for (Object r : roleList) {
        if (!(r instanceof String)) {
          throw new IllegalArgumentException(
              "role list must contain only strings, found: " + r.getClass().getSimpleName());
        }
        String roleStr = (String) r;
        if (!VALID_ROLES.contains(roleStr)) {
          throw new IllegalArgumentException(
              String.format("Invalid role '%s'. Valid roles are: %s", roleStr, VALID_ROLES));
        }
        validatedRoles.add(roleStr);
      }

      return validatedRoles;
    }

    throw new IllegalArgumentException("role must be a String, List<String>, or null");
  }

  public String getName() {
    return name;
  }

  public String getSessionTag() {
    return sessionTag;
  }
}
