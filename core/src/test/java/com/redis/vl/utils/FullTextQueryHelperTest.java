package com.redis.vl.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class FullTextQueryHelperTest {

  @Test
  void loadDefaultStopwordsReturnsNonEmptySetForEnglish() {
    Set<String> stopwords = FullTextQueryHelper.loadDefaultStopwords("english");
    assertThat(stopwords).isNotEmpty().contains("the", "and", "is");
  }

  @Test
  void loadDefaultStopwordsReturnsEmptySetForUnknownLanguage() {
    Set<String> stopwords = FullTextQueryHelper.loadDefaultStopwords("klingon");
    assertThat(stopwords).isEmpty();
  }

  @Test
  void loadDefaultStopwordsReturnsEmptySetForNullLanguage() {
    assertThat(FullTextQueryHelper.loadDefaultStopwords(null)).isEmpty();
  }

  @Test
  void loadDefaultStopwordsReturnsEmptySetForEmptyLanguage() {
    assertThat(FullTextQueryHelper.loadDefaultStopwords("")).isEmpty();
  }

  @Test
  void tokenizeAndEscapeQueryFiltersStopwords() {
    Set<String> stopwords = Set.of("the", "is", "a");
    String result = FullTextQueryHelper.tokenizeAndEscapeQuery("the cat is a dog", stopwords);
    assertThat(result).doesNotContain("the").doesNotContain("is").doesNotContain(" a ");
    assertThat(result).contains("cat").contains("dog");
  }

  @Test
  void tokenizeAndEscapeQueryJoinsWithPipe() {
    Set<String> stopwords = Set.of();
    String result = FullTextQueryHelper.tokenizeAndEscapeQuery("foo bar", stopwords);
    assertThat(result).isEqualTo("foo | bar");
  }

  @Test
  void tokenizeAndEscapeQueryEscapesSpecialChars() {
    Set<String> stopwords = Set.of();
    String result = FullTextQueryHelper.tokenizeAndEscapeQuery("hello-world", stopwords);
    assertThat(result).contains("\\-");
  }

  @Test
  void tokenizeAndEscapeQueryLowercasesTokens() {
    Set<String> stopwords = Set.of();
    String result = FullTextQueryHelper.tokenizeAndEscapeQuery("Hello World", stopwords);
    assertThat(result).isEqualTo("hello | world");
  }

  @Test
  void tokenizeAndEscapeQueryHandlesExtraWhitespace() {
    Set<String> stopwords = Set.of();
    String result = FullTextQueryHelper.tokenizeAndEscapeQuery("foo  bar", stopwords);
    assertThat(result).isEqualTo("foo | bar");
  }
}
