package com.redis.vl.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenEscaperTest {

  private TokenEscaper escaper;

  @BeforeEach
  void setUp() {
    escaper = new TokenEscaper();
  }

  @Test
  void escapeReturnsUnchangedStringWithNoSpecialChars() {
    assertThat(escaper.escape("hello")).isEqualTo("hello");
  }

  @Test
  void escapesSpaces() {
    assertThat(escaper.escape("hello world")).isEqualTo("hello\\ world");
  }

  @Test
  void escapesComma() {
    assertThat(escaper.escape("a,b")).isEqualTo("a\\,b");
  }

  @Test
  void escapesDot() {
    assertThat(escaper.escape("file.txt")).isEqualTo("file\\.txt");
  }

  @Test
  void escapesHyphen() {
    assertThat(escaper.escape("well-known")).isEqualTo("well\\-known");
  }

  @Test
  void escapesAtSign() {
    assertThat(escaper.escape("user@example.com")).isEqualTo("user\\@example\\.com");
  }

  @Test
  void escapesMultipleSpecialChars() {
    assertThat(escaper.escape("(test)")).isEqualTo("\\(test\\)");
  }

  @Test
  void escapeEmptyStringReturnsEmpty() {
    assertThat(escaper.escape("")).isEqualTo("");
  }

  @Test
  void escapeNullThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> escaper.escape(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }
}
