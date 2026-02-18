package com.redis.vl.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared utility methods for full-text query processing.
 *
 * <p>Ported from Python: redisvl/utils/full_text_query_helper.py
 *
 * <p>Provides tokenization, escaping, and stopword loading used by both {@code HybridQuery} (native
 * FT.HYBRID) and {@code AggregateHybridQuery} (FT.AGGREGATE-based hybrid search).
 *
 * @since 0.2.0
 */
public final class FullTextQueryHelper {

  private FullTextQueryHelper() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Load default stopwords for a given language.
   *
   * <p>Python uses nltk, we use a simple file-based approach.
   *
   * @param language the language (e.g., "english", "german")
   * @return set of stopwords
   */
  public static Set<String> loadDefaultStopwords(String language) {
    if (language == null || language.isEmpty()) {
      return Set.of();
    }

    // Try to load stopwords from resources
    String resourcePath = "/stopwords/" + language + ".txt";
    java.io.InputStream inputStream = FullTextQueryHelper.class.getResourceAsStream(resourcePath);

    if (inputStream == null) {
      // Fallback: common English stopwords
      if ("english".equalsIgnoreCase(language)) {
        return Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is",
            "it", "no", "not", "of", "on", "or", "such", "that", "the", "their", "then", "there",
            "these", "they", "this", "to", "was", "will", "with");
      }
      return Set.of();
    }

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .collect(Collectors.toSet());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load stopwords for language: " + language, e);
    }
  }

  /**
   * Tokenize and escape a user query, removing stopwords.
   *
   * <p>Ported from Python: _tokenize_and_escape_query
   *
   * @param userQuery the user query to tokenize
   * @param stopwords the set of stopwords to filter out
   * @return tokenized and escaped query string joined by OR (pipe)
   */
  public static String tokenizeAndEscapeQuery(String userQuery, Set<String> stopwords) {
    TokenEscaper escaper = new TokenEscaper();

    // Tokenize: split on whitespace, clean up punctuation
    List<String> tokens =
        Arrays.stream(userQuery.split("\\s+"))
            .map(
                token ->
                    escaper.escape(
                        token
                            .strip()
                            .replaceAll("^,+|,+$", "")
                            .replace("\u201c", "")
                            .replace("\u201d", "")
                            .toLowerCase()))
            .filter(token -> !token.isEmpty() && !stopwords.contains(token))
            .collect(Collectors.toList());

    // Join with OR (pipe)
    return String.join(" | ", tokens);
  }
}
