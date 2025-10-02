package com.redis.vl.utils;

import java.util.regex.Pattern;

/**
 * Escape punctuation within an input string.
 *
 * <p>Ported from Python: redisvl/utils/token_escaper.py
 *
 * <p>Adapted from RedisOM Python.
 *
 * <p>Characters that RediSearch requires us to escape during queries.
 *
 * <p>Source: <a
 * href="https://redis.io/docs/stack/search/reference/escaping/#the-rules-of-text-field-tokenization">Redis
 * Search Escaping</a>
 *
 * @since 0.1.0
 */
public class TokenEscaper {

  /**
   * Characters that RediSearch requires us to escape during queries.
   *
   * <p>Python: DEFAULT_ESCAPED_CHARS = r"[,.<>{}\[\]\\\"\':;!@#$%^&*()\-+=~\/ ]"
   */
  private static final String DEFAULT_ESCAPED_CHARS = "[,.<>{}\\[\\]\\\\\"':;!@#$%^&*()\\-+=~/ ]";

  private final Pattern escapedCharsPattern;

  /** Default constructor using default escaped characters. */
  public TokenEscaper() {
    this.escapedCharsPattern = Pattern.compile(DEFAULT_ESCAPED_CHARS);
  }

  /**
   * Constructor with custom escape pattern.
   *
   * @param escapeCharsPattern custom pattern for characters to escape
   */
  public TokenEscaper(Pattern escapeCharsPattern) {
    this.escapedCharsPattern = escapeCharsPattern;
  }

  /**
   * Escape special characters in the input string.
   *
   * <p>Python equivalent:
   *
   * <pre>
   * def escape(self, value: str) -> str:
   *     def escape_symbol(match):
   *         value = match.group(0)
   *         return f"\\{value}"
   *     return self.escaped_chars_re.sub(escape_symbol, value)
   * </pre>
   *
   * @param value the string to escape
   * @return escaped string
   * @throws IllegalArgumentException if value is not a string
   */
  public String escape(String value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "Value must be a string object for token escaping, got null");
    }

    // Replace each matched character with escaped version
    return escapedCharsPattern.matcher(value).replaceAll("\\\\$0");
  }
}
